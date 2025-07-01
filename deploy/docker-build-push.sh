#!/bin/sh

set -eu
v=${1?version}
# Create HTTP_CACHE directory if it doesn't already exist.
mkdir -p ./_HTTP_CACHE
docker build --platform linux/amd64 -t frc-codex-lambda -f frc-codex-lambda.Dockerfile --provenance=false .
docker build --platform linux/amd64 -t frc-codex-server -f frc-codex-server.Dockerfile --provenance=false .
docker build --platform linux/amd64 -t frc-codex-support -f frc-codex-support.Dockerfile --provenance=false .
docker tag frc-codex-lambda 390403885523.dkr.ecr.eu-west-2.amazonaws.com/frc-codex/processor-lambda:$v
docker tag frc-codex-server 390403885523.dkr.ecr.eu-west-2.amazonaws.com/frc-codex/server:$v
docker tag frc-codex-support 390403885523.dkr.ecr.eu-west-2.amazonaws.com/frc-codex/support:$v
docker push 390403885523.dkr.ecr.eu-west-2.amazonaws.com/frc-codex/processor-lambda:$v
docker push 390403885523.dkr.ecr.eu-west-2.amazonaws.com/frc-codex/server:$v
docker push 390403885523.dkr.ecr.eu-west-2.amazonaws.com/frc-codex/support:$v
