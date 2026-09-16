import logging
import multiprocessing
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

from processor.main.main_cache_manager import MainCacheManager
from processor.main.main_download_manager import MainDownloadManager
from processor.main.main_queue_manager import MainQueueManager
from processor.main.main_upload_manager import MainUploadManager
from processor.main.main_worker_factory import MainWorkerFactory
from processor.processor import Processor
from processor.processor_options import ProcessorOptions


BACKUP_CACHE_ZIP_PATH = Path("/tmp/_HTTP_CACHE.zip")

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="{%(processName)s} [%(levelname)s] %(message)s")


def _get_processor_count(processor_options: ProcessorOptions) -> int:
    cpu_count = multiprocessing.cpu_count()
    maximum_processors = max(0, processor_options.maximum_processors)
    if maximum_processors == 0:
        logger.info(
            "Processor count set to CPU count of %s.",
            cpu_count
        )
        return cpu_count
    if maximum_processors <= cpu_count:
        logger.info(
            "Processor count limited to %s by maximum_processors option.",
            maximum_processors
        )
        return maximum_processors
    logger.warning(
        "Value of %s for maximum_processors option exceeded CPU count of %s. "
        "Using CPU count instead.",
        maximum_processors, cpu_count
    )
    return cpu_count


def _run_processor(processor_options: ProcessorOptions, http_cache_directory: Path, next_sync_ts: datetime) -> None:
    processor = Processor(
        download_manager=MainDownloadManager(processor_options),
        upload_manager=MainUploadManager(processor_options),
        worker_factory=MainWorkerFactory(processor_options, http_cache_directory)
    )
    queue_manager = MainQueueManager(processor_options)
    while True:
        processor.run_from_queue(queue_manager)
        if datetime.now() >= next_sync_ts:
            break


def main():
    processor_options = ProcessorOptions()

    with tempfile.TemporaryDirectory(prefix="shared-cache_") as global_dir:
        cache_zip_path = Path(global_dir) / "_HTTP_CACHE.zip"
        cache_manager = MainCacheManager(processor_options, cache_zip_path)
        cache_zip_downloaded = cache_manager.download(backup_path=BACKUP_CACHE_ZIP_PATH)

        processor_count = _get_processor_count(processor_options)

        temp_directories = {}
        for i in range(processor_count):
            temp_directories[i] = tempfile.TemporaryDirectory(prefix=f"processor-cache-{i}_")

        temp_directory_paths = [Path(temp_dir.name) for temp_dir in temp_directories.values()]
        if cache_zip_downloaded:
            cache_manager.extract(temp_directory_paths)

        try:
            while True:
                next_sync_ts = datetime.now() + timedelta(seconds=processor_options.sync_interval_seconds)
                logger.info("Running processors. Next sync at: %s", next_sync_ts)
                processes = []
                for i, temp_dir in temp_directories.items():
                    cache_directory = Path(temp_dir.name)
                    process = multiprocessing.Process(
                        target=_run_processor,
                        args=(processor_options, cache_directory, next_sync_ts)
                    )
                    process.name = f"Processor-{i}"
                    process.start()
                    processes.append(process)
                for process in processes:
                    process.join()
                # Processors stopped to allow for syncing
                try:
                    paths_added = cache_manager.sync(temp_directory_paths)
                    if paths_added:
                        cache_manager.upload()
                    elif cache_manager.download():
                        cache_manager.extract(temp_directory_paths)
                except Exception:
                    logger.exception("An unexpected error occurred while syncing the cache.")
        finally:
            for temp_dir in temp_directories.values():
                temp_dir.cleanup()


if __name__ == "__main__":
    main()
