package com.frc.codex.clients.companieshouse;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import tools.jackson.core.JacksonException;
import com.frc.codex.model.NewFilingRequest;

public interface CompaniesHouseClient {
	boolean excludeCategory(String category);
	CompaniesHouseCompany getCompany(String companyNumber) throws JacksonException;
	CompaniesHouseFiling getFiling(String companyNumber, String transactionId) throws JacksonException;
	List<NewFilingRequest> getCompanyFilings(String companyNumber, String companyName) throws JacksonException;
	FilingUrl getCompanyFilingUrl(String companyNumber, String filingId) throws JacksonException;
	boolean isEnabled();
	CompaniesHouseFiling parseStreamedFiling(String json) throws JacksonException;
	void streamFilings(Long timepoint, Function<String, Boolean> callback) throws IOException, InterruptedException;
}
