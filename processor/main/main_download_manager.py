import logging
import os
import re
from pathlib import Path

import boto3
import requests

from processor.base.download_manager import DownloadManager
from processor.processor_options import ProcessorOptions

logger = logging.getLogger(__name__)

LOCAL_PACKAGES_DIRECTORY = Path("/tmp/local_packages")


class MainDownloadManager(DownloadManager):

    def __init__(self, processor_options: ProcessorOptions):
        self._processor_options = processor_options
        self._package_urls: list[str] | None = None

    def _download_ch_filing(self, filing_id, download_url, directory) -> Path:
        logger.info(
            "Downloading filing from CH: From %s to %s (Filing: %s)",
            download_url, directory, filing_id
        )
        response = self._retrieve(url=download_url, auth=None, headers=None)
        filing_path = self._get_ch_download_path(directory, response)
        self._save(response, filing_path)
        return filing_path

    def _download_fca_filing(self, filing_id: str, download_url: str, directory: Path) -> Path:
        filename = os.path.basename(download_url) if download_url.endswith(".zip") else "filing.zip"
        filing_path = directory / filename
        logger.info("Downloading filing from FCA: (%s) from %s to %s", filing_id, download_url, filing_path)
        response = self._retrieve(download_url, auth=None, headers=None)
        self._save(response, filing_path)
        return filing_path

    def _download_packages(self) -> list[Path]:
        LOCAL_PACKAGES_DIRECTORY.mkdir(parents=True, exist_ok=True)
        bucket_name = self._processor_options.s3_taxonomy_packages_bucket_name
        s3_client = boto3.client("s3")
        response = s3_client.list_objects_v2(Bucket=bucket_name)
        downloaded_paths = []
        for item in response.get("Contents", []):
            key = item["Key"]
            file_path = LOCAL_PACKAGES_DIRECTORY / key
            if file_path.exists():
                continue
            file_path.parent.mkdir(parents=True, exist_ok=True)
            s3_client.download_file(bucket_name, key, str(file_path))
            downloaded_paths.append(file_path)
        return downloaded_paths

    def _get_ch_download_path(self, directory, response):
        # Get original filename from: 'inline;filename="..."'
        content_disposition = response.headers["Content-Disposition"]
        filename_match = re.search(r'filename="(.+)"', content_disposition)
        assert filename_match, f"Could not find filename in Content-Disposition: {content_disposition}"
        filename = filename_match.group(1)
        return directory / filename

    def _retrieve(self, url: str, auth: tuple[str, str] | None, headers: dict[str, str] | None) -> requests.Response:
        response = requests.get(
            url=url,
            auth=auth,
            headers=headers
        )
        response.raise_for_status()
        return response

    def _save(self, response: requests.Response, path: Path) -> None:
        with open(path, "wb") as file:
            file.write(response.content)

    def download_filing(self, filing_id: str, registry_code: str, download_url: str, directory: Path) -> Path:
        if registry_code == "CH":
            return self._download_ch_filing(filing_id, download_url, directory)
        if registry_code == "FCA":
            return self._download_fca_filing(filing_id, download_url, directory)
        raise ValueError(f"Unknown registry code: {registry_code}")

    def get_package_urls(self) -> list[str]:
        if self._package_urls is None:
            LOCAL_PACKAGES_DIRECTORY.mkdir(parents=True, exist_ok=True)
            try:
                downloaded_paths = self._download_packages()
                logger.info("Downloaded (%s) package(s): (%s)", len(downloaded_paths), downloaded_paths)
            except Exception as e:
                logger.error("Failed to download taxonomy packages.", e)
            self._package_urls = sorted(str(file) for file in LOCAL_PACKAGES_DIRECTORY.glob("*.zip"))
            logger.info("Discovered (%s) local package(s): (%s)", len(self._package_urls), self._package_urls)
        return self._package_urls
