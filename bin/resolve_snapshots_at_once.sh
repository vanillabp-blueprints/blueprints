#!/usr/bin/env bash
#
# Resolves every dependency of a build in one short step, the VanillaBP snapshots with them,
# so that the build which follows reads all of them from one publish.
#
# Everything VanillaBP is 2.0.0-SNAPSHOT until the release, and the repositories of the
# framework publish several times a day. A publish takes about ten minutes, one artifact after
# the other. A build with --update-snapshots asks for each snapshot only when a module first
# needs it, and a build with tests runs for up to twenty minutes. So a build which overlaps a
# publish can take one artifact from before it and another one from after it. On the 12th of
# November 2026 a job took the new extensions-commons-spring-boot of the Business Cockpit and
# the old spi-for-java, which lacked a class the new one needs. The test failed with
# NoClassDefFoundError, and nothing in the blueprints had changed.
#
# So a job calls this script first, with --update-snapshots, and then builds with
# --no-snapshot-updates. The second call takes every snapshot this one put into the local
# repository and asks no repository for a newer one. What this call did not resolve, the build
# still downloads when it needs it. Resolving takes a minute or two, not twenty, so the window
# in which a publish can split a job is much smaller. It is not closed: a publish which runs
# during this minute can still split it. bin/platform_snapshot.sh writes down which snapshot
# builds the job took, so such a case can be seen in the summary of the job.
#
# Two goals do the resolving:
# - dependency:go-offline, for the dependencies and plugins of every module,
# - quarkus:go-offline, only in a Quarkus build. A Quarkus test resolves the deployment
#   artifacts of the extensions itself, while the test runs, and dependency:go-offline does not
#   know them. The plugin is named with its coordinates, because only the application module
#   declares it, and the version is the one the POMs give in 'quarkus.version'.
#
# Usage: bin/resolve_snapshots_at_once.sh <maven arguments without a goal...>
#        for example: -s .github/maven/settings.xml --batch-mode -Pcamunda8 [-f <pom>]
# The arguments are the ones of the build which follows, without its goals. They are passed
# to bin/mvn_with_retry.sh together with --update-snapshots.

set -o errexit
set -o nounset
set -o pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# the directory of the POM the build reads: the one of '-f', or the current one
pom_dir="."
previous=""
for argument in "$@"; do
  if [ "${previous}" = "-f" ] || [ "${previous}" = "--file" ]; then
    pom_dir="$(dirname "${argument}")"
  fi
  previous="${argument}"
done

goals=(dependency:go-offline)

# Every Quarkus blueprint writes its version into this property, in every POM. The first one
# found is enough.
quarkus_version="$(find "${pom_dir}" -name pom.xml -not -path '*/target/*' -print0 \
  | xargs -0 sed -n 's|.*<quarkus.version>\(.*\)</quarkus.version>.*|\1|p' \
  | head -1)"
if [ -n "${quarkus_version}" ]; then
  goals+=("io.quarkus:quarkus-maven-plugin:${quarkus_version}:go-offline")
fi

echo "Resolving the dependencies of this build at once: ${goals[*]}"
"${here}/mvn_with_retry.sh" "$@" --update-snapshots "${goals[@]}"
