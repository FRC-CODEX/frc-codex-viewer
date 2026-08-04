package com.frc.codex.clients.companieshouse;

import java.time.Instant;

import com.frc.codex.indexer.IndexerJob;

public interface CompaniesHouseStreamListener extends IndexerJob {
	Instant getLastEventReceivedDate();
}
