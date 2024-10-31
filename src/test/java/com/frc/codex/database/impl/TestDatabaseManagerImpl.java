package com.frc.codex.database.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.RegistryCode;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.model.Company;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingResultRequest;
import com.frc.codex.model.FilingStatus;
import com.frc.codex.model.NewFilingRequest;
import com.frc.codex.model.SearchFilingsRequest;
import com.frc.codex.model.companieshouse.CompaniesHouseArchive;
import com.google.common.collect.ImmutableList;

@Component
@Profile("test")
public class TestDatabaseManagerImpl implements DatabaseManager {
	private final Map<String, CompaniesHouseArchive> companiesHouseArchives;
	private final Map<UUID, Filing> filings;

	public TestDatabaseManagerImpl() {
		this.companiesHouseArchives = new HashMap<>();
		this.filings = new HashMap<>();
	}

	public void applyFilingResult(FilingResultRequest filingResultRequest) {
		Filing newFiling = copyFiling(filingResultRequest.getFilingId())
				.status(filingResultRequest.getStatus().toString())
				.error(filingResultRequest.getError())
				.logs(filingResultRequest.getLogs())
				.filename(filingResultRequest.getFilename())
				.oimDirectory(filingResultRequest.getOimDirectory())
				.stubViewerUrl(filingResultRequest.getStubViewerUrl())
				.build();
		updateFiling(newFiling);
	}

	public boolean checkCompaniesLimit(int companiesLimit) {
		return false;
	}

	public boolean checkRegistryLimit(RegistryCode registryCode, int limit) {
		return false;
	}

	public boolean companiesHouseArchiveExists(String filename) {
		return companiesHouseArchives.containsKey(filename);
	}

	public String createCompaniesHouseArchive(CompaniesHouseArchive archive) {
		CompaniesHouseArchive archiveCopy = CompaniesHouseArchive.builder()
				.filename(archive.getFilename())
				.uri(archive.getUri())
				.archiveType(archive.getArchiveType())
				.completedDate(Timestamp.from(Instant.now()))
				.build();
		companiesHouseArchives.put(archiveCopy.getFilename(), archiveCopy);
		return archiveCopy.getFilename();
	}

	public UUID createFiling(NewFilingRequest newFilingRequest) {
		Filing filing = Filing.builder()
				.filingId(UUID.randomUUID().toString())
				.companyName(newFilingRequest.getCompanyName())
				.companyNumber(newFilingRequest.getCompanyNumber())
				.discoveredDate(Timestamp.from(Instant.now()))
				.status(FilingStatus.PENDING.toString())
				.registryCode(newFilingRequest.getRegistryCode())
				.downloadUrl(newFilingRequest.getDownloadUrl())
				.streamTimepoint(newFilingRequest.getStreamTimepoint())
				.build();
		filings.put(filing.getFilingId(), filing);
		return filing.getFilingId();
	}

	public boolean filingExists(String registryCode, String externalFilingId) {
		return filings.values().stream()
				.anyMatch(f ->
						f.getRegistryCode().equals(registryCode) &&
						f.getExternalFilingId().equals(externalFilingId)
				);
	}

	public Filing getFiling(UUID filingId) {
		return filings.get(filingId);
	}

	public LocalDateTime getLatestFcaFilingDate(LocalDateTime defaultDate) {
		return filings.values().stream()
				.filter(f -> f.getRegistryCode().equals("FCA"))
				.map(Filing::getFilingDate)
				.max(LocalDateTime::compareTo)
				.orElse(defaultDate);
	}

	public Long getLatestStreamTimepoint(Long defaultTimepoint) {
		return filings.values().stream()
				.mapToLong(Filing::getStreamTimepoint)
				.max()
				.orElse(defaultTimepoint);
	}

	public List<Filing> getFilingsByStatus(FilingStatus status) {
		return filings.values().stream()
				.filter(f -> f.getStatus().equals(status.toString()))
				.collect(ImmutableList.toImmutableList());
	}

	public List<Filing> getFilingsByStatus(FilingStatus status, RegistryCode registryCode) {
		return filings.values().stream()
				.filter(f -> f.getStatus().equals(status.toString()))
				.filter(f -> f.getRegistryCode().equals(registryCode.getCode()))
				.collect(ImmutableList.toImmutableList());
	}

	public long getRegistryCount(RegistryCode registryCode) {
		return filings.values().stream()
				.filter(f -> f.getRegistryCode().equals(registryCode.getCode()))
				.count();
	}

	public void resetFiling(UUID filingId) {
		Filing newFiling = copyFiling(filingId)
				.status(FilingStatus.PENDING.toString())
				.error(null)
				.logs(null)
				.filename(null)
				.stubViewerUrl(null)
				.oimDirectory(null)
				.build();
		updateFiling(newFiling);
	}

	public List<Filing> searchFilings(SearchFilingsRequest searchFilingsRequest) {
		Stream<Filing> results = filings.values().stream();
		if (searchFilingsRequest.getCompanyName() != null) {
			List<String> terms = List.of(searchFilingsRequest.getCompanyName().split(" "));
			for(String term : terms) {
				results = results.filter(f -> f.getCompanyName().contains(term));
			}
		}
		if (searchFilingsRequest.getCompanyNumber() != null) {
			results = results.filter(f -> f.getCompanyNumber().equals(searchFilingsRequest.getCompanyNumber()));
		}
		if (searchFilingsRequest.getStatus() != null) {
			results = results.filter(f -> f.getStatus().equals(searchFilingsRequest.getStatus()));
		}
		return results.collect(ImmutableList.toImmutableList());
	}

	private Filing.Builder copyFiling(UUID filingId) {
		Filing filing = getFiling(filingId);
		return Filing.builder()
				.filingId(filing.getFilingId().toString())
				.companyName(filing.getCompanyName())
				.companyNumber(filing.getCompanyNumber())
				.discoveredDate(filing.getDiscoveredDate())
				.status(filing.getStatus())
				.registryCode(filing.getRegistryCode())
				.downloadUrl(filing.getDownloadUrl())
				.streamTimepoint(filing.getStreamTimepoint())
				.error(filing.getError())
				.logs(filing.getLogs());
	}

	private void updateFiling(Filing filing) {
		filings.put(filing.getFilingId(), filing);
	}

	public void updateFilingStatus(UUID filingId, String status) {
		Filing newFiling = copyFiling(filingId)
				.status(status)
				.build();
		updateFiling(newFiling);
	}

	public boolean companyExists(Company company) {
		return false;
	}

	public void createCompany(Company company) {

	}

	public void updateCompany(Company company) {

	}

	public List<Company> getIncompleteCompanies(int limit) {
		return null;
	}
}
