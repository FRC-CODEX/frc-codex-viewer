package com.frc.codex.indexer.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseCompaniesIndexer;
import com.frc.codex.clients.companieshouse.CompaniesHouseHistoryClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseStreamIndexer;
import com.frc.codex.clients.companieshouse.CompaniesHouseStreamListener;
import com.frc.codex.clients.companieshouse.FilingFormat;
import com.frc.codex.clients.fca.FcaClient;
import com.frc.codex.clients.fca.FcaFiling;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.indexer.Indexer;
import com.frc.codex.indexer.IndexerJob;
import com.frc.codex.indexer.LambdaManager;
import com.frc.codex.indexer.MetricManager;
import com.frc.codex.indexer.QueueManager;
import com.frc.codex.indexer.UploadIndexer;
import com.frc.codex.model.ArchiveType;
import com.frc.codex.model.Company;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingPayload;
import com.frc.codex.model.FilingResultRequest;
import com.frc.codex.model.FilingStatus;
import com.frc.codex.model.NewFilingRequest;
import com.frc.codex.model.RegistryCode;
import com.frc.codex.model.companieshouse.CompaniesHouseArchive;
import com.frc.codex.properties.FilingIndexProperties;

import software.amazon.awssdk.services.lambda.model.InvokeResponse;

@Component
@Profile("application")
public class IndexerImpl implements Indexer {
	private static final Logger LOG = LoggerFactory.getLogger(IndexerImpl.class);
	private final CompaniesHouseCompaniesIndexer companiesHouseCompaniesIndexer;
	private final CompaniesHouseClient companiesHouseClient;
	private final CompaniesHouseHistoryClient companiesHouseHistoryClient;
	private final CompaniesHouseStreamIndexer companiesHouseStreamIndexer;
	private final CompaniesHouseStreamListener companiesHouseStreamListener;
	private final DatabaseManager databaseManager;
	private final FcaClient fcaClient;
	private final List<IndexerJob> jobs;
	private final LambdaManager lambdaManager;
	private final MetricManager metricManager;
	private final FilingIndexProperties properties;
	private final QueueManager queueManager;
	private final UploadIndexer uploadIndexer;

	private final Pattern companiesHouseFilenamePattern;
	private Date fcaSessionLastStartedDate;
	private Date fcaSessionLastEndedDate;

	public IndexerImpl(
			CompaniesHouseClient companiesHouseClient,
			CompaniesHouseCompaniesIndexer companiesHouseCompaniesIndexer,
			CompaniesHouseHistoryClient companiesHouseHistoryClient,
			CompaniesHouseStreamIndexer companiesHouseStreamIndexer,
			CompaniesHouseStreamListener companiesHouseStreamListener,
			DatabaseManager databaseManager,
			FcaClient fcaClient,
			FilingIndexProperties properties,
			LambdaManager lambdaManager,
			MetricManager metricManager,
			QueueManager queueManager,
			UploadIndexer uploadIndexer

	) {
		this.companiesHouseClient = companiesHouseClient;
		this.companiesHouseCompaniesIndexer = companiesHouseCompaniesIndexer;
		this.companiesHouseHistoryClient = companiesHouseHistoryClient;
		this.companiesHouseStreamIndexer = companiesHouseStreamIndexer;
		this.companiesHouseStreamListener = companiesHouseStreamListener;
		this.databaseManager = databaseManager;
		this.fcaClient = fcaClient;
		this.lambdaManager = lambdaManager;
		this.metricManager = metricManager;
		this.properties = properties;
		this.queueManager = queueManager;
		this.uploadIndexer = uploadIndexer;
		this.companiesHouseFilenamePattern = Pattern.compile(
				"Prod\\d+_\\d+_([a-zA-Z0-9]+)_(\\d{8})\\..*"
		);
		this.jobs = List.of(
				companiesHouseCompaniesIndexer,
				companiesHouseStreamIndexer,
				companiesHouseStreamListener,
				this.uploadIndexer
		);
	}

	public String getStatus() {
		boolean healthy = jobs.stream().allMatch(IndexerJob::isHealthy);
		return String.format("""
						Indexer Status: %s
						%s
						FCA:
						\tLast started: %s
						\tLast finished: %s""",
				healthy ? "Healthy" : "Unhealthy",
				String.join("\n", jobs.stream().map(IndexerJob::getStatus).toList()),
				fcaSessionLastStartedDate,
				fcaSessionLastEndedDate
		);
	}

	/*
	 * Indexes Companies House filings from captured filing stream events.
	 * Processes in batches to allow for other tasks to run.
	 */
	@Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
	public void indexCompaniesHouseFilings() throws IOException {
		Supplier<Boolean> continueCallback = () -> {
			if (!companiesHouseClient.isEnabled()) {
				LOG.info("Cannot index from Companies House stream. Companies House client is disabled.");
				return false;
			}
			return !databaseManager.checkRegistryLimit(RegistryCode.COMPANIES_HOUSE, properties.filingLimitCompaniesHouse());
		};
		companiesHouseStreamIndexer.run(continueCallback);
	}

	/*
	 * Captures Companies House filing stream events for later processing.
	 * Runs continuously as long as HTTP connection remains open.
	 * If the connection closes, resumes after one minute.
	 * One scheduler thread is effectively dedicated to this task.
	 */
	@Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void listenToCompaniesHouseFilingsStream() throws IOException {
		Supplier<Boolean> continueCallback = () -> {
			if (!companiesHouseClient.isEnabled()) {
				LOG.info("Cannot listen to Companies House stream. Companies House client is disabled.");
				return false;
			}
			return true;
		};
		companiesHouseStreamListener.run(continueCallback);
	}

	/*
	 * Indexes Companies House filings from local companies index.
	 */
	@Scheduled(initialDelay = 90, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	public void indexFilingsFromCompaniesIndex() throws IOException {
		Supplier<Boolean> continueCallback = () -> {
			if (!companiesHouseClient.isEnabled()) {
				LOG.info("Cannot index filings from companies index: Companies House client is disabled.");
				return false;
			}
			return !databaseManager.checkRegistryLimit(RegistryCode.COMPANIES_HOUSE, properties.filingLimitCompaniesHouse());
		};
		companiesHouseCompaniesIndexer.run(continueCallback);
	}

	/*
	 * Indexes filings from uploaded CSVs.
	 */
	@Scheduled(initialDelay = 60, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	public void indexFilingsFromUploads() throws IOException {
		LOG.info("Starting indexing from uploads.");
		Supplier<Boolean> continueCallback = () -> true; // CSV uploads circumvent the filing limit.
		uploadIndexer.run(continueCallback);
		LOG.info("Completed indexing from uploads.");
	}

	/*
	 * Processes a Companies House archive by downloading it and index known companies by
	 * extracting company numbers from the contained filenames.
	 * Returns true if the archive was processed successfully or doesn't need processing.
	 */
	private boolean processCompaniesHouseArchive(URI uri, ArchiveType archiveType, Set<String> existingCompanyNumbers) {
		if (databaseManager.checkCompaniesLimit(properties.unprocessedCompaniesLimit())) {
			return false;
		}
		String filename = new File(uri.getPath()).getName();
		if (databaseManager.companiesHouseArchiveExists(filename)) {
			LOG.debug("Skipping existing CH archive: {}", uri);
			return true;
		}
		Path tempFile;
		try {
			tempFile = Files.createTempFile(filename, ".zip");
		} catch (IOException e) {
			LOG.error("Failed to create temporary file", e);
			return false;
		}
		try (Closeable ignored = () -> Files.deleteIfExists(tempFile)) {
			return processCompaniesHouseArchiveUsingTempFile(uri, archiveType, filename, tempFile, existingCompanyNumbers);
		} catch (IOException e) {
			LOG.error("Failed to delete temporary file: {}", tempFile, e);
			return false;
		}
	}

	private boolean processCompaniesHouseArchiveUsingTempFile(URI uri, ArchiveType archiveType, String filename, Path tempFile, Set<String> existingCompanyNumbers) {
		LOG.info("Downloading archive: {}", uri);
		try {
			this.companiesHouseHistoryClient.downloadArchive(uri, tempFile);
		} catch (IOException e) {
			LOG.error("Failed to download archive: {}", uri, e);
			return false;
		}
		LOG.info("Downloaded archive: {}", tempFile);

		List<String> arcnames;
		try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
			arcnames = zipFile.stream()
					.map(ZipEntry::getName)
					.sorted()
					.toList();
		} catch (Exception e) {
			LOG.error("Failed to get arcnames for archive: {}", uri, e);
			return false;
		}
		LOG.info("Found arcnames: {}", arcnames.size());

		// Example: Prod223_3785_13056435_20240331.html
		for (String arcname : arcnames) {
			Matcher matcher = companiesHouseFilenamePattern.matcher(arcname);
			if (!matcher.matches()) {
				LOG.warn("Found invalid archive entry in {}: {}", uri, arcname);
				continue;
			}
			String companyNumber = matcher.group(1);
			if (existingCompanyNumbers.contains(companyNumber)) {
				if (archiveType.isResetsCompany()) {
					String documentDateStr = matcher.group(2);
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
					LocalDate documentDate = LocalDate.parse(documentDateStr, formatter);
					Filing existingFiling = databaseManager.getFiling(companyNumber, documentDate);
					if (existingFiling == null) {
						databaseManager.resetCompany(companyNumber);
						LOG.info("Reset company {}.", companyNumber);
						continue;
					}
					LOG.debug("Skipped reset, found filing matching company number {} and document date {}.", companyNumber, documentDate);
				}
				LOG.debug("Skipping existing company: {}", companyNumber);
				continue;
			}
			Company company = Company.builder()
					.companyNumber(companyNumber)
					.build();
			databaseManager.createCompany(company);
			LOG.debug("Created company {}.", companyNumber);
			existingCompanyNumbers.add(companyNumber);
		}
		CompaniesHouseArchive archive = CompaniesHouseArchive.builder()
				.filename(filename)
				.uri(uri)
				.archiveType(archiveType.getCode())
				.build();
		databaseManager.createCompaniesHouseArchive(archive);
		LOG.info("Completed archive: {}", filename);
		return true;
	}

	@Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
	public void indexCompaniesFromCompaniesHouseArchives() {
		if (!properties.enableIndexingDailyArchives() &&
				!properties.enableIndexingMonthlyArchives() &&
				!properties.enableIndexingArchiveArchives()
		) {
			return;
		}

		if (databaseManager.checkCompaniesLimit(properties.unprocessedCompaniesLimit())) {
			return;
		}

		var existingCompanyNumbers = new HashSet<String>(databaseManager.getCompaniesCompanyNumbers());
		List<URI> downloadLinks;
		if (properties.enableIndexingDailyArchives()) {
			downloadLinks = companiesHouseHistoryClient.getDailyDownloadLinks();
			for (URI uri : downloadLinks) {
				if (!processCompaniesHouseArchive(uri, ArchiveType.DAILY, existingCompanyNumbers)) {
					return;
				}
			}
		}
		if (properties.enableIndexingMonthlyArchives()) {
			downloadLinks = companiesHouseHistoryClient.getMonthlyDownloadLinks();
			for (URI uri : downloadLinks) {
				if (!processCompaniesHouseArchive(uri, ArchiveType.MONTHLY, existingCompanyNumbers)) {
					return;
				}
			}
		}
		if (properties.enableIndexingArchiveArchives()) {
			downloadLinks = companiesHouseHistoryClient.getArchiveDownloadLinks();
			for (URI uri : downloadLinks) {
				if (!processCompaniesHouseArchive(uri, ArchiveType.ARCHIVE, existingCompanyNumbers)) {
					return;
				}
			}
		}
	}

	/*
	 * Indexes FCA filings.
	 * Runs hourly, taking only a few seconds.
	 * Can share a scheduler thread with other tasks.
	 */
	@Scheduled(initialDelay = 1, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
	public void indexFca() {
		// TODO: Implement as IndexerJob
		fcaSessionLastStartedDate = new Date();
		LOG.info("Starting FCA indexing at {}", fcaSessionLastStartedDate);
		if (databaseManager.checkRegistryLimit(RegistryCode.FCA, properties.filingLimitFca())) {
			return;
		}
		LocalDateTime fcaStartDate = properties.fcaPastDays() <= 0 ? null :
				LocalDateTime.now().minusDays(properties.fcaPastDays());
		LocalDateTime latestSubmittedDate = databaseManager.getLatestFcaFilingDate(fcaStartDate);
		List<FcaFiling> filings = fcaClient.fetchAllSinceDate(latestSubmittedDate);
		for (FcaFiling filing : filings) {
			NewFilingRequest newFilingRequest = NewFilingRequest.builder()
					.companyName(filing.companyName())
					.companyNumber(filing.lei())
					.documentDate(filing.documentDate())
					.downloadUrl(filing.downloadUrl())
					.externalFilingId(filing.sequenceId())
					.externalViewUrl(filing.infoUrl())
					.filingDate(filing.submittedDate())
					.format(FilingFormat.ZIP.getFormat())
					.registryCode(RegistryCode.FCA.getCode())
					.build();
			if (databaseManager.filingExists(newFilingRequest.getRegistryCode(), newFilingRequest.getExternalFilingId())) {
				LOG.info("Skipping existing FCA filing: {}", filing.downloadUrl());
				continue;
			}
			if (databaseManager.checkRegistryLimit(RegistryCode.FCA, properties.filingLimitFca())) {
				break;
			}
			UUID filingId = databaseManager.createFiling(newFilingRequest);
			LOG.info("Created FCA filing for {}: {}", filing.downloadUrl(), filingId);
		}
		fcaSessionLastEndedDate = new Date();
		LOG.info("Completed FCA indexing at {}", fcaSessionLastEndedDate);
	}

	@Scheduled(initialDelay = 1, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
	public void preprocessFcaViaLambda() throws InterruptedException {
		int concurrency = properties.lambdaPreprocessingConcurrency();
		if (concurrency < 1) {
			return;
		}
		if (databaseManager.checkRegistryLimit(RegistryCode.FCA, properties.filingLimitFca())) {
			return;
		}
		LOG.info("Starting preprocessing of FCA filings via Lambda.");
		Queue<Filing> filings = new LinkedList<>(databaseManager.getFilingsByStatus(FilingStatus.PENDING, RegistryCode.FCA));
		@SuppressWarnings("unchecked")
		CompletableFuture<InvokeResponse>[] futures = new CompletableFuture[concurrency];
		while (Arrays.stream(futures).anyMatch(Objects::nonNull) || !filings.isEmpty()) {
			for (int i = 0; i < futures.length; i++) {
				CompletableFuture<InvokeResponse> future = futures[i];
				if (future == null) {
					Filing filing = filings.poll();
					if (filing == null) {
						continue; // Nothing new to add, allow other futures to complete.
					}
					if (databaseManager.checkRegistryLimit(RegistryCode.FCA, properties.filingLimitFca())) {
						continue; // Limit reached, allow other futures to complete.
					}
					// Invoke the Lambda function and assign to the future slot.
					LOG.info("Started preprocessing of FCA filing via Lambda: {}", filing.getFilingId());
					future = lambdaManager.invokeAsync(new FilingPayload(
							filing.getFilingId(),
							filing.getDownloadUrl(),
							filing.getFormat(),
							filing.getRegistryCode()
					));
					futures[i] = future;
				} else if (future.isDone()) {
					futures[i] = null;
					InvokeResponse response;
					try {
						response = future.get();
					} catch (ExecutionException e) {
						LOG.error("Preprocessing via Lambda encountered an exception.", e);
						continue;
					}
					FilingResultRequest result = lambdaManager.parseResult(response);
					if (result == null) {
						continue;
					}
					LOG.info("Completed preprocessing of FCA filing via Lambda: {}", result.getFilingId());
					databaseManager.applyFilingResult(result);
				}
			}
			Thread.sleep(1000);
		}
	}

	/*
	 * Retrieves messages from the results queue and applies them to the database.
	 * Reruns after a delay of 20 seconds.
	 * Can share a scheduler thread with other tasks.
	 */
	@Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
	public void processResults() {
		if (!properties.enablePreprocessing()) {
			return;
		}
		LOG.info("Starting to process results.");
		queueManager.processResults((FilingResultRequest filingResultRequest) -> {
			try {
				LOG.info("Applying filing result: {}", filingResultRequest);
				databaseManager.applyFilingResult(filingResultRequest);
				return true;
			} catch (Exception e) {
				LOG.error("Failed to process result: {}", filingResultRequest.getFilingId(), e);
				return false;
			}
		});
	}

	/*
	 * Retrieves pending filings from the database and adds them to the job queue.
	 * Reruns after a delay of 20 seconds.
	 * Can share a scheduler thread with other tasks.
	 */
	@Scheduled(initialDelay = 5, fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
	public void queueJobs() {
		if (!properties.enablePreprocessing()) {
			return;
		}
		LOG.info("Starting to queue jobs.");
		List<Filing> filings = databaseManager.getFilingsByStatus(FilingStatus.PENDING);
		LOG.info("Pending filings: {}", filings.size());
		queueManager.addJobs(filings, (Filing filing) -> {
			databaseManager.updateFilingStatus(filing.getFilingId(), FilingStatus.QUEUED.toString());
		});
	}

	/*
	 * Calculates and uploads metrics on an approximate one-minute interval
	 */
	@Scheduled(initialDelay = 30, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	public void uploadMetrics() {
		metricManager.uploadMetrics();
	}
}
