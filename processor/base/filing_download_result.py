from dataclasses import dataclass
from pathlib import Path


@dataclass
class FilingDownloadResult:
    download_path: Path
    target_path: Path | None
    namelist: list[str]
