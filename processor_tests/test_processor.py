from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock, patch

from processor.base.job_message import JobMessage
from processor.base.worker import WorkerResult
from processor.processor import Processor
from processor_tests.mock.mock_queue_manager import MockQueueManager
from processor_tests.mock.mock_upload_manager import MockUploadManager
from processor_tests.mock.mock_worker_factory import MockWorkerFactory


class TestProcessor(TestCase):

    @patch('processor.processor.Processor._get_target_path')
    def test_processor_run_success(self, mock_get_target_path) -> None:
        job_messages = [
            JobMessage(
                filing_id='filing_id1',
                registry_code='registry_code1',
                download_url='download_url1',
                receipt_handle='receipt_handle1',
                message_id='message_id1',
            ),
            JobMessage(
                filing_id='filing_id2',
                registry_code='registry_code2',
                download_url='download_url2',
                receipt_handle='receipt_handle2',
                message_id='message_id2',
            ),
        ]
        worker_results_map = {
            'filing_id1': WorkerResult(
                'filing_id1',
                logs='logs1',
                success=True,
                viewer_entrypoint='viewer_entrypoint1',
            ),
            'filing_id2': WorkerResult(
                'filing_id2',
                error='error2',
                logs='logs2',
                success=False,
            ),
        }
        mock_get_target_path.return_value = (Path('download_url1'), [])
        upload_manager = MockUploadManager()
        processor = Processor(
            download_manager=Mock(),
            upload_manager=upload_manager,
            worker_factory=MockWorkerFactory(worker_results_map=worker_results_map),
        )
        queue_manager = MockQueueManager(job_messages=job_messages)
        worker_results = processor.run_from_queue(queue_manager)

        self.assertEqual(worker_results, [
            WorkerResult(
                'filing_id1',
                logs='logs1',
                success=True,
                total_uploaded_bytes=1024,
                viewer_entrypoint='viewer_entrypoint1',
            ),
            WorkerResult(
                'filing_id2',
                error='error2',
                logs='logs2',
                success=False,
                total_uploaded_bytes=0,
            )
        ])
        self.assertEqual(queue_manager.worker_results, [
            WorkerResult(
                company_name=None,
                company_number=None,
                document_date=None,
                error='',
                filing_id='filing_id1',
                logs='logs1',
                success=True,
                total_uploaded_bytes=1024,
                viewer_entrypoint='viewer_entrypoint1',
            ),
            WorkerResult(
                company_name=None,
                company_number=None,
                document_date=None,
                error='error2',
                filing_id='filing_id2',
                logs='logs2',
                success=False,
                total_uploaded_bytes=0,
                viewer_entrypoint='',
            ),
        ])
        self.assertEqual(upload_manager.uploads, [
            ('filing_id1', 'viewer'),
        ])
