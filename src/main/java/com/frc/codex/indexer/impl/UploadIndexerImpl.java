package com.frc.codex.indexer.impl;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.frc.codex.database.DatabaseManager;
import com.frc.codex.indexer.UploadIndexer;
import com.frc.codex.model.NewFilingRequest;
import com.frc.codex.model.RegistryCode;
import com.frc.codex.properties.FilingIndexProperties;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

@Component
public class UploadIndexerImpl implements UploadIndexer {
	private static final Logger LOG = LoggerFactory.getLogger(UploadIndexerImpl.class);
	private final DatabaseManager databaseManager;
	private final FilingIndexProperties properties;
	private final S3Client s3Client;
	private int sessionFilingCount;
	private int sessionUploadCount;
	private Integer remainingFileCount;

	public UploadIndexerImpl(
			DatabaseManager databaseManager,
			FilingIndexProperties properties,
			S3Client s3Client
	) {
		this.databaseManager = requireNonNull(databaseManager);
		this.properties = requireNonNull(properties);
		this.s3Client = requireNonNull(s3Client);
	}

	private String assertValidUrl(String urlString) {
		try {
			new URI(urlString);
			return urlString;
		} catch (URISyntaxException e) {
			throw new RuntimeException("Invalid URL found in CSV: " + urlString, e);
		}
	}

	private void deleteUpload(String uploadKey) {
		DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
				.bucket(properties.s3IndexerUploadsBucketName())
				.key(uploadKey)
				.build();
		s3Client.deleteObject(deleteObjectRequest);
		LOG.info("Deleted uploaded index file: {}", uploadKey);
	}

	private BufferedReader getReader(String uploadKey) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(properties.s3IndexerUploadsBucketName())
				.key(uploadKey)
				.build();
		ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);
		return new BufferedReader(new InputStreamReader(responseInputStream));
	}

	public String getStatus() {
		return String.format("""
						Upload Indexer: %s
						\tFilings indexed this session: %s
						\tUploads completed this session: %s
						\tFiles remaining at last check: %s""",
				isHealthy() ? "Healthy" : "Unhealthy",
				sessionFilingCount,
				sessionUploadCount,
				remainingFileCount
		);
	}

	private List<String> getUploadKeys() {
		ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
				.bucket(properties.s3IndexerUploadsBucketName())
				.build();
		ListObjectsResponse response = s3Client.listObjects(listObjectsRequest);
		return response.contents().stream()
				.map(S3Object::key)
				.filter(key -> key.toLowerCase().endsWith(".csv"))
				.toList();
	}

	public boolean isHealthy() {
		try {
			HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
					.bucket(properties.s3IndexerUploadsBucketName())
					.build();
			s3Client.headBucket(headBucketRequest);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private boolean indexCsvRow(String[] row) {
		int column = 0;
		RegistryCode registryCode;
		if (row.length == 8) {
			// registry_code,download_url,company_name,company_number,external_filing_id,external_view_url,filing_date,document_date
			registryCode = RegistryCode.valueOf(row[column++]);
		} else if (row.length == 7) {
			// download_url,company_name,company_number,external_filing_id,external_view_url,filing_date,document_date
			registryCode = RegistryCode.COMPANIES_HOUSE;
		} else {
			throw new RuntimeException("Invalid CSV row: " + Arrays.stream(row).toList());
		}
		String downloadUrl = assertValidUrl(row[column++]);
		String companyName = row[column++];
		if (companyName.isBlank()) {
			throw new RuntimeException("Company name is missing.");
		}
		String companyNumber = row[column++];
		if (companyNumber.isBlank() || !companyNumber.matches("[a-zA-Z0-9]+")) {
			throw new RuntimeException("Invalid company number: " + companyNumber);
		}
		String externalFilingId = row[column++];
		if (externalFilingId.isBlank() || !externalFilingId.matches("[a-zA-Z0-9]+")) {
			throw new RuntimeException("Invalid external filing ID: " + externalFilingId);
		}
		String externalViewUrl = assertValidUrl(row[column++]);
		LocalDateTime filingDate = parseDate(row[column++]);
		LocalDateTime documentDate = parseDate(row[column++]);

		if (this.databaseManager.filingExists(registryCode.getCode(), externalFilingId)) {
			LOG.debug("Skipping existing filing: {}", externalFilingId);
			return false;
		}
		UUID filingId = this.databaseManager.createFiling(NewFilingRequest.builder()
				.companyName(companyName)
				.companyNumber(companyNumber)
				.documentDate(documentDate)
				.downloadUrl(downloadUrl)
				.externalFilingId(externalFilingId)
				.externalViewUrl(externalViewUrl)
				.filingDate(filingDate)
				.registryCode(registryCode.getCode())
				.build()
		);
		LOG.debug("Indexed filing filing from CSV: ({}, {}) {}", registryCode, externalFilingId, filingId);
		return true;
	}

	private LocalDateTime parseDate(String date) {
		if (date.length() > 10) date = date.substring(0, 10);
		TemporalAccessor parsedDate = ISO_LOCAL_DATE.parse(date);
		return LocalDate.from(parsedDate).atStartOfDay();
	}

	public void run(Supplier<Boolean> continueCallback) throws IOException {
		if (!continueCallback.get()) {
			return;
		}
		List<String> uploadKeys = getUploadKeys();
		remainingFileCount = uploadKeys.size();
		LOG.info("Found {} uploaded file(s) for indexing.", remainingFileCount);
		for(String uploadKey : uploadKeys) {
			if (!continueCallback.get()) {
				return;
			}
			LOG.info("Indexing uploaded index file: {}", uploadKey);
			try (
					BufferedReader reader = getReader(uploadKey);
					CSVReader csvReader = new CSVReader(reader)
			) {
				String[] row;
				while ((row = csvReader.readNext()) != null) {
					if (!continueCallback.get()) {
						return;
					}
					if (indexCsvRow(row)) {
						sessionFilingCount += 1;
					}
				}
			} catch (CsvValidationException e) {
				throw new RuntimeException(e);
			}
			LOG.info("Completed uploaded index file: {}", uploadKey);
			sessionUploadCount += 1;
			deleteUpload(uploadKey);
		}
		remainingFileCount = getUploadKeys().size();
	}
}
