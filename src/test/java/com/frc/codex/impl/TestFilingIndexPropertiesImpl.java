package com.frc.codex.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.properties.FilingIndexProperties;
import com.zaxxer.hikari.HikariConfig;

@Component
@Profile("test")
public class TestFilingIndexPropertiesImpl implements FilingIndexProperties {

	public String adminCookieName() {
		return "frc_codex_admin_key";
	}

	public boolean adminEnabled() {
		return true;
	}

	public String adminKey() {
		return "XYZ";
	}

	public String awsLambdaFunctionName() {
		return "function";
	}

	public long awsLambdaTimeoutSeconds() {
		return 300;
	}

	public String companiesHouseDocumentApiBaseUrl() {
		return "http://localhost:8085";
	}

	public String companiesHouseInformationApiBaseUrl() {
		return "http://localhost:8085";
	}

	public int companiesHouseRapidRateLimit() {
		return 20;
	}

	public int companiesHouseRapidRateWindow() {
		return 10;
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

	public long companiesHouseStreamIndexerBatchSize() {
		return 100;
	}

	public String dbSeedScriptPath() {
		return null;
	}

	public boolean enablePreprocessing() {
		return false;
	}

	public String fcaDataApiBaseUrl() {
		return "http://localhost:8086/data";
	}

	public int fcaPastDays() {
		return 30;
	}

	public String fcaSearchApiUrl() {
		return "http://localhost:8086/search";
	}

	public int filingLimitCompaniesHouse() {
		return 5;
	}

	public int filingLimitFca() {
		return 5;
	}

	public HikariConfig getDatabaseConfig(String poolName) {
		HikariConfig config = new HikariConfig();
		config.setInitializationFailTimeout(0);
		config.setAutoCommit(false);
		config.setJdbcUrl("http://localhost:5432/frc_codex");
		config.setUsername("frc_codex");
		config.setPassword("frc_codex");
		config.setMaxLifetime(300*1000);
		config.setPoolName(poolName);
		return config;
	}

	public String httpUsername() {
		return null;
	}

	public String httpPassword() {
		return null;
	}

	public boolean isAws() {
		return false;
	}

	public boolean isDbMigrateAsync() {
		return false;
	}

	public int lambdaPreprocessingConcurrency() {
		return 1;
	}

	public String s3ResultsBucketName() {
		return "frc-codex-results";
	}

	public String s3IndexerUploadsBucketName() {
		return "frc-codex-indexer-uploads";
	}

	public int searchMaximumPages() {
		return 10;
	}

	public int searchPageSize() {
		return 10;
	}

	public String sqsJobsQueueName() {
		return "frc_codex_jobs";
	}

	public String sqsResultsQueueName() {
		return "frc_codex_results";
	}

	public String supportEmail() {
		return null;
	}

	public int unprocessedCompaniesLimit() {
		return 1000;
	}

	public int dbHealthCheckTimeout() {
		return 2;
	}
}
