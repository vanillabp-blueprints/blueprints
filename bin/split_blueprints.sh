#!/usr/bin/env bash
#
# Push every blueprint of this monorepo into a repository of its own.
#
# The job is DIRECTORY DRIVEN and IDEMPOTENT: it iterates over the directories
# '<group>/<blueprint-id>/<platform>/' which exist and splits what it finds. A blueprint whose
# platform directory does not exist yet is not mentioned anywhere - no repository is
# created on stock, and the index keeps saying 'planned' until the directory arrives. A
# later run picks it up without anybody having to remember it.
#
# For each directory:
#   1. the target repository vanillabp-blueprints/<id>-<platform> is created if missing,
#      and its description and homepage are taken from blueprints.yaml,
#   2. 'git subtree split' produces the blueprint's history without the rest of the
#      monorepo, and that is force-pushed onto the mirror's main branch,
#   3. the index entry is set to 'available' - so the catalogue can only advertise a
#      repository which exists.
#
# The mirrors are read-only. Issues and pull requests belong into this monorepo, which is
# what every blueprint's README says.
#
# Environment:
#   GH_TOKEN   a token allowed to create repositories in the organisation and push into
#              them (the repository secret BLUEPRINTS_SPLIT_TOKEN)
#   DRY_RUN=1  do everything locally, push nothing, create nothing
#   INDEX_BRANCH  branch of the '.github' repository the index is read from (default main)
#
set -euo pipefail

ORG="vanillabp-blueprints"
INDEX_REPO="${ORG}/.github"
INDEX_BRANCH="${INDEX_BRANCH:-main}"
HOMEPAGE="https://github.com/${ORG}"
PLATFORMS=(springboot quarkus)
DRY_RUN="${DRY_RUN:-0}"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
cd "${root}"

if git subtree -h 2>&1 | grep -q "not a git command"; then
  echo "git subtree is not available - it is required to split the blueprints." >&2
  exit 1
fi
if [ "${DRY_RUN}" != "1" ] && [ -z "${GH_TOKEN:-}" ]; then
  echo "GH_TOKEN is empty. The split needs the BLUEPRINTS_SPLIT_TOKEN secret:" >&2
  echo "  a fine-grained token for the organisation ${ORG} with" >&2
  echo "  'Administration: read and write' and 'Contents: read and write'." >&2
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

index="${work}/index"
if [ "${DRY_RUN}" = "1" ]; then
  # Without a token, read the published index; the local one may be ahead of it.
  mkdir -p "${index}"
  if ! curl -fsSL -o "${index}/blueprints.yaml" \
    "https://raw.githubusercontent.com/${ORG}/.github/${INDEX_BRANCH}/blueprints.yaml"; then
    echo "blueprints.yaml not found on branch '${INDEX_BRANCH}' of ${INDEX_REPO}." >&2
    echo "Set INDEX_BRANCH to the branch carrying it." >&2
    exit 1
  fi
else
  # Full history on purpose: the index is pushed at the end of this script and rebased
  # onto whatever arrived meanwhile, which needs the commit both sides branched from.
  git clone --quiet --branch "${INDEX_BRANCH}" \
    "https://x-access-token:${GH_TOKEN}@github.com/${INDEX_REPO}.git" "${index}"
fi

# Reads one value of a blueprint out of the index.
blueprint_value() {
  local id="$1" key="$2"
  python3 - "${index}/blueprints.yaml" "${id}" "${key}" <<'PYTHON'
import sys, yaml
index_file, blueprint_id, key = sys.argv[1:4]
with open(index_file, encoding="utf-8") as file:
    index = yaml.safe_load(file)
for blueprint in index["blueprints"]:
    if blueprint["id"] == blueprint_id:
        print(str(blueprint.get(key, "")).strip())
        break
else:
    sys.exit(f"'{blueprint_id}' has no entry in blueprints.yaml")
PYTHON
}

split_count=0
splitted=()

for platform in "${PLATFORMS[@]}"; do
  for pom in */*/"${platform}"/pom.xml; do
    [ -f "${pom}" ] || continue
    prefix="$(dirname "${pom}")"
    id="$(basename "$(dirname "${prefix}")")"
    repo="${id}-${platform}"
    branch="split-${id}-${platform}"

    title="$(blueprint_value "${id}" title)"
    description="${title}. A VanillaBP blueprint, read-only mirror of the monorepo 'blueprints'."

    echo "=== ${prefix} -> ${ORG}/${repo}"

    if [ "${DRY_RUN}" = "1" ]; then
      echo "    would ensure repository, description: ${description}"
    elif gh repo view "${ORG}/${repo}" >/dev/null 2>&1; then
      gh repo edit "${ORG}/${repo}" \
        --description "${description}" \
        --homepage "${HOMEPAGE}" >/dev/null
      echo "    repository exists, description and homepage updated"
    else
      gh repo create "${ORG}/${repo}" \
        --public \
        --description "${description}" \
        --homepage "${HOMEPAGE}" >/dev/null
      echo "    repository created"
    fi

    git branch -D "${branch}" >/dev/null 2>&1 || true
    git subtree split -q --prefix="${prefix}" -b "${branch}" >/dev/null
    echo "    split $(git rev-list --count "${branch}") commits"

    if [ "${DRY_RUN}" = "1" ]; then
      echo "    would push ${branch} to ${ORG}/${repo} main"
    else
      # Force, because a split is derived from this repository's history: the mirror never
      # carries commits of its own, so there is nothing that could be lost.
      git push --quiet --force \
        "https://x-access-token:${GH_TOKEN}@github.com/${ORG}/${repo}.git" \
        "${branch}:main"
      echo "    pushed to ${ORG}/${repo}"
    fi

    git branch -D "${branch}" >/dev/null 2>&1 || true
    splitted+=("${id} ${platform}")
    split_count=$((split_count + 1))
  done
done

if [ "${split_count}" -eq 0 ]; then
  echo "No blueprint directory found - nothing to split."
  exit 0
fi

echo "=== marking ${split_count} platform variant(s) available in the index"
for entry in "${splitted[@]}"; do
  # shellcheck disable=SC2086
  set -- ${entry}
  if [ "${DRY_RUN}" = "1" ]; then
    echo "    would set $1/$2 to available"
  else
    python3 "${index}/bin/set_platform_status.py" \
      --index "${index}/blueprints.yaml" \
      --id "$1" --platform "$2" --status available
  fi
done

if [ "${DRY_RUN}" = "1" ]; then
  echo "Dry run finished, nothing was created, pushed or changed."
  exit 0
fi

if git -C "${index}" diff --quiet -- blueprints.yaml; then
  echo "index already up to date"
  exit 0
fi

git -C "${index}" config user.name "github-actions[bot]"
git -C "${index}" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git -C "${index}" add blueprints.yaml
git -C "${index}" commit --quiet \
  -m "docs: mark blueprints available which have been split out" \
  -m "Written by the split job of the monorepo."
# The index was cloned when this job started, and somebody may have pushed to it since -
# a blueprint marked available by hand, or an entry added for a blueprint yet to come.
# Rebasing onto that keeps both changes instead of losing the race.
git -C "${index}" pull --rebase --quiet origin "${INDEX_BRANCH}"
git -C "${index}" push --quiet
echo "index updated - the organisation page is rendered by its own workflow"
