-- select max(discovered_date) from filings where stream_timepoint is not null
CREATE INDEX filings_stream_discovered_date_idx ON filings (discovered_date) WHERE stream_timepoint IS NOT NULL;
