#!/usr/bin/env bash
#
# Render a picture of every process of every blueprint, for the README to show.
#
# A reader of a blueprint wants to see the process before reading about it, and a BPMN file
# is not something you read. The pictures are committed, so a README renders on GitHub
# without anybody having to run a tool.
#
# For each directory '<group>/<blueprint-id>/<platform>/' the models of one adapter are rendered,
# the first one alphabetically. The BPMN files below 'processes/<adapter-id>/' differ in
# their engine specific attributes only, never in their diagram, so one picture holds for
# every BPMS a blueprint supports.
#
# The picture goes to '<group>/<blueprint-id>/<platform>/docs/<process-id>.png', inside the
# directory the split turns into a repository.
#
# PNG rather than SVG on purpose: bpmn-js draws dark strokes on no background at all, so an
# SVG becomes unreadable in GitHub's dark mode. The PNG carries a white one.
#
# Requires bpmn-to-image (https://github.com/bpmn-io/bpmn-to-image):
#   npm install -g bpmn-to-image
#
set -euo pipefail

PLATFORMS=(springboot quarkus)
SCALE=2

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
cd "${root}"

if command -v bpmn-to-image >/dev/null 2>&1; then
  render=(bpmn-to-image)
elif command -v npx >/dev/null 2>&1; then
  render=(npx --yes bpmn-to-image)
else
  echo "bpmn-to-image is not available. Install it with 'npm install -g bpmn-to-image'." >&2
  exit 1
fi

count=0

for platform in "${PLATFORMS[@]}"; do
  for pom in */*/"${platform}"/pom.xml; do
    [ -f "${pom}" ] || continue
    blueprint="$(dirname "${pom}")"

    # One adapter directory per WORKFLOW MODULE, not per blueprint: a blueprint may hold
    # several modules, and each of them brings processes of its own. The models below
    # 'processes/<adapter-id>/' differ in engine specific attributes only, so the first
    # adapter directory of a module holds for all of them.
    adapter_dirs=()
    seen_modules=""
    while IFS= read -r candidate; do
      module="${candidate%/processes/*}"
      case " ${seen_modules} " in
        *" ${module} "*) continue ;;
      esac
      seen_modules="${seen_modules} ${module}"
      adapter_dirs+=("${candidate}")
    done < <(find "${blueprint}" -type d -path '*/processes/*' -not -path '*/target/*' | sort)

    if [ "${#adapter_dirs[@]}" -eq 0 ]; then
      echo "=== ${blueprint}: no BPMN files found, skipped"
      continue
    fi

    mkdir -p "${blueprint}/docs"

    for adapter_dir in "${adapter_dirs[@]}"; do
      for bpmn in "${adapter_dir}"/*.bpmn; do
        [ -f "${bpmn}" ] || continue
        image="${blueprint}/docs/$(basename "${bpmn}" .bpmn).png"
        echo "=== ${bpmn} -> ${image}"
        # A minimum size of 100x100 keeps the picture at the size of the diagram; the default
        # of 400x300 pads small models with empty space.
        "${render[@]}" \
          --no-footer \
          --scale="${SCALE}" \
          --min-dimensions=100x100 \
          "${bpmn}:${image}" >/dev/null
        count=$((count + 1))
      done
    done
  done
done

echo "rendered ${count} picture(s)"
