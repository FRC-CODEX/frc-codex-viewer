-- select * from filings where status = 'failed'
CREATE INDEX status_failed_idx ON filings (status) WHERE status = 'failed';
