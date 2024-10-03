package com.frc.codex.discovery.companieshouse.impl;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.discovery.companieshouse.CompaniesHouseClient;
import com.frc.codex.model.NewFilingRequest;

@Component
@Profile("test")
public class TestCompaniesHouseClientImpl implements CompaniesHouseClient {

	public String getCompany(String companyNumber) {
		return null;
	}

	public List<NewFilingRequest> getCompanyFilings(String companyNumber) {
		return null;
	}

	public Set<String> getCompanyFilingUrls(String companyNumber, String filingId) {
		return null;
	}

	public void streamFilings(Long timepoint, Function<String, Boolean> callback) {

	}
}
