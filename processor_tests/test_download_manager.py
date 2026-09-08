from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock, patch

from processor.main.main_download_manager import MainDownloadManager


class TestDownloadManager(TestCase):

    @patch("processor.main.main_download_manager.MainDownloadManager._retrieve")
    @patch("processor.main.main_download_manager.MainDownloadManager._save")
    def test_download_filing_success_fca(self, mock_save, mock_retrieve) -> None:
        mock_retrieve.return_value = Mock()
        mock_save.return_value = None

        download_manager = MainDownloadManager(Mock())
        filing_path = download_manager.download_filing(
            filing_id="filing_id",
            registry_code="FCA",
            download_url="download_url",
            directory=Path("directory"),
        )
        mock_retrieve.assert_called_once_with(
            "download_url",
            auth=None,
            headers=None
        )
        mock_save.assert_called_once_with(
            mock_retrieve.return_value,
            Path("directory/filing.zip")
        )
        self.assertEqual(filing_path, Path("directory/filing.zip"))

    @patch("processor.main.main_download_manager.MainDownloadManager._retrieve")
    @patch("processor.main.main_download_manager.MainDownloadManager._save")
    def test_download_filing_success_ch(self, mock_save, mock_retrieve) -> None:
        headers = {
            "Content-Disposition": 'inline;filename="filename"'
        }
        mock_retrieve.return_value = Mock(headers=headers)
        mock_save.return_value = None

        download_manager = MainDownloadManager(Mock())
        filing_path = download_manager.download_filing(
            filing_id="filing_id",
            registry_code="CH",
            download_url="download_url",
            directory=Path("directory"),
        )
        mock_retrieve.assert_called_once_with(
            url="download_url",
            auth=None,
            headers=None
        )
        mock_save.assert_called_once_with(
            mock_retrieve.return_value,
            Path("directory/filename")
        )
        self.assertEqual(filing_path, Path("directory/filename"))

    @patch("processor.main.main_download_manager.MainDownloadManager._retrieve")
    @patch("processor.main.main_download_manager.MainDownloadManager._save")
    def test_download_filing_bad_region_code(self, mock_save, mock_retrieve) -> None:
        mock_retrieve.return_value = Mock()
        mock_save.return_value = None

        download_manager = MainDownloadManager(Mock())
        with self.assertRaisesRegex(ValueError, "Unknown registry code: ZZ"):
            download_manager.download_filing(
                filing_id="filing_id",
                registry_code="ZZ",
                download_url="download_url",
                directory=Path("directory"),
            )

    @patch("processor.main.main_download_manager.MainDownloadManager._retrieve")
    @patch("processor.main.main_download_manager.MainDownloadManager._save")
    def test_download_filing_missing_filename(self, mock_save, mock_retrieve) -> None:
        headers = {
            "Content-Disposition": "inline;"
        }
        mock_retrieve.return_value = Mock(headers=headers)
        mock_save.return_value = None

        download_manager = MainDownloadManager(Mock())
        with self.assertRaisesRegex(AssertionError, "Could not find filename in Content-Disposition: inline;"):
            download_manager.download_filing(
                filing_id="filing_id",
                registry_code="CH",
                download_url="download_url?contentType=application/xml",
                directory=Path("directory"),
            )
