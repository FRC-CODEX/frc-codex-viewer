package com.frc.codex;

import org.springframework.boot.context.properties.ConfigurationProperties;

public interface FilingIndexProperties {
	String companiesHouseDocumentApiBaseUrl();
	String companiesHouseInformationApiBaseUrl();
	String companiesHouseRestApiKey();
	String companiesHouseStreamApiBaseUrl();
	String companiesHouseStreamApiKey();
	String fcaDataApiBaseUrl();
	String fcaSearchApiUrl();
}

