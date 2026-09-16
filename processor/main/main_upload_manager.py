import logging
from pathlib import Path

import boto3

from processor.base.upload_manager import UploadManager

logger = logging.getLogger(__name__)


class MainUploadManager(UploadManager):

    def __init__(self, processor_options):
        self._processor_options = processor_options
        self._s3_client = boto3.client(
            "s3",
            region_name=processor_options.s3_region_name,
        )

    def upload_files(self, filing_id: str, viewer_directory: Path) -> int:
        bucket_name = self._processor_options.s3_results_bucket_name
        total_bytes = 0
        for viewer_file in viewer_directory.rglob("*"):
            if not viewer_file.is_file():
                continue
            total_bytes += viewer_file.stat().st_size
            relative_path = viewer_file.relative_to(viewer_directory)
            s3_path = Path(filing_id) / relative_path
            logger.info("Uploading viewer file: (%s) to %s: %s", viewer_file, bucket_name, s3_path)
            self._s3_client.upload_file(str(viewer_file), bucket_name, str(s3_path))
        return total_bytes
