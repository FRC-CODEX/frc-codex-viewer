package com.frc.codex.indexer.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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

import com.frc.codex.FilingIndexProperties;
import com.frc.codex.RegistryCode;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.discovery.companieshouse.CompaniesHouseClient;
import com.frc.codex.discovery.companieshouse.CompaniesHouseHistoryClient;
import com.frc.codex.discovery.companieshouse.impl.CompaniesHouseCompaniesIndexerImpl;
import com.frc.codex.discovery.companieshouse.impl.CompaniesHouseStreamIndexerImpl;
import com.frc.codex.discovery.fca.FcaClient;
import com.frc.codex.discovery.fca.FcaFiling;
import com.frc.codex.indexer.Indexer;
import com.frc.codex.indexer.IndexerJob;
import com.frc.codex.indexer.LambdaManager;
import com.frc.codex.indexer.QueueManager;
import com.frc.codex.model.Company;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingPayload;
import com.frc.codex.model.FilingResultRequest;
import com.frc.codex.model.FilingStatus;
import com.frc.codex.model.NewFilingRequest;
import com.frc.codex.model.companieshouse.CompaniesHouseArchive;

import software.amazon.awssdk.services.lambda.model.InvokeResponse;

@Component
@Profile("application")
public class IndexerImpl implements Indexer {
	private static final Logger LOG = LoggerFactory.getLogger(IndexerImpl.class);
	private final CompaniesHouseCompaniesIndexerImpl companiesHouseCompaniesIndexer;
	private final CompaniesHouseClient companiesHouseClient;
	private final CompaniesHouseHistoryClient companiesHouseHistoryClient;
	private final CompaniesHouseStreamIndexerImpl companiesHouseStreamIndexer;
	private final DatabaseManager databaseManager;
	private final FcaClient fcaClient;
	private final List<IndexerJob> jobs;
	private final LambdaManager lambdaManager;
	private final FilingIndexProperties properties;
	private final QueueManager queueManager;

	private final Pattern companiesHouseFilenamePattern;
	private Date fcaSessionLastStartedDate;
	private Date fcaSessionLastEndedDate;

	public IndexerImpl(
			FilingIndexProperties properties,
			CompaniesHouseClient companiesHouseClient,
			CompaniesHouseHistoryClient companiesHouseHistoryClient,
			DatabaseManager databaseManager,
			FcaClient fcaClient,
			LambdaManager lambdaManager,
			QueueManager queueManager
	) {
		this.properties = properties;
		this.companiesHouseCompaniesIndexer = new CompaniesHouseCompaniesIndexerImpl(companiesHouseClient, databaseManager); // TODO: Dependency injection?
		this.companiesHouseClient = companiesHouseClient;
		this.companiesHouseHistoryClient = companiesHouseHistoryClient;
		this.companiesHouseStreamIndexer = new CompaniesHouseStreamIndexerImpl(companiesHouseClient, databaseManager); // TODO: Dependency injection?
		this.databaseManager = databaseManager;
		this.fcaClient = fcaClient;
		this.lambdaManager = lambdaManager;
		this.queueManager = queueManager;
		this.companiesHouseFilenamePattern = Pattern.compile(
				"Prod\\d+_\\d+_([a-zA-Z0-9]+)_(\\d{8})\\..*"
		);
		this.jobs = List.of(
				companiesHouseCompaniesIndexer,
				companiesHouseStreamIndexer
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
	 * Indexes Companies House filings from stream.
	 * Runs continuously as long as HTTP connection remains open.
	 * If the connection closes, resumes after one minute.
	 * One scheduler thread is effectively dedicated to this task.
	 */
	@Scheduled(fixedDelay = 60 * 1000)
	public void indexCompaniesHouseFilings() throws IOException {
		Supplier<Boolean> continueCallback = () -> {
			if (!companiesHouseClient.isEnabled()) {
				LOG.info("Can not index from Companies House stream. Companies House client is disabled.");
				return false;
			}
			return !databaseManager.checkRegistryLimit(RegistryCode.COMPANIES_HOUSE, properties.filingLimitCompaniesHouse());
		};
		companiesHouseStreamIndexer.run(continueCallback);
	}

	/*
	 * Indexes Companies House filings from local companies index.
	 */
	@Scheduled(fixedDelay = 60 * 1000)
	public void indexFilingsFromCompaniesIndex() throws IOException {
		Supplier<Boolean> continueCallback = () -> {
			if (!companiesHouseClient.isEnabled()) {
				LOG.info("Can not index filings from companies index: Companies House client is disabled.");
				return false;
			}
			return !databaseManager.checkRegistryLimit(RegistryCode.COMPANIES_HOUSE, properties.filingLimitCompaniesHouse());
		};
		companiesHouseCompaniesIndexer.run(continueCallback);
	}

	/*
	 * Processes a Companies House archive by downloading it and index known companies by
	 * extracting company numbers from the contained filenames.
	 * Returns true if the archive was processed successfully or doesn't need processing.
	 */
	private boolean processCompaniesHouseArchive(URI uri, String archiveType, Set<String> existingCompanyNumbers) {
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

	private boolean processCompaniesHouseArchiveUsingTempFile(URI uri, String archiveType, String filename, Path tempFile, Set<String> existingCompanyNumbers) {
		boolean completed = true;
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
				completed = false;
				continue;
			}
			String companyNumber = matcher.group(1);
			if (existingCompanyNumbers.contains(companyNumber)) {
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
		if (completed) {
			CompaniesHouseArchive archive = CompaniesHouseArchive.builder()
					.filename(filename)
					.uri(uri)
					.archiveType(archiveType)
					.build();
			databaseManager.createCompaniesHouseArchive(archive);
			LOG.info("Completed archive: {}", filename);
		} else {
			LOG.error("Archive not completed: {}", filename);
		}
		return true;
	}

	@Scheduled(fixedDelay = 30 * 60 * 1000)
	public void indexCompaniesFromCompaniesHouseArchives() {
		if (databaseManager.checkCompaniesLimit(properties.unprocessedCompaniesLimit())) {
			return;
		}
		var existingCompanyNumbers = new HashSet<String>(databaseManager.getCompaniesCompanyNumbers());
		List<URI> downloadLinks;
		downloadLinks = companiesHouseHistoryClient.getDailyDownloadLinks();
		for (URI uri : downloadLinks) {
			if (!processCompaniesHouseArchive(uri, "daily", existingCompanyNumbers)) {
				return;
			}
		}
		downloadLinks = companiesHouseHistoryClient.getMonthlyDownloadLinks();
		for (URI uri : downloadLinks) {
			if (!processCompaniesHouseArchive(uri, "monthly", existingCompanyNumbers)) {
				return;
			}
		}
		downloadLinks = companiesHouseHistoryClient.getArchiveDownloadLinks();
		for (URI uri : downloadLinks) {
			if (!processCompaniesHouseArchive(uri, "archive", existingCompanyNumbers)) {
				return;
			}
		}
	}

	/*
	 * Indexes FCA filings.
	 * Runs hourly, taking only a few seconds.
	 * Can share a scheduler thread with other tasks.
	 */
	@Scheduled(fixedDelay = 60 * 60 * 1000)
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

	@Scheduled(fixedDelay = 60 * 10 * 1000)
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
	@Scheduled(fixedDelay = 20 * 1000)
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
	@Scheduled(fixedDelay = 20 * 1000)
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
}
