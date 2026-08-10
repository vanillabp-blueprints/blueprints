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

CAMUNDA_VERSION="${CAMUNDA_VERSION:-8.8.1}"
ELASTICSEARCH_VERSION="${ELASTICSEARCH_VERSION:-8.18.2}"
REST_PORT="${REST_PORT:-8080}"
NETWORK=vanillabp-camunda8
ELASTICSEARCH=vanillabp-elasticsearch
CAMUNDA=vanillabp-camunda8-cluster
# Booting Elasticsearch and Camunda takes a while on a cold CI runner.
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-300}"

start() {
  docker network create "${NETWORK}" >/dev/null 2>&1 || true

  docker run --detach \
    --name "${ELASTICSEARCH}" \
    --network "${NETWORK}" \
    --env discovery.type=single-node \
    --env xpack.security.enabled=false \
    --env "ES_JAVA_OPTS=-Xms1g -Xmx1g" \
    "docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}" >/dev/null

  await "Elasticsearch" "docker exec ${ELASTICSEARCH} curl --silent --fail http://localhost:9200/_cluster/health"

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

  echo "Camunda 8 is listening on http://localhost:${REST_PORT}"
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
