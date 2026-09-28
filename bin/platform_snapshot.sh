#!/usr/bin/env bash
#
# Writes down which platform build the current Maven run resolved.
#
# Every VanillaBP artifact is 2.0.0-SNAPSHOT until the release, and the platform publishes
# under that same string several times a day, so the version a build reports says nothing
# about which platform it met. The timestamp and the build number do, and they name a
# platform build which can be read next to the one before it.
#
# Not knowing this cost a morning on the 26th of September 2026. One blueprint job was red
# because the schema artifact had grown the table VANILLABP_HOUSEKEEPING, and seven green
# sibling jobs of the same run looked like proof that the rest was fine. They were not
# comparable: the green ones had run at 13:46 the day before, the platform published the
# table at 14:39 that day, and the red one was the second attempt of the same run, started
# the next morning. Nothing in either run said which platform it had built against.
#
# Usage: bin/platform_snapshot.sh <marker file> [output file]
#
# The marker is a file created before the Maven run. Only metadata newer than it is read,
# so a runner which carried a platform jar in from a cache does not get to name it. When
# nothing is newer, everything is listed and the report says that it may be the cache
# talking.
#
# The report goes to the file given as the second argument, to the step summary when
# GITHUB_STEP_SUMMARY is set, and to standard output.

set -o nounset
set -o pipefail

marker="${1:?the marker file created before the Maven run}"
out="${2:-platform-snapshot.md}"

repository="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}"
snapshots="${repository}/io/vanillabp"

# Maven writes one maven-metadata-<repository id>.xml per remote repository a snapshot came
# from, next to the jar it resolved. The ids of the VanillaBP repositories all begin with
# 'vanillabp-', see .github/workflows/github-packages-settings.xml, so this reads what came
# from the platform and skips the modules a build installed itself, which carry no such
# file.
mapfile -t metadata < <(find "$snapshots" \
  -path '*-SNAPSHOT/maven-metadata-vanillabp-*.xml' -newer "$marker" 2>/dev/null | sort)

stale=""
if [ "${#metadata[@]}" -eq 0 ]; then
  mapfile -t metadata < <(find "$snapshots" \
    -path '*-SNAPSHOT/maven-metadata-vanillabp-*.xml' 2>/dev/null | sort)
  stale="Maven refreshed none of these while this run was going, so they are what the cache
of the runner held and not necessarily what was built against."
fi

: > "$out"
for meta in "${metadata[@]}"; do
  directory=$(dirname "$meta")
  version=$(basename "$directory")
  # the path under the local repository is the group and the artifact, and the artifact
  # alone is not enough: 'core' is the name of several of them
  coordinates=$(dirname "${directory#"${repository}/"}")
  group=$(dirname "$coordinates" | tr / .)
  artifact=$(basename "$coordinates")
  timestamp=$(sed -n 's|.*<timestamp>\(.*\)</timestamp>.*|\1|p' "$meta" | head -1)
  build=$(sed -n 's|.*<buildNumber>\(.*\)</buildNumber>.*|\1|p' "$meta" | head -1)
  if [ -n "$timestamp" ] && [ -n "$build" ]; then
    resolved="${version%-SNAPSHOT}-${timestamp}-${build}"
  else
    # a repository which answers without a timestamp still says which version was asked
    # for, and that is better than leaving the artifact out
    resolved="${version}, and $(basename "$meta") holds no timestamp"
  fi
  printf -- '- `%s:%s` %s\n' "$group" "$artifact" "$resolved" >> "$out"
done

# Every VanillaBP repository serves every VanillaBP package, so one artifact is answered by
# several of the repositories in the settings and written down once per repository. The same
# line several times says nothing twice.
sort -u -o "$out" "$out"

if [ -n "$stale" ]; then
  printf '\n%s\n' "$stale" >> "$out"
fi

if [ ! -s "$out" ]; then
  {
    echo "No platform snapshot was resolved in this run. Either the build did not reach its"
    echo "dependencies, or it could not reach the registry."
  } > "$out"
fi

{
  echo "### The platform this run was built against"
  echo
  cat "$out"
} > "${out}.summary"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "${out}.summary" >> "$GITHUB_STEP_SUMMARY"
fi
cat "${out}.summary"
rm -f "${out}.summary"
