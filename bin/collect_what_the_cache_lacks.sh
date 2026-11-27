#!/usr/bin/env bash
#
# Resolves what every blueprint needs on every engine, once, and copies what the restored
# Maven cache did not hold into a directory of its own. The nightly saves that directory as a
# small cache of the run, and every job of its matrix lays it over the cache of main.
#
# Why. The matrix reads the cache which the last build on main wrote, and that cache holds the
# releases the adapters needed THAT day. The adapters publish snapshots every day, and a
# snapshot can bring a new release with it. On the 26th of November 2026 the Operaton adapter
# moved from Operaton 2.2.1 to 2.2.2 at five in the morning, and no build on main had run
# since. So each of the eighty Operaton jobs of that night asked Maven Central for the same
# engine, twenty at a time, Central answered with 429, and four blueprints went red although
# nothing in them was wrong. With this script one job asks Central, and the matrix asks
# nobody.
#
# The commands are the ones of the job 'monorepo' in build.yaml, which fills the cache of
# main: dependency:go-offline for every engine, and quarkus:go-offline for every Quarkus
# module. Neither needs the blueprints installed: go-offline leaves out the modules of the
# reactor, as it does in bin/resolve_snapshots_at_once.sh.
#
# Snapshots are left out of the directory. Every job of the matrix asks for them itself, so
# that it builds against the newest ones (see 'Remove the snapshots the cache carried in' in
# nightly.yaml).
#
# Usage: bin/collect_what_the_cache_lacks.sh <directory> <maven arguments without a goal...>
#        for example: -s .github/maven/settings.xml --batch-mode --no-transfer-progress
# It runs in the root of the repository. ENGINES says which engines, all four by default.
# The local repository is ~/.m2/repository, or the one -Dmaven.repo.local names.

set -o errexit
set -o nounset
set -o pipefail

here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" > /dev/null && pwd)"
root="$(dirname "${here}")"
cd "${root}"

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <directory> <maven arguments...>" >&2
  exit 2
fi
target="$1"
shift

ENGINES="${ENGINES:-camunda7 camunda8 operaton cibseven}"

local_repository="${HOME}/.m2/repository"
for argument in "$@"; do
  case "${argument}" in
    -Dmaven.repo.local=*) local_repository="${argument#-Dmaven.repo.local=}" ;;
  esac
done
mkdir -p "${local_repository}" "${target}"

# What the restored cache holds, so that what this script adds can be told apart.
before="$(mktemp)"
after="$(mktemp)"
trap 'rm -f "${before}" "${after}"' EXIT
(cd "${local_repository}" && find . -type f | sort) > "${before}"

# The application and the bundle of a cockpit blueprint need the UI jar of a frontend, which
# no build of this job makes, so they are left out as in build.yaml.
chained="$(python3 bin/build_matrix.py --chained-modules)"
leave_out=()
if [ -n "${chained}" ]; then
  leave_out=(-pl "${chained}")
fi

for engine in ${ENGINES}; do
  echo "Resolving what the blueprints need on ${engine}"
  "${here}/mvn_with_retry.sh" "$@" --update-snapshots -P"${engine}" "${leave_out[@]}" \
    dependency:go-offline
done

# Every module below a 'quarkus' directory is named on its own, as in build.yaml: '-amd' on
# the blueprint POM would miss the modules whose parent is a different POM.
chained_paths="$(printf '%s\n' "${chained}" | tr ',' '\n' | sed 's/^!//')"
modules="$(find blueprints-* -path '*/quarkus/*' -name pom.xml \
  -not -path '*/target/*' -not -path '*/node_modules/*' -printf '%h\n' \
  | grep -vxF -e "${chained_paths:-/}" | sort | paste -sd, -)"
versions="$(find blueprints-* -path '*/quarkus/pom.xml' -not -path '*/target/*' -print0 \
  | xargs -0 sed -n 's|.*<quarkus.version>\(.*\)</quarkus.version>.*|\1|p' | sort -u)"
if [ -n "${modules}" ] && [ -n "${versions}" ]; then
  for version in ${versions}; do
    for engine in ${ENGINES}; do
      echo "Resolving what Quarkus ${version} needs on ${engine}"
      "${here}/mvn_with_retry.sh" "$@" -P"${engine}" -pl "${modules}" \
        "io.quarkus:quarkus-maven-plugin:${version}:go-offline"
    done
  done
fi

# What is new, without the snapshots and without the markers of failed downloads.
(cd "${local_repository}" && find . -type f | sort) > "${after}"
comm -13 "${before}" "${after}" \
  | { grep -vE -e '-SNAPSHOT/' -e '\.lastUpdated$' -e '/resolver-status\.properties$' || true; } \
  | (cd "${local_repository}" && tar -cf - -T -) \
  | tar -C "${target}" -xf -

files="$(find "${target}" -type f | wc -l)"
size="$(du -sh "${target}" | cut -f1)"
echo "The cache lacked ${files} files (${size}), now in ${target}."
