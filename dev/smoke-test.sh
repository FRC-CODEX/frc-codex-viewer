#!/bin/bash

set -e

CURL_OPTS="--include --fail --connect-timeout 30 --max-time 300"

echo "Testing survey form submission..."
curl $CURL_OPTS -X POST http://localhost:8080/survey \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "searchUtilityRating=5&searchSpeedRating=4&viewerSpeedRating=3"
          
echo "Wait for smoke testing to be ready..."
curl $CURL_OPTS --silent http://localhost:8080/admin/smoketest/wait

echo "Test invocation"
curl $CURL_OPTS --silent -L http://localhost:8080/admin/smoketest/invoke

echo "Test homepage"
curl $CURL_OPTS http://localhost:8080/

echo "Test CH API client"
curl $CURL_OPTS http://localhost:8080/admin/smoketest/companieshouse/company/00324341

echo "Test CH archive client"
curl $CURL_OPTS http://localhost:8080/admin/smoketest/companieshouse/history

echo "Test FCA API"
curl $CURL_OPTS http://localhost:8080/admin/smoketest/fca

echo "Test indexer page"
curl $CURL_OPTS http://localhost:8080/admin/smoketest/indexer

echo "Test queue page"
curl $CURL_OPTS http://localhost:8080/admin/smoketest/queue

echo "Test database page"
curl $CURL_OPTS http://localhost:8080/admin/smoketest/database

echo "Test support action: get_ch_indexing_stats"
curl $CURL_OPTS 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"get_ch_indexing_stats"}'

echo "Test support action: list_errors"
curl $CURL_OPTS 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"list_errors"}'

echo "Test support action: reset_filings"
curl $CURL_OPTS 'localhost:8082/2015-03-31/functions/function/invocations' \
  --data '{"action":"reset_filings","company_number":"1234567890"}'
