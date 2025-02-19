package com.frc.codex.clients.companieshouse.impl;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.frc.codex.clients.companieshouse.CompaniesHouseCompaniesIndexer;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseCompany;
import com.frc.codex.clients.companieshouse.RateLimitException;
import com.frc.codex.model.Company;
import com.frc.codex.model.NewFilingRequest;

@Component
public class CompaniesHouseCompaniesIndexerImpl implements CompaniesHouseCompaniesIndexer {
	private static final int COMPANIES_BATCH_SIZE = 100;
	private static final Logger LOG = LoggerFactory.getLogger(CompaniesHouseCompaniesIndexerImpl.class);
	private final CompaniesHouseClient companiesHouseClient;
	private final DatabaseManager databaseManager;
	private int companiesHouseSessionCompanyCount;
	private int companiesHouseSessionFilingCount;

	public CompaniesHouseCompaniesIndexerImpl(
			CompaniesHouseClient companiesHouseClient,
			DatabaseManager databaseManager
	) {
		this.companiesHouseClient = companiesHouseClient;
		this.databaseManager = databaseManager;
	}

	public String getStatus() {
		return String.format("""
						Companies House Companies Indexer:
						\tCompanies completed this session: %s
						\tFilings discovered this session: %s""",
				companiesHouseSessionCompanyCount,
				companiesHouseSessionFilingCount
		);
	}

	public boolean isHealthy() {
		return true;
	}

	public void run(Supplier<Boolean> continueCallback) throws IOException {
		if (!continueCallback.get()) {
			return;
		}
		LOG.info("Indexing filings from companies index.");
		List<Company> companies = databaseManager.getIncompleteCompanies(COMPANIES_BATCH_SIZE);
		LOG.info("Loaded {} incomplete companies from companies index.", companies.size());
		for (Company company : companies) {
			if (!continueCallback.get()) {
				break;
			}
			String companyNumber = company.getCompanyNumber();
			String companyName = company.getCompanyName();
			if (companyName == null) {
				// If we don't already have the company's name, pull it from the API.
				CompaniesHouseCompany companiesHouseCompany = companiesHouseClient.getCompany(companyNumber);
				companyName = companiesHouseCompany.getCompanyName();
				Company updateCompany = Company.builder()
						.companyNumber(companyNumber)
						.companyName(companyName)
						.build();
				databaseManager.updateCompany(updateCompany);
			}
			LOG.info("Retrieving filings for company {}.", companyNumber);
			List<NewFilingRequest> filings;
			try {
				filings = companiesHouseClient.getCompanyFilings(companyNumber, companyName);
			} catch (RateLimitException e) {
				LOG.warn("Rate limit exceeded while retrieving CH filing history. Resuming later.", e);
				return;
			}
			LOG.info("Retrieved {} filings for company {}.", filings.size(), companyNumber);
			for (NewFilingRequest filing : filings) {
				if (!continueCallback.get()) {
					break;
				}
				if (databaseManager.filingExists(filing.getRegistryCode(), filing.getExternalFilingId())) {
					LOG.info("Skipping existing filing: {}", filing.getDownloadUrl());
					continue;
				}
				UUID filingId = databaseManager.createFiling(filing);
				LOG.info("Created CH filing for {}: {}", filing.getDownloadUrl(), filingId);
				companiesHouseSessionFilingCount += 1;

			}
			Company updatedCompany = Company.builder()
					.companyNumber(companyNumber)
					.completedDate(new Timestamp(System.currentTimeMillis()))
					.build();
			databaseManager.updateCompany(updatedCompany);
			LOG.info("Completed company: {}", companyNumber);
			companiesHouseSessionCompanyCount += 1;
		}
	}
}
