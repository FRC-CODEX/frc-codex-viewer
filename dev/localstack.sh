#!/bin/sh

# Create SQS Queues
echo "Initializing localstack SQS: frc_codex_jobs"
awslocal sqs create-queue --queue-name frc_codex_jobs --region "$REGION_NAME"
echo "Initializing localstack SQS: frc_codex_results"
awslocal sqs create-queue --queue-name frc_codex_results --region "$REGION_NAME"

# Create S3 Bucket
echo "Initializing localstack S3 bucket: $S3_HTTP_CACHE_BUCKET_NAME"
awslocal s3 mb "s3://$S3_HTTP_CACHE_BUCKET_NAME" --region "$REGION_NAME"
echo "Initializing localstack S3 bucket: $S3_INDEXER_UPLOADS_BUCKET_NAME"
awslocal s3 mb "s3://$S3_INDEXER_UPLOADS_BUCKET_NAME" --region "$REGION_NAME"
echo "Initializing localstack S3 bucket: $S3_RESULTS_BUCKET_NAME"
awslocal s3 mb "s3://$S3_RESULTS_BUCKET_NAME" --region "$REGION_NAME"
echo "Initializing localstack S3 bucket: $S3_TAXONOMY_PACKAGES_BUCKET_NAME"
awslocal s3 mb "s3://$S3_TAXONOMY_PACKAGES_BUCKET_NAME" --region "$REGION_NAME"

# Populating S3 bucket with test data
echo "Initializing taxonomy packages in bucket: $S3_TAXONOMY_PACKAGES_BUCKET_NAME"
awslocal s3 sync --exclude "*" --include "*.zip" \
  "/tmp/taxonomy_packages" \
  "s3://$S3_TAXONOMY_PACKAGES_BUCKET_NAME"
echo "Initializing indexer uploads in bucket: $S3_INDEXER_UPLOADS_BUCKET_NAME"
awslocal s3 sync --exclude "*" --include "*.csv" \
  "/tmp/indexer_uploads" \
  "s3://$S3_INDEXER_UPLOADS_BUCKET_NAME"
