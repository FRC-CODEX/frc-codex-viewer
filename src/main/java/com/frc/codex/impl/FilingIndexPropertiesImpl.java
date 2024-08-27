package com.frc.codex.impl;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.FilingIndexProperties;

@Component
@Profile("application")
public class FilingIndexPropertiesImpl implements FilingIndexProperties {
	private static final String COMPANIES_HOUSE_DOCUMENT_API_BASE_URL = "COMPANIES_HOUSE_DOCUMENT_API_BASE_URL";
	private static final String COMPANIES_HOUSE_INFORMATION_API_BASE_URL = "COMPANIES_HOUSE_INFORMATION_API_BASE_URL";
	private static final String COMPANIES_HOUSE_REST_API_KEY = "COMPANIES_HOUSE_REST_API_KEY";
	private static final String COMPANIES_HOUSE_STREAM_API_BASE_URL = "COMPANIES_HOUSE_STREAM_API_BASE_URL";
	private static final String COMPANIES_HOUSE_STREAM_API_KEY = "COMPANIES_HOUSE_STREAM_API_KEY";
	private static final String FCA_DATA_API_BASE_URL = "FCA_DATA_API_BASE_URL";
	private static final String FCA_SEARCH_API_URL = "FCA_SEARCH_API_URL";
	private static final String SECRETS_FILEPATH = "/run/secrets/frc-codex-server.secrets";
	private final String companiesHouseDocumentApiBaseUrl;
	private final String companiesHouseInformationApiBaseUrl;
	private final String companiesHouseRestApiKey;
	private final String companiesHouseStreamApiBaseUrl;
	private final String companiesHouseStreamApiKey;
	private final String fcaDataApiBaseUrl;
	private final String fcaSearchApiUrl;

	public FilingIndexPropertiesImpl() {
		companiesHouseDocumentApiBaseUrl = System.getenv(COMPANIES_HOUSE_DOCUMENT_API_BASE_URL);
		companiesHouseInformationApiBaseUrl = System.getenv(COMPANIES_HOUSE_INFORMATION_API_BASE_URL);
		companiesHouseStreamApiBaseUrl = System.getenv(COMPANIES_HOUSE_STREAM_API_BASE_URL);

		Properties secrets = getSecrets();
		if (secrets.containsKey(COMPANIES_HOUSE_REST_API_KEY)) {
			companiesHouseRestApiKey = secrets.getProperty(COMPANIES_HOUSE_REST_API_KEY);
		} else {
			companiesHouseRestApiKey = System.getenv(COMPANIES_HOUSE_REST_API_KEY);
		}
		if (secrets.containsKey(COMPANIES_HOUSE_STREAM_API_KEY)) {
			companiesHouseStreamApiKey = secrets.getProperty(COMPANIES_HOUSE_STREAM_API_KEY);
		} else {
			companiesHouseStreamApiKey = System.getenv(COMPANIES_HOUSE_STREAM_API_KEY);
		}

		fcaDataApiBaseUrl = System.getenv(FCA_DATA_API_BASE_URL);
		fcaSearchApiUrl = System.getenv(FCA_SEARCH_API_URL);
	}

	private static Properties getSecrets() {
		try {
			Properties properties = new Properties();
			File file = new File(SECRETS_FILEPATH);
			if (!file.exists() || !file.isFile()) {
				return properties;
			}
			FileInputStream fileInputStream = new FileInputStream(file);
			properties.load(fileInputStream);
			fileInputStream.close();
			return properties;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String companiesHouseDocumentApiBaseUrl() {
		return companiesHouseDocumentApiBaseUrl;
	}

	public String companiesHouseInformationApiBaseUrl() {
		return companiesHouseInformationApiBaseUrl;
	}

	public String companiesHouseRestApiKey() {
		return companiesHouseRestApiKey;
	}

	public String companiesHouseStreamApiBaseUrl() {
		return companiesHouseStreamApiBaseUrl;
	}

	public String companiesHouseStreamApiKey() {
		return companiesHouseStreamApiKey;
	}

	public String fcaDataApiBaseUrl() {
		return fcaDataApiBaseUrl;
	}

	public String fcaSearchApiUrl() {
		return fcaSearchApiUrl;
	}
}
