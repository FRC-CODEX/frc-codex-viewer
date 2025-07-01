#!/bin/bash

set -e
# Create HTTP_CACHE directory if it doesn't already exist.
mkdir -p ./_HTTP_CACHE

docker build -t frc-codex-lambda --platform linux/amd64 -f frc-codex-lambda.Dockerfile --provenance=false . &
BUILD_LAMBDA_PID_1=$!

docker build -t frc-codex-processor -f frc-codex-processor.Dockerfile --provenance=false . &
BUILD_PROCESSOR_PID_2=$!

docker build -t frc-codex-server -f frc-codex-server.Dockerfile --provenance=false . &
BUILD_SERVER_PID_3=$!

docker build -t frc-codex-support --platform linux/amd64 -f frc-codex-support.Dockerfile --provenance=false . &
BUILD_SUPPORT_PID_4=$!

wait $BUILD_LAMBDA_PID_1 $BUILD_PROCESSOR_PID_2 $BUILD_SERVER_PID_3 $BUILD_SUPPORT_PID_4
