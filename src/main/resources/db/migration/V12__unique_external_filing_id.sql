DROP INDEX filings_registry_code_external_filing_id_idx;
-- select filing_id from filings where registry_code = $1 and external_filing_id = $2
CREATE UNIQUE INDEX filings_registry_code_external_filing_id_idx ON filings (registry_code, external_filing_id) INCLUDE (filing_id);
