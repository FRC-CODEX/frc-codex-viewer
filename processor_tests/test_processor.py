from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock, patch

from processor.base.filing_download_result import FilingDownloadResult
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
                oim_directory='oim_directory',
            ),
            'filing_id2': WorkerResult(
                'filing_id2',
                error='error2',
                logs='logs2',
                success=False,
            ),
        }
        mock_get_target_path.return_value = FilingDownloadResult(Path('download_url1'), Path('filing.zip'), [])
        upload_manager = MockUploadManager()
        processor = Processor(
            download_manager=Mock(),
            upload_manager=upload_manager,
            worker_factory=MockWorkerFactory(worker_results_map=worker_results_map),
        )
        queue_manager = MockQueueManager(job_messages=job_messages)
        worker_results = processor.run_from_queue(queue_manager)

        self.assertEqual(worker_results[0].company_name, None)
        self.assertEqual(worker_results[0].company_number, None)
        self.assertEqual(worker_results[0].document_date, None)
        self.assertEqual(worker_results[0].error, '')
        self.assertEqual(worker_results[0].filing_id, 'filing_id1')
        self.assertEqual(worker_results[0].logs, 'logs1')
        self.assertEqual(worker_results[0].success, True)
        self.assertEqual(worker_results[0].viewer_entrypoint, 'viewer_entrypoint1')
        self.assertEqual(worker_results[0].oim_directory, 'oim_directory')

        self.assertEqual(worker_results[1].company_name, None)
        self.assertEqual(worker_results[1].company_number, None)
        self.assertEqual(worker_results[1].document_date, None)
        self.assertEqual(worker_results[1].error, 'error2')
        self.assertEqual(worker_results[1].filing_id, 'filing_id2')
        self.assertEqual(worker_results[1].logs, 'logs2')
        self.assertEqual(worker_results[1].success, False)
        self.assertEqual(worker_results[1].viewer_entrypoint, '')
        self.assertEqual(worker_results[1].oim_directory, '')

        self.assertEqual(upload_manager.uploads, [
            ('filing_id1', 'viewer'),
        ])
