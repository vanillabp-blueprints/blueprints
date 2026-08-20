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
#
# Used by .github/workflows/build.yaml and usable locally for the same purpose:
#
#   bin/camunda8_cluster.sh start
#   cd bpmn-service-task/springboot && mvn -Pcamunda8 install verify
#   bin/camunda8_cluster.sh stop

set -o errexit
set -o nounset
set -o pipefail

# The cluster has to be at least as new as the client the Camunda 8 adapter is built
# against, currently camunda-client-java 8.9.16. An older cluster rejects requests the newer
# client sends: with 8.8.34 every job activation ended in "Request property [tenantFilter]
# cannot be parsed", so a workflow started and its task was never delivered.
CAMUNDA_VERSION="${CAMUNDA_VERSION:-8.9.16}"
ELASTICSEARCH_VERSION="${ELASTICSEARCH_VERSION:-8.18.2}"
REST_PORT="${REST_PORT:-8080}"
NETWORK=vanillabp-camunda8
ELASTICSEARCH=vanillabp-elasticsearch
CAMUNDA=vanillabp-camunda8-cluster
# Booting Elasticsearch and Camunda takes a while on a cold CI runner.
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-300}"
# How often a cluster whose exporter never starts working is thrown away and started again.
START_ATTEMPTS="${START_ATTEMPTS:-2}"
# How long the exporter gets to write its first index before the cluster counts as broken.
EXPORTER_TIMEOUT_SECONDS="${EXPORTER_TIMEOUT_SECONDS:-120}"

start() {
  local attempt=1

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
