package com.frc.codex.clients.companieshouse.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseCompany;
import com.frc.codex.clients.companieshouse.CompaniesHouseFiling;
import com.frc.codex.clients.companieshouse.RateLimitException;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.indexer.IndexerJob;
import com.frc.codex.model.NewFilingRequest;
import com.frc.codex.model.RegistryCode;
import com.frc.codex.model.StreamEvent;
import com.frc.codex.properties.FilingIndexProperties;

public class CompaniesHouseStreamIndexerImpl implements IndexerJob {
	private static final Logger LOG = LoggerFactory.getLogger(CompaniesHouseStreamIndexerImpl.class);
	private final CompaniesHouseClient companiesHouseClient;
	private final long companiesHouseStreamIndexerBatchSize;
	private final DatabaseManager databaseManager;
	private int companiesHouseSessionFilingCount;
	private Long companiesHouseSessionLatestTimepoint;
	private Long companiesHouseSessionStartTimepoint;

	public CompaniesHouseStreamIndexerImpl(
			CompaniesHouseClient companiesHouseClient,
			DatabaseManager databaseManager,
			FilingIndexProperties properties
	) {
		this.companiesHouseClient = companiesHouseClient;
		this.companiesHouseStreamIndexerBatchSize = properties.companiesHouseStreamIndexerBatchSize();
		this.databaseManager = databaseManager;
	}

	public String getStatus() {
		return String.format("""
						Companies House Stream Indexer:
						\tFilings discovered this session: %s
						\tEarliest timepoint this session: %s
						\tLatest timepoint this session: %s""",
				companiesHouseSessionFilingCount,
				companiesHouseSessionStartTimepoint,
				companiesHouseSessionLatestTimepoint
		);
	}

	/*
	 * Processes a filing event from the Companies House streaming API.
	 * Returns the timepoint of the event.
	 */
	private long handleFilingStreamEvent(CompaniesHouseFiling companiesHouseFiling) throws JsonProcessingException {
		LOG.debug("CH filing stream event. Handling {}.", companiesHouseFiling.transactionId());
		long timepoint = companiesHouseFiling.timepoint();
		// Streaming event is a filing (should always be the case, but might as well check)
		if (!"filing-history".equals(companiesHouseFiling.resourceKind())) {
			LOG.debug("CH filing stream event: Skipped {}, not a filing.", companiesHouseFiling.transactionId());
			return timepoint;
		}
		// Event does not indicate the filing is deleted
		if ("deleted".equals(companiesHouseFiling.eventType())) {
			LOG.debug("CH filing stream event: Skipped {}, deleted.", companiesHouseFiling.transactionId());
			return timepoint;
		}
		// Category is not explicitly excluded
		if (!companiesHouseClient.filterCategory(companiesHouseFiling.category())) {
			LOG.debug(
					"CH filing stream event: Skipped {}, category \"{}\" excluded.",
					companiesHouseFiling.transactionId(),
					companiesHouseFiling.category()
			);
			return timepoint;
		}
		// Filing has already been indexed
		if (databaseManager.filingExists(RegistryCode.COMPANIES_HOUSE.getCode(), companiesHouseFiling.transactionId())) {
			LOG.debug("CH filing stream event: Skipped {}, filing already exists.", companiesHouseFiling.transactionId());
			return timepoint;
		}
		// Check if an IXBRL document is associated with the filing
		Set<String> filingUrls = companiesHouseClient.getCompanyFilingUrls(
				companiesHouseFiling.companyNumber(),
				companiesHouseFiling.resourceId()
		);
		if (filingUrls.isEmpty()) {
			LOG.debug("CH filing stream event: Skipped {}, no IXBRL documents.", companiesHouseFiling.transactionId());
			return timepoint;
		}

		// Retrieve document date
		LocalDateTime documentDate = companiesHouseFiling.actionDate();
		if (documentDate == null) {
			CompaniesHouseFiling fullFiling = companiesHouseClient.getFiling(
					companiesHouseFiling.companyNumber(),
					companiesHouseFiling.transactionId()
			);
			documentDate = fullFiling.actionDate();
		}

		// Retrieve company name
		CompaniesHouseCompany company = companiesHouseClient.getCompany(companiesHouseFiling.companyNumber());
		String companyName = company.getCompanyName();

		NewFilingRequest newFilingRequest = NewFilingRequest.builder()
				.companyName(companyName)
				.companyNumber(companiesHouseFiling.companyNumber())
				.documentDate(documentDate)
				.downloadUrl(companiesHouseFiling.downloadUrl())
				.externalFilingId(companiesHouseFiling.transactionId())
				.externalViewUrl(companiesHouseFiling.downloadUrl())
				.filingDate(companiesHouseFiling.date())
				.registryCode(RegistryCode.COMPANIES_HOUSE.getCode())
				.streamTimepoint(companiesHouseFiling.timepoint())
				.build();
		UUID filingId = this.databaseManager.createFiling(newFilingRequest);
		LOG.info("Created CH filing for {}: {}", newFilingRequest.getDownloadUrl(), filingId);
		this.companiesHouseSessionFilingCount += 1;
		return timepoint;
	}

	public boolean isHealthy() {
		return companiesHouseSessionStartTimepoint != null;
	}

	public void run(Supplier<Boolean> continueCallback) {
		if (!continueCallback.get()) {
			return;
		}
		LOG.info("Starting Companies House stream indexing at {}", System.currentTimeMillis() / 1000);
		List<StreamEvent> streamEvents = this.databaseManager.getStreamEvents(companiesHouseStreamIndexerBatchSize);
		for (StreamEvent streamEvent : streamEvents) {

			CompaniesHouseFiling companiesHouseFiling;
			try {
				companiesHouseFiling = companiesHouseClient.parseStreamedFiling(streamEvent.getJson());
			} catch (JsonProcessingException e) {
				LOG.error("Failed to parse CH filing stream event.", e);
				break;
			}

			long timepoint;
			try {
				timepoint = handleFilingStreamEvent(companiesHouseFiling);
				databaseManager.deleteStreamEvent(streamEvent.getStreamEventId());
			} catch (RateLimitException e) {
				LOG.warn("Rate limit exceeded while streaming CH filings. Resuming later.", e);
				break;
			} catch (Exception e) {
				LOG.error("Failed to handle CH filing stream event.", e);
				break;
			}

			if (companiesHouseSessionStartTimepoint == null) {
				companiesHouseSessionStartTimepoint = timepoint;
			}
			companiesHouseSessionLatestTimepoint = timepoint;
		}
	}
}
