#!/usr/bin/env bash
#
# A Camunda 8 cluster for the integration tests, as small as one can be.
#
# Camunda 8 keeps its secondary storage in Elasticsearch and does not finish booting
# without it, so this is two containers rather than one. Authentication is switched off:
# the tests address the REST API directly, and a cluster that lives for the length of one
# build has nothing to protect.
#
# Usage: bin/camunda8_cluster.sh start|stop|logs
#        CAMUNDA_VERSION, ELASTICSEARCH_VERSION and REST_PORT can be overridden.
#        CAMUNDA_VERSION defaults to the version the Camunda 8 adapter accepts, see below.
#
# Used by .github/workflows/build.yaml and usable locally for the same purpose:
#
#   bin/camunda8_cluster.sh start
#   cd blueprints-bpmn/bpmn-service-task/springboot && mvn -Pcamunda8 install verify
#   bin/camunda8_cluster.sh stop
#
# One cluster may serve several runs, one after the other. The timer start events of the
# blueprints use a cycle without an end, such as R/PT5S, for this reason. A cycle of R1 fires
# ONCE per model, and a cluster which already holds that model creates no new timer for the
# next deployment of it, because a model deployed again without a change is no new version.
# With R1 the second run against the same cluster waited for a start which happened during
# the first one, and its test timed out although nothing was broken.

set -o errexit
set -o nounset
set -o pipefail

# The cluster has to be at least as new as the Camunda client the Camunda 8 adapter is built
# against. That client is the lowest cluster version the adapter accepts, and the adapter says
# so in its startup log. An older cluster rejects requests the newer client sends: with 8.8.34
# every job activation ended in "Request property [tenantFilter] cannot be parsed", so a
# workflow started and its task was never delivered.
#
# So this script does not pin a version. It asks the adapter, see adapter_client_version
# below. A pin was here until October 2026, and it fell behind: it said 8.10.0 while the
# adapter was built against 8.10.5. No build failed, and somebody had to read the startup log
# of the adapter to see it. Set CAMUNDA_VERSION to run another version on purpose.
CAMUNDA_VERSION="${CAMUNDA_VERSION:-}"
ELASTICSEARCH_VERSION="${ELASTICSEARCH_VERSION:-8.18.2}"
REST_PORT="${REST_PORT:-8080}"
NETWORK=vanillabp-camunda8
ELASTICSEARCH=vanillabp-elasticsearch
CAMUNDA=vanillabp-camunda8-cluster
# Booting Elasticsearch and Camunda takes a while on a cold CI runner.
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-300}"
# The root of this repository. The blueprint POMs below it name the adapter version.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
# How often a cluster whose exporter never starts working is thrown away and started again.
START_ATTEMPTS="${START_ATTEMPTS:-2}"
# How long the exporter gets to write its first index before the cluster counts as broken.
EXPORTER_TIMEOUT_SECONDS="${EXPORTER_TIMEOUT_SECONDS:-120}"

start() {
  local attempt=1

  if [ -z "${CAMUNDA_VERSION}" ]; then
    CAMUNDA_VERSION="$(adapter_client_version)" || exit 1
  fi
  echo "Starting Camunda 8 ${CAMUNDA_VERSION}"

  while true; do
    start_containers

    if exporting; then
      echo "Camunda 8 is listening on http://localhost:${REST_PORT}"
      return 0
    fi

    # A broker whose exporter never starts working hands no work out: a workflow starts, the
    # cluster confirms it, and no job is ever delivered. From the outside that looks like a
    # defect of whichever blueprint runs into it, and on 2026-08-20 it cost four jobs of one
    # CI run and an hour of reading test logs. Better to say it here.
    echo "The exporter of Camunda 8 wrote no index to Elasticsearch (attempt ${attempt})." >&2

    if [ "${attempt}" -ge "${START_ATTEMPTS}" ]; then
      echo "Its secondary storage is not working, so every test against it would wait for" >&2
      echo "deliveries which never happen." >&2
      logs
      exit 1
    fi

    attempt=$((attempt + 1))
    echo "Starting over." >&2
    stop
  done
}

# The version of the Camunda client which the Camunda 8 adapter is built against. It is read
# from the POM the adapter publishes. That POM names the client as a dependency with a plain
# version, and the adapter has a test which keeps it that way (Camunda8PublishedPomTest). The
# adapter version is the one the blueprint POMs name. They all name the same one, and this
# function stops if they do not.
#
# Maven fetches the POM. Locally it first tries your own Maven settings, so a run does not
# replace an adapter you installed yourself with the published one. Those settings often do not
# name the snapshot repository of Maven Central, or name it only in a profile you have to pass.
# So if that fails, the script tries again with .github/maven/settings.xml, which names that
# repository, and you need no profile. In CI the script goes straight to that file, because the
# CI build uses it too.
adapter_client_version() {
  local adapter
  adapter="$(adapter_version)" || exit 1

  local work
  work="$(mktemp -d)"

  local ci_settings="${ROOT}/.github/maven/settings.xml"
  local tried
  if [ -n "${CI:-}" ]; then
    tried="${ci_settings}"
    fetch_adapter_pom "${adapter}" "${work}" --settings "${ci_settings}" || true
  else
    tried="your own Maven settings, then ${ci_settings}"
    fetch_adapter_pom "${adapter}" "${work}" \
      || fetch_adapter_pom "${adapter}" "${work}" --settings "${ci_settings}" \
      || true
  fi

  if ! compgen -G "${work}/*.pom" >/dev/null; then
    cannot_fetch_adapter_pom "${adapter}" "${tried}" "${work}/maven.log"
    rm -rf "${work}"
    exit 1
  fi

  local client
  client="$(python3 -I - "${work}"/*.pom <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
for dependency in ET.parse(sys.argv[1]).getroot().iterfind("m:dependencies/m:dependency", ns):
    if (dependency.findtext("m:groupId", namespaces=ns) == "io.camunda"
            and dependency.findtext("m:artifactId", namespaces=ns) == "camunda-client-java"):
        print(dependency.findtext("m:version", default="", namespaces=ns).strip())
PYTHON
)"
  rm -rf "${work}"

  # a version with a placeholder in it would mean the adapter published its source POM
  if ! [[ "${client}" =~ ^[0-9]+\.[0-9]+\.[0-9]+ ]]; then
    echo "The POM of camunda8-adapter ${adapter} names no usable version of" >&2
    echo "io.camunda:camunda-client-java (found '${client}')." >&2
    echo "Set CAMUNDA_VERSION to the version you want." >&2
    exit 1
  fi

  echo "camunda8-adapter ${adapter} is built against Camunda client ${client}" >&2
  echo "${client}"
}

# Copies the POM of the adapter into the given directory. Further arguments go to Maven, such
# as the settings to use. What Maven says goes into maven.log in that directory. Each attempt
# starts the file again, so after a failure it holds the reasons of the last attempt.
fetch_adapter_pom() {
  local adapter="$1"
  local work="$2"
  shift 2

  # Maven runs in the empty work directory. In the root of this repository it would read the
  # whole build first, and that fails for the same reason when the snapshots cannot be fetched.
  # --update-snapshots, because the adapter is a SNAPSHOT: a cached POM may be days old and
  # name an older client than the one the build will get.
  (cd "${work}" && maven --batch-mode --update-snapshots "$@" \
    org.apache.maven.plugins:maven-dependency-plugin:3.10.0:copy \
    "-Dartifact=io.vanillabp:camunda8-adapter:${adapter}:pom" \
    "-DoutputDirectory=${work}") >"${work}/maven.log" 2>&1
}

cannot_fetch_adapter_pom() {
  local adapter="$1"
  local tried="$2"
  local log="$3"

  echo "Maven could not fetch the POM of io.vanillabp:camunda8-adapter:${adapter}." >&2
  echo "This script needs it to know which Camunda version to start." >&2
  echo "It tried with ${tried}." >&2
  echo "Maven said:" >&2
  grep '^\[ERROR\] .' "${log}" \
    | grep --invert-match --extended-regexp \
        '^\[ERROR\] (BUILD FAILURE|-> \[Help|\[Help|To see the full|Re-run Maven|For more information)' \
    | head -n 10 >&2 || true
  echo "Do one of these, and then start the script again:" >&2
  echo "  1. Check that this machine can reach Maven Central and its snapshot repository" >&2
  echo "     https://central.sonatype.com/repository/maven-snapshots/ (no proxy or firewall" >&2
  echo "     in the way, Maven not set to offline)." >&2
  echo "  2. Check that the adapter version ${adapter} exists. It comes from" >&2
  echo "     'camunda8-adapter.version' in the blueprint POMs." >&2
  echo "  3. Build that adapter version yourself with 'mvn install' in the camunda8-adapter" >&2
  echo "     repository, so Maven finds it in your local repository." >&2
  echo "  4. Skip the lookup and name the Camunda version yourself:" >&2
  echo "     CAMUNDA_VERSION=<a tag of the image camunda/camunda> bin/camunda8_cluster.sh start" >&2
}

# The version of the Camunda 8 adapter which the blueprints build against. Each blueprint
# POM names it in the property 'camunda8-adapter.version'.
adapter_version() {
  local versions
  versions="$(grep --recursive --no-filename --only-matching --include=pom.xml \
      --exclude-dir=target '<camunda8-adapter.version>[^<]*' "${ROOT}"/blueprints-* \
    | sed 's/.*>//' | sort --unique)"

  if [ -z "${versions}" ] || [ "$(echo "${versions}" | wc -l)" -ne 1 ]; then
    echo "The blueprint POMs should name exactly one version of the Camunda 8 adapter" >&2
    echo "in 'camunda8-adapter.version', but they name: ${versions:-none}" >&2
    echo "Set CAMUNDA_VERSION to the version you want." >&2
    exit 1
  fi

  echo "${versions}"
}

# Maven from the PATH, as in CI, or else the wrapper of this repository.
maven() {
  if command -v mvn >/dev/null 2>&1; then
    mvn "$@"
  else
    "${ROOT}/mvnw" "$@"
  fi
}

start_containers() {
  docker network create "${NETWORK}" >/dev/null 2>&1 || true

  docker run --detach \
    --name "${ELASTICSEARCH}" \
    --network "${NETWORK}" \
    --env discovery.type=single-node \
    --env xpack.security.enabled=false \
    --env "ES_JAVA_OPTS=-Xms1g -Xmx1g" \
    "docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}" >/dev/null

  # 'wait_for_status=yellow' rather than the bare health endpoint: that one answers while the
  # cluster is still red, and a broker connecting to a red Elasticsearch is exactly the state
  # this script used to hand over.
  await "Elasticsearch" "docker exec ${ELASTICSEARCH} curl --silent --fail http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=5s"

  docker run --detach \
    --name "${CAMUNDA}" \
    --network "${NETWORK}" \
    --publish "${REST_PORT}:8080" \
    --publish 26500:26500 \
    --env CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
    --env CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
    --env CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch \
    --env "CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://${ELASTICSEARCH}:9200" \
    "camunda/camunda:${CAMUNDA_VERSION}" >/dev/null

  await "Camunda 8" "curl --silent --fail http://localhost:${REST_PORT}/v2/topology"
}

# Whether the exporter is doing its work, asked of Elasticsearch rather than of the broker's
# log. The log is no use for this question: it warns 'Failed to open exporter' while
# Elasticsearch is still starting and succeeds on a later attempt, so a cluster which then runs
# a whole build carries those lines too (measured on 2026-08-20, twice, on a cluster which was
# fine). What separates a working exporter from a broken one is whether Camunda's indices
# exist, and only Elasticsearch can be asked that.
exporting() {
  local waited=0

  while ! indices_exist; do
    if [ "${waited}" -ge "${EXPORTER_TIMEOUT_SECONDS}" ]; then
      return 1
    fi
    sleep 5
    waited=$((waited + 5))
  done

  echo "The exporter of Camunda 8 writes to Elasticsearch after ${waited}s"

}

indices_exist() {
  docker exec "${ELASTICSEARCH}" \
      curl --silent --fail "http://localhost:9200/_cat/indices?h=index" 2>/dev/null \
    | grep -q "^camunda-"
}

# Polls until the check succeeds, and says what it was waiting for when it does not.
await() {
  local what="$1"
  local check="$2"
  local waited=0

  until ${check} >/dev/null 2>&1; do
    if [ "${waited}" -ge "${READY_TIMEOUT_SECONDS}" ]; then
      echo "${what} was not ready within ${READY_TIMEOUT_SECONDS}s" >&2
      logs
      exit 1
    fi
    sleep 5
    waited=$((waited + 5))
  done

  echo "${what} is ready after ${waited}s"
}

logs() {
  echo "=== ${ELASTICSEARCH} ==="
  docker logs --tail 50 "${ELASTICSEARCH}" 2>&1 || true
  echo "=== ${CAMUNDA} ==="
  docker logs --tail 100 "${CAMUNDA}" 2>&1 || true
}

stop() {
  docker rm --force "${CAMUNDA}" "${ELASTICSEARCH}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  logs) logs ;;
  *)
    echo "Usage: $0 start|stop|logs" >&2
    exit 2
    ;;
esac
