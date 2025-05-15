import json
import logging
from collections.abc import Iterable

import boto3
from botocore.exceptions import ClientError

from processor.base.job_message import JobMessage
from processor.base.queue_manager import QueueManager
from processor.base.worker import WorkerResult
from processor.processor_options import ProcessorOptions

logger = logging.getLogger(__name__)


class MainQueueManager(QueueManager):

    def __init__(self, processor_options: ProcessorOptions):
        self._jobs_queue = MainQueueManager._get_queue(processor_options.sqs_jobs_queue_name, processor_options)
        self._results_queue = MainQueueManager._get_queue(processor_options.sqs_results_queue_name, processor_options)
        self._max_number = processor_options.sqs_max_messages
        self._wait_time = processor_options.sqs_wait_time
        self._arelle_version = processor_options.arelle_version
        self._ixbrl_viewer_version = processor_options.ixbrl_viewer_version
        self._service_version = processor_options.service_version

    def complete_job(self, job_message: JobMessage) -> None:
        self._jobs_queue.delete_messages(
            Entries=[{
                'Id': job_message.message_id,
                'ReceiptHandle': job_message.receipt_handle
            }]
        )

    def get_jobs(self) -> Iterable[JobMessage]:
        """
        Receive a batch of job messages in a single request from an SQS queue.
        :return: The list of Message objects received. These each contain the body
                 of the message and metadata and custom attributes.
        """
        try:
            logger.debug("Waiting for messages...")
            messages = self._jobs_queue.receive_messages(
                MessageAttributeNames=["All"],
                MaxNumberOfMessages=self._max_number,
                WaitTimeSeconds=self._wait_time,
            )
            logger.debug("Received %s message(s).", len(messages))
            for message in messages:
                attributes_names = [key for key in message.message_attributes] if message.message_attributes else []
                logger.info(
                    "Processing started for message with attributes %s: (%s) %s",
                    attributes_names, message.message_id, message.body
                )
                yield JobMessage(
                    download_url=message.message_attributes['DownloadUrl']['StringValue'],
                    filing_id=message.body,
                    format=message.message_attributes['Format']['StringValue'],
                    message_id=message.message_id,
                    receipt_handle=message.receipt_handle,
                    registry_code=message.message_attributes['RegistryCode']['StringValue'],
                )
                logger.info("Processing completed for message: (%s) %s", message.message_id, message.body)
        except ClientError as error:
            logger.exception("Couldn't receive messages from queue: %s", self._jobs_queue)
            raise error

    def publish_result(self, worker_result: WorkerResult) -> None:
        message_body = {
            'ArelleVersion': self._arelle_version,
            'ArelleViewerVersion': self._ixbrl_viewer_version,
            'ServiceVersion': self._service_version,
            'CompanyName': worker_result.company_name,
            'CompanyNumber': worker_result.company_number,
            'Error': worker_result.error,
            'FilingId': worker_result.filing_id,
            'Logs': worker_result.logs,
            'Success': worker_result.success,
            "Filename": worker_result.filename,
            'ViewerEntrypoint': worker_result.viewer_entrypoint,
            'OimDirectory': worker_result.oim_directory,
            # Analytics
            'DownloadTime': worker_result.download_time,
            'TotalProcessingTime': worker_result.total_processing_time,
            'TotalUploadedBytes': worker_result.total_uploaded_bytes,
            'UploadTime': worker_result.upload_time,
            'WorkerTime': worker_result.worker_time,
        }
        if worker_result.document_date is not None:
            message_body['DocumentDate'] = worker_result.document_date.strftime('%Y-%m-%d')
        self._results_queue.send_message(
            MessageBody=json.dumps(message_body),
        )

    @staticmethod
    def _get_queue(queue_name: str, processor_options: ProcessorOptions):
        session = boto3.session.Session()
        client = session.resource(
            'sqs',
            region_name=processor_options.sqs_region_name,
        )
        queue = client.get_queue_by_name(QueueName=queue_name)
        return queue
