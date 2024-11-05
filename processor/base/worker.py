import datetime
from abc import abstractmethod
from dataclasses import dataclass
from pathlib import Path

from processor.base.filing_download_result import FilingDownloadResult
from processor.base.job_message import JobMessage


@dataclass
class WorkerResult:
    filing_id: str
    error: str = ''
    logs: str = ''
    company_name: str | None = None
    company_number: str | None = None
    document_date: datetime.datetime | None = None
    viewer_entrypoint: str = ''
    oim_directory: str = ''
    filename: str = ''
    success: bool = False

    # Analytics
    download_time: float | None = None
    total_processing_time: float | None = None
    total_uploaded_bytes: int | None = None
    upload_time: float | None = None
    worker_time: float | None = None


class Worker:

    @abstractmethod
    def work(
            self,
            job_message: JobMessage,
            filingDownload: FilingDownloadResult,
            viewer_directory: Path,
            taxonomy_package_urls: list[str]
    ) -> WorkerResult:
        pass
