import os
from functools import cached_property
from pathlib import Path


class ProcessorOptions:

    def __init__(self, secrets_filepath):
        self.secrets_filepath = secrets_filepath
        with open(secrets_filepath) as f:
            self._secrets = {
                key.strip(): value.strip()
                for key, value in [
                    line.split('=') for line in f.readlines()
                ]
            }

    @property
    def companies_house_rest_api_key(self):
        return self._secrets['COMPANIES_HOUSE_REST_API_KEY']

    @cached_property
    def http_cache_directory(self) -> Path:
        return Path(os.getenv('HTTP_CACHE_DIRECTORY', '/tmp/_HTTP_CACHE'))

    @cached_property
    def ixbrl_viewer_plugin_path(self) -> Path:
        return Path(os.getenv(
            'IXBRL_VIEWER_PLUGIN_PATH',
            '/var/task/processor/plugins/frc-ixbrl-viewer/iXBRLViewerPlugin'
        ))

    @cached_property
    def maximum_processors(self):
        return int(os.getenv('MAXIMUM_PROCESSORS', 0))

    @cached_property
    def s3_http_cache_bucket_name(self):
        return os.getenv('S3_HTTP_CACHE_BUCKET_NAME')

    @cached_property
    def s3_results_bucket_name(self):
        return os.getenv('S3_RESULTS_BUCKET_NAME')

    @cached_property
    def s3_region_name(self):
        return os.getenv('S3_REGION_NAME')

    @cached_property
    def sqs_jobs_queue_name(self):
        return os.getenv('SQS_JOBS_QUEUE_NAME')

    @cached_property
    def sqs_results_queue_name(self):
        return os.getenv('SQS_RESULTS_QUEUE_NAME')

    @cached_property
    def sqs_region_name(self):
        return os.getenv('SQS_REGION_NAME')

    @cached_property
    def sqs_max_messages(self):
        return os.getenv('SQS_MAX_MESSAGES', 1)

    @cached_property
    def sqs_visibility_timeout(self):
        return os.getenv('SQS_VISIBILITY_TIMEOUT', 60)

    @cached_property
    def sqs_wait_time(self):
        return os.getenv('SQS_WAIT_TIME', 20)

    @cached_property
    def sync_interval_seconds(self):
        return os.getenv('SYNC_INTERVAL_SECONDS', 0)
