package com.frc.codex.database;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.frc.codex.model.RegistryCode;
import com.frc.codex.model.Company;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingResultRequest;
import com.frc.codex.model.FilingStatus;
import com.frc.codex.model.NewFilingRequest;
import com.frc.codex.model.SearchFilingsRequest;
import com.frc.codex.model.StreamEvent;
import com.frc.codex.model.companieshouse.CompaniesHouseArchive;

public interface DatabaseManager {
	void applyFilingResult(FilingResultRequest filingResultRequest);
	boolean checkCompaniesLimit(int companiesLimit);
	boolean checkRegistryLimit(RegistryCode registryCode, int limit);
	boolean companiesHouseArchiveExists(String filename);
	boolean companyNumberExists(String companyNumber);
	String createCompaniesHouseArchive(CompaniesHouseArchive archive);
	UUID createFiling(NewFilingRequest newFilingRequest);
	UUID createStreamEvent(long timepoint, String json);
	void deleteStreamEvent(UUID streamEventId);
	boolean filingExists(String registryCode, String externalFilingId);
	Filing getFiling(UUID filingId);
	Filing getFiling(String companyNumber, LocalDate documentDate);
	UUID getFilingId(String registryCode, String externalFilingId);
	List<Filing> getFilingsByStatus(FilingStatus status);
	List<Filing> getFilingsByStatus(FilingStatus status, RegistryCode registryCode);
	long getFilingsCount(SearchFilingsRequest searchFilingsRequest);
	LocalDateTime getLatestFcaFilingDate(LocalDateTime defaultDate);
	LocalDateTime getLatestStreamDiscoveredDate();
	Long getLatestStreamTimepoint(Long defaultTimepoint);
	long getRegistryCount(RegistryCode registryCode);
	void resetCompany(String companyNumber);
	List<StreamEvent> getStreamEvents(long limit);
	long getStreamEventsCount();
	void resetFiling(UUID filingId);
	List<Filing> searchFilings(SearchFilingsRequest searchFilingsRequest);
	void updateFilingStatus(UUID filingId, String status);
	Set<String> getCompaniesCompanyNumbers();
	void createCompany(Company company);
	void updateCompany(Company company);
	List<Company> getIncompleteCompanies(int limit);
	boolean isHealthy();
}
