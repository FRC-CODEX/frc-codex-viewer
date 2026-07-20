#!/bin/bash

set -e

BASE_CURL_OPTS=(--include --fail --connect-timeout 30)
CURL_OPTS=("${BASE_CURL_OPTS[@]}" --max-time 300)

echo "Testing survey form submission..."
curl "${CURL_OPTS[@]}" -X POST http://localhost:8080/survey \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "searchUtilityRating=5&searchSpeedRating=4&viewerSpeedRating=3"

CURL_TIMEOUT_EXIT_CODE=28
FALLBACK_COMPANY_NUMBER=11162569
FALLBACK_FILING_ID="MzQzOTA3MDIxN2FkaXF6a2N4"

wait_for_smoketest() {
  local max_time="$1"
  curl "${BASE_CURL_OPTS[@]}" --silent --max-time "$max_time" http://localhost:8080/admin/smoketest/wait
}

echo "Wait for smoke testing to be ready..."
wait_status=0
wait_for_smoketest 60 || wait_status=$?
if [ $wait_status -ne 0 ]; then
  if [ $wait_status -ne $CURL_TIMEOUT_EXIT_CODE ]; then
    echo "ERROR: /admin/smoketest/wait failed with curl exit code $wait_status" >&2
    exit $wait_status
  fi

  echo "No Companies House stream event arrived within timeout; injecting fallback stream event for company $FALLBACK_COMPANY_NUMBER (filing $FALLBACK_FILING_ID)..."

  STREAM_EVENT_JSON=$(cat <<EOF
{
  "resource_kind": "filing-history",
  "resource_id": "$FALLBACK_FILING_ID",
  "resource_uri": "/company/$FALLBACK_COMPANY_NUMBER/filing-history/$FALLBACK_FILING_ID",
  "event": {
    "type": "changed",
    "timepoint": 1
  },
  "data": {
    "transaction_id": "$FALLBACK_FILING_ID",
    "category": "accounts",
    "date": "2024-01-01",
    "action_date": "2024-01-01"
  }
}
EOF
  )

  set +e
  POSTGRES_CONTAINER=$(docker compose -f ./dev/compose.yml ps -q postgres)
  docker exec "$POSTGRES_CONTAINER" psql -U frc_codex -d frc_codex -c \
    "INSERT INTO stream_events (timepoint, json) VALUES (1, '$STREAM_EVENT_JSON');"
  insert_status=$?
  set -e

  if [ $insert_status -ne 0 ]; then
    echo "ERROR: Could not insert the fallback stream event into stream_events." >&2
    exit 1
  fi

  echo "Retrying wait for smoke testing to be ready..."
  retry_status=0
  wait_for_smoketest 120 || retry_status=$?
  if [ $retry_status -ne 0 ]; then
    echo "ERROR: The fallback stream event for company $FALLBACK_COMPANY_NUMBER (filing $FALLBACK_FILING_ID) could not be processed into a PENDING filing." >&2
    exit 1
  fi
fi

echo "Test invocation"
curl "${CURL_OPTS[@]}" --silent -L http://localhost:8080/admin/smoketest/invoke

echo "Test homepage"
curl "${CURL_OPTS[@]}" http://localhost:8080/

echo "Test CH API client"
curl "${CURL_OPTS[@]}" http://localhost:8080/admin/smoketest/companieshouse/company/10178367

echo "Test CH archive client"
curl "${CURL_OPTS[@]}" http://localhost:8080/admin/smoketest/companieshouse/history

echo "Test FCA API"
curl "${CURL_OPTS[@]}" http://localhost:8080/admin/smoketest/fca

echo "Test indexer page"
curl "${CURL_OPTS[@]}" http://localhost:8080/admin/smoketest/indexer

echo "Test queue page"
curl "${CURL_OPTS[@]}" http://localhost:8080/admin/smoketest/queue

echo "Test database page"
curl "${CURL_OPTS[@]}" http://localhost:8080/admin/smoketest/database

echo "Test support action: delete_filing"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"delete_filing","filing_id": "cfc59bc3-1899-40ae-87f3-bd199bee8171", "test_mode": true}'

echo "Test support action: get_ch_indexing_stats"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"get_ch_indexing_stats"}'

echo "Test support action: get_ch_streaming_stats"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"get_ch_streaming_stats"}'

echo "Test support action: get_filing_details"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"get_filing_details","filing_id":"cfc59bc3-1899-40ae-87f3-bd199bee8171"}'

echo "Test support action: list_errors"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"list_errors"}'

echo "Test support action: reset_archives"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"reset_archives","archive_type":"daily"}'

echo "Test support action: reset_companies"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"reset_companies","company_number":"1234567890"}'

echo "Test support action: reset_filings"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"reset_filings","company_number":"1234567890"}'

echo "Test support action: reset_stream_events"
curl "${CURL_OPTS[@]}" 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"reset_stream_events","timepoint_before":0}'
