package com.frc.codex.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.FilingIndexProperties;

@Component
@Profile("test")
public class TestFilingIndexPropertiesImpl implements FilingIndexProperties {

	public String companiesHouseDocumentApiBaseUrl() {
		return "http://localhost:8085";
	}

	public String companiesHouseInformationApiBaseUrl() {
		return "http://localhost:8085";
	}

	public String companiesHouseRestApiKey() {
		return "XXX";
	}

	public String companiesHouseStreamApiBaseUrl() {
		return "http://localhost:8085";
	}

	public String companiesHouseStreamApiKey() {
		return "XXX";
	}

	public String fcaDataApiBaseUrl() {
		return "http://localhost:8086/data";
	}

	public String fcaSearchApiUrl() {
		return "http://localhost:8086/search";
	}
}
