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
# Usage: bin/mvn_with_retry.sh <maven arguments...>
#        ATTEMPTS and PAUSE_SECONDS can be overridden.

set -o errexit
set -o nounset
set -o pipefail

ATTEMPTS="${ATTEMPTS:-3}"
PAUSE_SECONDS="${PAUSE_SECONDS:-30}"

# What a repository refusing to answer looks like in the log. Every one of these is about
# reaching an artifact, none of them can be caused by the code being built.
TRANSFER_FAILURES='Could not transfer artifact|Could not resolve dependencies|Non-resolvable parent POM|status code: 40[39]|status code: 5[0-9][0-9]|Connection reset|Connection timed out|Read timed out|Failed to read artifact descriptor|Remote host terminated the handshake'

log="$(mktemp)"
trap 'rm -f "${log}"' EXIT

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

  echo "A repository did not answer (attempt ${attempt} of ${ATTEMPTS}). Trying again in ${PAUSE_SECONDS}s."
  sleep "${PAUSE_SECONDS}"
done
