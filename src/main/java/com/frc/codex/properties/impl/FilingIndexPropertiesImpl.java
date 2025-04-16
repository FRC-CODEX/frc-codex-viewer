package com.frc.codex.properties.impl;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.properties.FilingIndexProperties;
import com.zaxxer.hikari.HikariConfig;

@Component
@Profile("application")
public class FilingIndexPropertiesImpl implements FilingIndexProperties {
	private static final String ADMIN_COOKIE_NAME = "ADMIN_COOKIE_NAME";
	private static final String ADMIN_ENABLED = "ADMIN_ENABLED";
	private static final String ADMIN_KEY = "ADMIN_KEY";
	private static final String AWS_LAMBDA_FUNCTION_NAME = "AWS_LAMBDA_FUNCTION_NAME";
	private static final String AWS_LAMBDA_TIMEOUT_SECONDS = "AWS_LAMBDA_TIMEOUT_SECONDS";
	private static final String COMPANIES_HOUSE_DOCUMENT_API_BASE_URL = "COMPANIES_HOUSE_DOCUMENT_API_BASE_URL";
	private static final String COMPANIES_HOUSE_INFORMATION_API_BASE_URL = "COMPANIES_HOUSE_INFORMATION_API_BASE_URL";
	private static final String COMPANIES_HOUSE_RAPID_RATE_LIMIT = "COMPANIES_HOUSE_RAPID_RATE_LIMIT";
	private static final String COMPANIES_HOUSE_RAPID_RATE_WINDOW = "COMPANIES_HOUSE_RAPID_RATE_WINDOW";
	private static final String COMPANIES_HOUSE_REST_API_KEY = "COMPANIES_HOUSE_REST_API_KEY";
	private static final String COMPANIES_HOUSE_STREAM_API_BASE_URL = "COMPANIES_HOUSE_STREAM_API_BASE_URL";
	private static final String COMPANIES_HOUSE_STREAM_API_KEY = "COMPANIES_HOUSE_STREAM_API_KEY";
	private static final String COMPANIES_HOUSE_STREAM_INDEXER_BATCH_SIZE = "COMPANIES_HOUSE_STREAM_INDEXER_BATCH_SIZE";
	private static final String HTTP_USERNAME = "HTTP_USERNAME";
	private static final String HTTP_PASSWORD = "HTTP_PASSWORD";
	private static final String DB_URL = "DB_URL";
	private static final String DB_USERNAME = "DB_USERNAME";
	private static final String DB_PASSWORD = "DB_PASSWORD";
	private static final String DB_MAX_LIFETIME = "DB_MAX_LIFETIME";
	private static final String DB_SEED_SCRIPT_PATH = "DB_SEED_SCRIPT_PATH";
	private static final String DB_HEALTH_CHECK_TIMEOUT = "DB_HEALTH_CHECK_TIMEOUT";
	private static final String ENABLE_INDEXING_ARCHIVE_ARCHIVES = "ENABLE_INDEXING_ARCHIVE_ARCHIVES";
	private static final String ENABLE_INDEXING_DAILY_ARCHIVES = "ENABLE_INDEXING_DAILY_ARCHIVES";
	private static final String ENABLE_INDEXING_MONTHLY_ARCHIVES = "ENABLE_INDEXING_MONTHLY_ARCHIVES";
	private static final String ENABLE_PREPROCESSING = "ENABLE_PREPROCESSING";
	private static final String FCA_DATA_API_BASE_URL = "FCA_DATA_API_BASE_URL";
	private static final String FCA_PAST_DAYS = "FCA_PAST_DAYS";
	private static final String FCA_SEARCH_API_URL = "FCA_SEARCH_API_URL";
	private static final String FILING_LIMIT_COMPANIES_HOUSE = "FILING_LIMIT_COMPANIES_HOUSE";
	private static final String FILING_LIMIT_FCA = "FILING_LIMIT_FCA";
	private static final String LAMBDA_PREPROCESSING_CONCURRENCY = "LAMBDA_PREPROCESSING_CONCURRENCY";
	private static final String METRIC_NAMESPACE = "METRIC_NAMESPACE";
	private static final String S3_INDEXER_UPLOADS_BUCKET_NAME = "S3_INDEXER_UPLOADS_BUCKET_NAME";
	private static final String S3_RESULTS_BUCKET_NAME = "S3_RESULTS_BUCKET_NAME";
	private static final String SEARCH_MAXIMUM_PAGES = "SEARCH_MAXIMUM_PAGES";
	private static final String SEARCH_PAGE_SIZE = "SEARCH_PAGE_SIZE";
	private static final String SECRETS_FILEPATH = "/run/secrets/frc-codex-server.secrets";
	private static final String SQS_JOBS_QUEUE_NAME = "SQS_JOBS_QUEUE_NAME";
	private static final String SQS_RESULTS_QUEUE_NAME = "SQS_RESULTS_QUEUE_NAME";
	private static final String STREAM_DISCOVERY_DELAY_METRIC = "STREAM_DISCOVERY_DELAY_METRIC";
	private static final String STREAM_EVENTS_METRIC = "STREAM_EVENTS_METRIC";
	private static final String SUPPORT_EMAIL = "SUPPORT_EMAIL";
	private static final String UNPROCESSED_COMPANIES_LIMIT = "UNPROCESSED_COMPANIES_LIMIT";
	private final String adminCookieName;
	private final boolean adminEnabled;
	private final String adminKey;
	private final String companiesHouseDocumentApiBaseUrl;
	private final String companiesHouseInformationApiBaseUrl;
	private final int companiesHouseRapidRateLimit;
	private final int companiesHouseRapidRateWindow;
	private final String companiesHouseRestApiKey;
	private final String companiesHouseStreamApiBaseUrl;
	private final String companiesHouseStreamApiKey;
	private final long companiesHouseStreamIndexerBatchSize;
	private final String dbUrl;
	private final String dbUsername;
	private final String dbPassword;
	private final long dbMaxLifetime;
	private final String dbSeedScriptPath;
	private final boolean enableIndexingArchiveArchives;
	private final boolean enableIndexingDailyArchives;
	private final boolean enableIndexingMonthlyArchives;
	private final boolean enablePreprocessing;
	private final String fcaDataApiBaseUrl;
	private final int fcaPastDays;
	private final String fcaSearchApiUrl;
	private final int filingLimitCompaniesHouse;
	private final int filingLimitFca;
	private final boolean isAws;
	private final String awsLambdaFunctionName;
	private final long awsLambdaTimeoutSeconds;
	private final int lambdaPreprocessingConcurrency;
	private final String metricNamespace;
	private final String s3IndexerUploadsBucketName;
	private final String s3ResultsBucketName;
	private final int searchMaximumPages;
	private final int searchPageSize;
	private final String sqsJobsQueueName;
	private final String sqsResultsQueueName;
	private final String streamDiscoveryDelayMetric;
	private final String streamEventsMetric;
	private final String supportEmail;
	private final int unprocessedCompaniesLimit;
	private final String httpUsername;
	private final String httpPassword;
	private final int dbHealthCheckTimeout;


	public FilingIndexPropertiesImpl() {
		adminCookieName = requireNonNull(getEnv(ADMIN_COOKIE_NAME, "frc_codex_admin_key"));
		adminEnabled = Boolean.parseBoolean(requireNonNull(getEnv(ADMIN_ENABLED, "false")));
		adminKey = requireNonNull(getEnv(ADMIN_KEY, ""));

		awsLambdaFunctionName = requireNonNull(getEnv(AWS_LAMBDA_FUNCTION_NAME, "function"));
		awsLambdaTimeoutSeconds = Long.parseLong(requireNonNull(getEnv(AWS_LAMBDA_TIMEOUT_SECONDS, "300")));

		companiesHouseDocumentApiBaseUrl = requireNonNull(getEnv(COMPANIES_HOUSE_DOCUMENT_API_BASE_URL));
		companiesHouseInformationApiBaseUrl = requireNonNull(getEnv(COMPANIES_HOUSE_INFORMATION_API_BASE_URL));
		companiesHouseStreamApiBaseUrl = requireNonNull(getEnv(COMPANIES_HOUSE_STREAM_API_BASE_URL));
		// Default rapid rate limit is 20 requests per 10 seconds (600 requests / 5 minutes)
		companiesHouseRapidRateLimit = Integer.parseInt(requireNonNull(getEnv(COMPANIES_HOUSE_RAPID_RATE_LIMIT, "20")));
		companiesHouseRapidRateWindow = Integer.parseInt(requireNonNull(getEnv(COMPANIES_HOUSE_RAPID_RATE_WINDOW, "10000")));

		companiesHouseStreamIndexerBatchSize = Long.parseLong(requireNonNull(getEnv(COMPANIES_HOUSE_STREAM_INDEXER_BATCH_SIZE, "1000")));

		dbUrl = requireNonNull(getEnv(DB_URL));
		dbUsername = requireNonNull(getEnv(DB_USERNAME));
		dbPassword = requireNonNull(getEnv(DB_PASSWORD));
		dbMaxLifetime = Long.parseLong(requireNonNull(getEnv(DB_MAX_LIFETIME, "300000")));
		dbSeedScriptPath = getEnv(DB_SEED_SCRIPT_PATH);

		enableIndexingArchiveArchives = Boolean.parseBoolean(requireNonNull(getEnv(ENABLE_INDEXING_ARCHIVE_ARCHIVES, "false")));
		enableIndexingDailyArchives = Boolean.parseBoolean(requireNonNull(getEnv(ENABLE_INDEXING_DAILY_ARCHIVES, "false")));
		enableIndexingMonthlyArchives = Boolean.parseBoolean(requireNonNull(getEnv(ENABLE_INDEXING_MONTHLY_ARCHIVES, "false")));

		enablePreprocessing = Boolean.parseBoolean(requireNonNull(getEnv(ENABLE_PREPROCESSING, "false")));

		fcaDataApiBaseUrl = requireNonNull(getEnv(FCA_DATA_API_BASE_URL));
		fcaPastDays = Integer.parseInt(getEnv(FCA_PAST_DAYS, "0"));
		fcaSearchApiUrl = requireNonNull(getEnv(FCA_SEARCH_API_URL));

		// Limits must be explicitly overridden
		filingLimitCompaniesHouse = Integer.parseInt(requireNonNull(getEnv(FILING_LIMIT_COMPANIES_HOUSE, "5")));
		filingLimitFca = Integer.parseInt(requireNonNull(getEnv(FILING_LIMIT_FCA, "5")));

		isAws = Boolean.parseBoolean(requireNonNull(getEnv("AWS", "true")));

		lambdaPreprocessingConcurrency = Integer.parseInt(requireNonNull(getEnv(LAMBDA_PREPROCESSING_CONCURRENCY, "1")));

		metricNamespace = requireNonNull(getEnv(METRIC_NAMESPACE, null));

		searchMaximumPages = Integer.parseInt(requireNonNull(getEnv(SEARCH_MAXIMUM_PAGES, "10")));
		searchPageSize = Integer.parseInt(requireNonNull(getEnv(SEARCH_PAGE_SIZE, "10")));

		s3IndexerUploadsBucketName = requireNonNull(getEnv(S3_INDEXER_UPLOADS_BUCKET_NAME));
		s3ResultsBucketName = requireNonNull(getEnv(S3_RESULTS_BUCKET_NAME));
		sqsJobsQueueName = requireNonNull(getEnv(SQS_JOBS_QUEUE_NAME));
		sqsResultsQueueName = requireNonNull(getEnv(SQS_RESULTS_QUEUE_NAME));

		streamDiscoveryDelayMetric = requireNonNull(getEnv(STREAM_DISCOVERY_DELAY_METRIC, null));
		streamEventsMetric = requireNonNull(getEnv(STREAM_EVENTS_METRIC, null));

		supportEmail = getEnv(SUPPORT_EMAIL, null);

		dbHealthCheckTimeout = Integer.parseInt(requireNonNull(getEnv(DB_HEALTH_CHECK_TIMEOUT, "2")));

		unprocessedCompaniesLimit = Integer.parseInt(requireNonNull(getEnv(UNPROCESSED_COMPANIES_LIMIT, "1000")));

		Properties secrets = getSecrets();
		if (secrets.containsKey(HTTP_USERNAME)) {
			httpUsername = secrets.getProperty(HTTP_USERNAME);
		} else {
			httpUsername = getEnv(HTTP_USERNAME);
		}
		if (secrets.containsKey(HTTP_PASSWORD)) {
			httpPassword = secrets.getProperty(HTTP_PASSWORD);
		} else {
			httpPassword = getEnv(HTTP_PASSWORD);
		}
		if ((httpUsername == null) != (httpPassword == null)) {
			throw new RuntimeException("HTTP_USERNAME and HTTP_PASSWORD must both be set or unset");
		}
		if (secrets.containsKey(COMPANIES_HOUSE_REST_API_KEY)) {
			companiesHouseRestApiKey = secrets.getProperty(COMPANIES_HOUSE_REST_API_KEY);
		} else {
			companiesHouseRestApiKey = getEnv(COMPANIES_HOUSE_REST_API_KEY);
		}
		if (secrets.containsKey(COMPANIES_HOUSE_STREAM_API_KEY)) {
			companiesHouseStreamApiKey = secrets.getProperty(COMPANIES_HOUSE_STREAM_API_KEY);
		} else {
			companiesHouseStreamApiKey = getEnv(COMPANIES_HOUSE_STREAM_API_KEY);
		}
	}

	private String getEnv(String name) {
		return System.getenv(name);
	}

	private String getEnv(String name, String defaultValue) {
		String value = getEnv(name);
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

	private Properties getSecrets() {
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

	public String adminCookieName() {
		return adminCookieName;
	}

	public boolean adminEnabled() {
		return adminEnabled;
	}

	public String adminKey() {
		return adminKey;
	}

	public String awsLambdaFunctionName() {
		return awsLambdaFunctionName;
	}

	public long awsLambdaTimeoutSeconds() {
		return awsLambdaTimeoutSeconds;
	}

	public String companiesHouseDocumentApiBaseUrl() {
		return companiesHouseDocumentApiBaseUrl;
	}

	public String companiesHouseInformationApiBaseUrl() {
		return companiesHouseInformationApiBaseUrl;
	}

	public int companiesHouseRapidRateLimit() {
		return companiesHouseRapidRateLimit;
	}

	public int companiesHouseRapidRateWindow() {
		return companiesHouseRapidRateWindow;
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

	public long companiesHouseStreamIndexerBatchSize() {
		return companiesHouseStreamIndexerBatchSize;
	}

	public String dbSeedScriptPath() {
		return dbSeedScriptPath;
	}

	public boolean enableIndexingArchiveArchives() {
		return enableIndexingArchiveArchives;
	}

	public boolean enableIndexingDailyArchives() {
		return enableIndexingDailyArchives;
	}

	public boolean enableIndexingMonthlyArchives() {
		return enableIndexingMonthlyArchives;
	}

	public boolean enablePreprocessing() {
		return enablePreprocessing;
	}

	public String fcaDataApiBaseUrl() {
		return fcaDataApiBaseUrl;
	}

	public int fcaPastDays() {
		return fcaPastDays;
	}

	public String fcaSearchApiUrl() {
		return fcaSearchApiUrl;
	}

	public int filingLimitCompaniesHouse() {
		return filingLimitCompaniesHouse;
	}

	public int filingLimitFca() {
		return filingLimitFca;
	}

	public HikariConfig getDatabaseConfig(String poolName) {
		HikariConfig config = new HikariConfig();
		config.setInitializationFailTimeout(0);
		// recommended is true but is turned off for rollback ability on error
		config.setAutoCommit(false);
		config.setJdbcUrl(dbUrl);
		config.setUsername(dbUsername);
		config.setPassword(dbPassword);
		config.setMaxLifetime(dbMaxLifetime);
		config.setPoolName(poolName);
		return config;
	}

	public String httpUsername() {
		return httpUsername;
	}

	public String httpPassword() {
		return httpPassword;
	}

	public boolean isAws() {
		return isAws;
	}

	public boolean isDbMigrateAsync() {
		return false;
	}

	public int lambdaPreprocessingConcurrency() {
		return lambdaPreprocessingConcurrency;
	}

	public String metricNamespace() {
		return metricNamespace;
	}

	public String s3IndexerUploadsBucketName() {
		return s3IndexerUploadsBucketName;
	}

	public String s3ResultsBucketName() {
		return s3ResultsBucketName;
	}

	public int searchMaximumPages() {
		return searchMaximumPages;
	}

	public int searchPageSize() {
		return searchPageSize;
	}

	public String sqsJobsQueueName() {
		return sqsJobsQueueName;
	}

	public String sqsResultsQueueName() {
		return sqsResultsQueueName;
	}

	public String streamDiscoveryDelayMetric() {
		return streamDiscoveryDelayMetric;
	}

	public String streamEventsMetric() {
		return streamEventsMetric;
	}

	public String supportEmail() {
		return supportEmail;
	}

	public int unprocessedCompaniesLimit() {
		return unprocessedCompaniesLimit;
	}

	public int dbHealthCheckTimeout() {
		return dbHealthCheckTimeout;
	}
}
