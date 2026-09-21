from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock

from processor.base.filing_download_result import FilingDownloadResult
from processor.base.job_message import JobMessage
from processor.main.workers.ixbrl_viewer_worker import IxbrlViewerWorker


class TestIxbrlViewerWorker(TestCase):

    def setUp(self) -> None:
        self.worker = IxbrlViewerWorker(Mock())

    def _get_plugins(
            self,
            registry_code: str,
            namelist: list[str],
    ) -> list[str]:
        job_message = JobMessage(
            filing_id="filing_id",
            format="zip",
            download_url="download_url",
            registry_code=registry_code,
            receipt_handle="receipt_handle",
            message_id="message_id",
        )
        filing_download = FilingDownloadResult(Path("filing.zip"), namelist)
        return self.worker._get_plugins(job_message, filing_download)

    def test_ch_report_package_includes_inline_document_set_plugin(self) -> None:
        self.assertEqual(
            self._get_plugins(
                "CH",
                [
                    "filing/META-INF/reportPackage.json",
                    "filing/reports/report.xhtml",
                ],
            ),
            ["inlineXbrlDocumentSet", "ixbrl-viewer", "saveLoadableOIM"],
        )

    def test_ch_zip_with_root_manifest_excludes_inline_document_set_plugin(self) -> None:
        self.assertEqual(
            self._get_plugins(
                "CH",
                [
                    "META-INF/reportPackage.json",
                    "reports/report.xhtml",
                ],
            ),
            ["ixbrl-viewer", "saveLoadableOIM"],
        )

    def test_ch_non_report_package_zip_excludes_inline_document_set_plugin(self) -> None:
        self.assertEqual(
            self._get_plugins(
                "CH",
                [
                    "accounts/financialStatement.xhtml",
                    "cic34/cicReport.xhtml",
                ],
            ),
            ["ixbrl-viewer", "saveLoadableOIM"],
        )

    def test_fca_non_report_package_zip_includes_inline_document_set_plugin(self) -> None:
        self.assertEqual(
            self._get_plugins("FCA", ["report.xhtml"]),
            ["inlineXbrlDocumentSet", "ixbrl-viewer", "saveLoadableOIM"],
        )
