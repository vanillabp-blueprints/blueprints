#!/usr/bin/env bash
#
# Runs Maven and repeats it when the build died on the way to a repository rather than on
# anything in this repository.
#
# Maven Central answers a runner which resolves everything from scratch with 403 or 429
# often enough to be the most common red build of the nightly, and the failure looks
# nothing like a broken blueprint: the POM cannot even be read. Repeating that is free.
# Repeating a failed test is not, it hides the very thing the build is for, so this only
# repeats what the log proves to be a transfer.
#
# It tries three times. A 429 is a ban, and Central keeps it for longer than the 30
# seconds this script used to wait, so the second attempt ran into the same ban. Now the
# pause grows: 60 seconds before the second attempt, 180 before the third, each plus a
# random 0 to 30 seconds. The random part keeps jobs which were refused together from
# all coming back in the same second. A transfer failure ends an attempt in its first
# minute, so the worst case is about five minutes of waiting plus one full build. The
# longest job of a green run takes six minutes, so that stays inside the job limit of 30
# minutes. A fourth attempt would wait nine minutes more, and a nightly which keeps
# trying stops being a nightly.
#
# Usage: bin/mvn_with_retry.sh <maven arguments...>
#        ATTEMPTS, PAUSE_SECONDS, PAUSE_FACTOR and JITTER_SECONDS can be overridden.

set -o errexit
set -o nounset
set -o pipefail

ATTEMPTS="${ATTEMPTS:-3}"
# the pause before the second attempt; each further pause is PAUSE_FACTOR times longer
PAUSE_SECONDS="${PAUSE_SECONDS:-60}"
PAUSE_FACTOR="${PAUSE_FACTOR:-3}"
JITTER_SECONDS="${JITTER_SECONDS:-30}"

# What a repository refusing to answer looks like in the log. Every one of these is about
# reaching an artifact, none of them can be caused by the code being built. Under load Maven
# Central also answers "not found" for artifacts which are there, and Maven then reports
# them as "could not be resolved".
TRANSFER_FAILURES='Could not transfer artifact|Could not resolve dependencies|could not be resolved|Non-resolvable parent POM|status code: 40[39]|status code: 429|Too Many Requests|status code: 5[0-9][0-9]|Connection reset|Connection timed out|Read timed out|Failed to read artifact descriptor|Remote host terminated the handshake'

# The settings file turns on the profile for GitHub Packages when VBP_SNAPSHOTS_FROM says so.
# Maven itself sees that, but a Quarkus test does not: it resolves the deployment artifacts
# of the extensions with a resolver of its own, inside the test JVM. That resolver reads the
# profiles from the Maven command line only, and without this one it asked Maven Central
# for io.vanillabp, which the filter forbids, and every Quarkus test of Operaton and CIB seven
# failed with "Failed to collect dependencies of ...-deployment". So the profile is named
# here as well.
if [ "${VBP_SNAPSHOTS_FROM:-}" = "github-packages" ]; then
  set -- -Psnapshots-from-github-packages "$@"
fi

log="$(mktemp)"
trap 'rm -f "${log}"' EXIT

pause="${PAUSE_SECONDS}"
for attempt in $(seq 1 "${ATTEMPTS}"); do
  if mvn "$@" 2>&1 | tee "${log}"; then
    exit 0
  fi

  if ! grep -qE "${TRANSFER_FAILURES}" "${log}"; then
    echo "The build failed on something other than reaching a repository, so it is not repeated."
    exit 1
  fi

  if [ "${attempt}" -eq "${ATTEMPTS}" ]; then
    echo "A repository kept refusing to answer through ${ATTEMPTS} attempts. Giving up."
    exit 1
  fi

  wait_for=$((pause + RANDOM % (JITTER_SECONDS + 1)))
  echo "A repository did not answer (attempt ${attempt} of ${ATTEMPTS}). Trying again in ${wait_for}s."
  sleep "${wait_for}"
  pause=$((pause * PAUSE_FACTOR))
done
