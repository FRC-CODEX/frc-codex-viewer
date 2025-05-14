from dataclasses import dataclass


@dataclass(frozen=True)
class JobMessage:
    filing_id: str
    format: str
    download_url: str
    registry_code: str
    receipt_handle: str
    message_id: str
