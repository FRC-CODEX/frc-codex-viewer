import datetime
import logging
import shutil
import zipfile
from pathlib import Path

import boto3
from botocore.exceptions import ClientError

from processor.base.cache_manager import CacheManager
from processor.processor_options import ProcessorOptions


CACHE_IGNORE_SUFFIXES = {'.lock', '.zip', '.tmp', '.DS_Store'}

logger = logging.getLogger(__name__)


class MainCacheManager(CacheManager):

    def __init__(self, processor_options: ProcessorOptions, cache_zip_path: Path):
        self._cache_zip_path = cache_zip_path
        self._s3_client = boto3.client(
            's3',
            region_name=processor_options.s3_region_name,
        )
        self._bucket_name = processor_options.s3_http_cache_bucket_name
        self._cache_last_modified: datetime.datetime | None = None

    def _get_modified_timestamp(self) -> datetime.datetime:
        head = self._s3_client.head_object(
            Bucket=self._bucket_name, Key=self._cache_zip_path.name
        )
        return head['LastModified']

    def download(self, backup_path: Path | None = None) -> bool:
        """
        Download the HTTP cache from S3. If the cache is not found on S3, a backup cache is used, if it exists.
        :return: Whether a cache was downloaded (or copied from backup)
        """
        s3_path = self._cache_zip_path.name
        try:
            # Check if cache download is required based on modified timestamp.
            cache_last_modified = self._get_modified_timestamp()
            if not self._cache_last_modified:
                logger.info("Initial cache download required.")
            else:
                if cache_last_modified <= self._cache_last_modified:
                    logger.info("HTTP cache is up to date.")
                    return False
                else:
                    logger.info(
                        "HTTP cache modified at %s since %s.",
                        cache_last_modified, self._cache_last_modified
                    )

            logger.info(
                "Downloading HTTP cache last modified at %s: (%s) from %s: %s",
                cache_last_modified, self._cache_zip_path, self._bucket_name, s3_path
            )
            self._s3_client.download_file(
                self._bucket_name,
                s3_path,
                str(self._cache_zip_path)
            )
            self._cache_last_modified = cache_last_modified
        except ClientError as e:
            if e.response['Error']['Code'] != '404':
                return False
            logger.info("HTTP cache not found on S3")
            if not backup_path or not backup_path.exists():
                return False
            logger.info("Copying backup cache from %s", backup_path)
            shutil.copyfile(backup_path, self._cache_zip_path)
        with zipfile.ZipFile(self._cache_zip_path, 'r') as zip_file:
            logger.debug("Initial HTTP cache: \n%s", '\n'.join(zip_file.namelist()))
        return True

    def extract(self, cache_directories: list[Path]) -> None:
        """
        Extract the HTTP cache to the given directories.
        :param cache_directories:
        :return:
        """
        for cache_directory in cache_directories:
            with zipfile.ZipFile(self._cache_zip_path, 'r') as zip_file:
                zip_file.extractall(cache_directory)

    def sync(self, cache_directories: list[Path]) -> set[str]:
        """
        Update the cache zip with new files from the given directories.
        :param cache_directories:
        :return: Set of added arcnames
        """
        paths_added = set()
        namelist = set()
        if self._cache_zip_path.exists():
            with zipfile.ZipFile(self._cache_zip_path, 'r') as zip_file:
                namelist.update(zip_file.namelist())
        with zipfile.ZipFile(self._cache_zip_path, 'a') as zip_file:
            for cache_directory in cache_directories:
                for cache_path in cache_directory.glob('**/*'):
                    if any(cache_path.suffix.endswith(suffix) for suffix in CACHE_IGNORE_SUFFIXES):
                        continue
                    if cache_path.is_dir():
                        continue
                    arcname = str(cache_path.relative_to(cache_directory))
                    if arcname in namelist:
                        continue
                    zip_file.write(cache_path, arcname)
                    paths_added.add(arcname)
                    namelist.add(arcname)
                    logger.info("Added to cache: %s (%s)", arcname, cache_path)
        return paths_added

    def upload(self) -> None:
        """
        Upload the updated cache zip to S3.
        :return:
        """
        s3_path = self._cache_zip_path.name
        with zipfile.ZipFile(self._cache_zip_path, 'r') as zip_file:
            new_namelist = zip_file.namelist()
        logger.debug("Files in updated cache: %s", '\n'.join(new_namelist))
        logger.info(
            "Added files to cache. Uploading HTTP cache: (%s) to %s: %s",
            self._cache_zip_path, self._bucket_name, s3_path
        )
        self._s3_client.upload_file(str(self._cache_zip_path), self._bucket_name, s3_path)
