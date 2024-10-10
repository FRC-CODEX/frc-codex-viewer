import json
import logging
import tempfile
import zipfile
from pathlib import Path

from processor.base.download_manager import DownloadManager
from processor.base.queue_manager import QueueManager, JobMessage, ResultMessage
from processor.base.upload_manager import UploadManager
from processor.base.worker import WorkerResult
from processor.base.worker_factory import WorkerFactory

logger = logging.getLogger(__name__)


class Processor:

    def __init__(
            self,
            download_manager: DownloadManager,
            upload_manager: UploadManager,
            worker_factory: WorkerFactory,
    ):
        self._download_manager = download_manager
        self._upload_manager = upload_manager
        self._worker_factory = worker_factory

    def _download_filing(self, job_message: JobMessage, directory: Path) -> tuple[Path | None, list[str]]:
        filing_path = self._download_manager.download_filing(
            job_message.filing_id,
            job_message.registry_code,
            job_message.download_url,
            directory
        )
        return self._get_target_path(filing_path)

    def _get_target_path(self, filing_path: Path) -> tuple[Path | None, list[str]]:
        if not zipfile.is_zipfile(filing_path):
            return filing_path, []
        target_path = None
        with zipfile.ZipFile(filing_path) as zip_file:
            for file in zip_file.namelist():
                if file.endswith('.html') or file.endswith('.xhtml'):
                    target_path = filing_path / file
                    break
            return target_path, zip_file.namelist()

    def _handle_message(self, job_message: JobMessage, queue_manager: QueueManager) -> WorkerResult:
        logger.info(
            "Processing started for job message: (Message: %s, Filing: %s)",
            job_message.message_id, job_message.filing_id
        )
        worker_result = self._process_filing(job_message)
        logger.info(
            "Processing finished for job message: (Message: %s, Filing: %s)",
            job_message.message_id, job_message.filing_id
        )
        result_message = ResultMessage(
            company_name=worker_result.company_name,
            company_number=worker_result.company_number,
            document_date=worker_result.document_date,
            error=worker_result.error,
            filing_id=job_message.filing_id,
            logs=worker_result.logs,
            success=worker_result.success,
            viewer_entrypoint=worker_result.viewer_entrypoint,
        )
        queue_manager.publish_result(result_message)
        logger.info(
            "Added result message for job message: (Message: %s, Filing: %s)",
            job_message.message_id, job_message.filing_id
        )
        queue_manager.complete_job(job_message)
        logger.info(
            "Removed job message: (Message: %s, Filing: %s)",
            job_message.message_id, job_message.filing_id
        )
        return worker_result

    def _process_filing(self, job_message: JobMessage) -> WorkerResult:
        try:
            with tempfile.TemporaryDirectory(prefix='ixbrl-viewer_') as temp_dir:
                temp_dir_path = Path(temp_dir)
                # Download filing
                target_path, namelist = self._download_filing(job_message, temp_dir_path)
                if not target_path:
                    logger.error(
                        "Target path could not be determined in filing from %s: (Message: %s, Filing: %s)",
                        namelist, job_message.message_id, job_message.filing_id
                    )
                    return WorkerResult(
                        job_message.filing_id,
                        error=f'Target path could not be determined in filing from {namelist}.'
                    )
                logger.info('Using target path: %s', target_path)
                # Prepare directory for viewer files
                viewer_directory = temp_dir_path / 'viewer'
                viewer_directory.mkdir()

                worker = self._worker_factory.create_worker(job_message)
                worker_result = worker.work(job_message, target_path, viewer_directory)
                if worker_result.success:
                    self._upload_manager.upload_files(job_message.filing_id, viewer_directory)
                else:
                    logger.error(
                        "Worker failed to process filing: %s (Message: %s, Filing: %s)",
                        worker_result.error, job_message.message_id, job_message.filing_id
                    )
                return worker_result
        except Exception as e:
            logger.exception(
                "An unexpected error occurred while processing the filing: (Message: %s, Filing: %s)",
                job_message.message_id, job_message.filing_id
            )
            return WorkerResult(
                job_message.filing_id,
                error='An unexpected error occurred while processing the filing: ' + str(e)
            )

    def run_from_queue(self, queue_manager: QueueManager) -> list[WorkerResult]:
        results = []
        for message in queue_manager.get_jobs():
            results.append(self._handle_message(message, queue_manager))
        return results

    def run_from_lambda(self, event, context) -> WorkerResult:
        body = event
        if 'body' in body:
            body = json.loads(body['body'])

        job_message = JobMessage(
            filing_id=body['filing_id'],
            download_url=body['filing_url'],
            registry_code=body['registry_code'],
            receipt_handle=context.aws_request_id,
            message_id=context.aws_request_id,
        )
        logger.info(
            "Processing started for Lambda event: (Event: %s, Filing: %s)",
            job_message.message_id, job_message.filing_id
        )
        worker_result = self._process_filing(job_message)
        logger.info(
            "Processing finished for Lambda event: (Event: %s, Filing: %s)",
            job_message.message_id, job_message.filing_id
        )
        return worker_result
