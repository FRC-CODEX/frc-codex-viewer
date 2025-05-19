package com.frc.codex.controllers;

import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import com.frc.codex.clients.companieshouse.FilingFormat;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.indexer.LambdaManager;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingPayload;
import com.frc.codex.model.FilingResultRequest;
import com.frc.codex.model.FilingStatus;
import com.frc.codex.model.OimFormat;
import com.frc.codex.model.RegistryCode;
import com.frc.codex.oim.ReportPackageProvider;
import com.frc.codex.properties.FilingIndexProperties;
import com.frc.codex.tools.RateLimiter;

import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@RestController
public class ViewController {
	private static final Logger LOG = LoggerFactory.getLogger(ViewController.class);

	private final DatabaseManager databaseManager;
	private final LambdaManager lambdaManager;
	private final FilingIndexProperties properties;
	private final RestTemplate restTemplate;
	private final S3Client s3Client;
	private final ReportPackageProvider reportPackageProvider;
	private final ConcurrentHashMap<UUID, CompletableFuture<InvokeResponse>> invokeFutures;
	private final RateLimiter publicPageRateLimiter;

	public ViewController(
			FilingIndexProperties properties,
			DatabaseManager databaseManager,
			LambdaManager lambdaManager,
			RestTemplate restTemplate,
			S3Client s3Client,
			ReportPackageProvider reportPackageProvider
	) {
		this.properties = requireNonNull(properties);
		this.databaseManager = requireNonNull(databaseManager);
		this.lambdaManager = requireNonNull(lambdaManager);
		this.restTemplate = requireNonNull(restTemplate);
		this.s3Client = requireNonNull(s3Client);
		this.reportPackageProvider = requireNonNull(reportPackageProvider);
		this.invokeFutures = new ConcurrentHashMap<>();
		this.publicPageRateLimiter = new RateLimiter(
				properties.companiesHouseRapidRateLimit(),
				properties.companiesHouseRapidRateWindow()
		);

	}

	private ModelAndView loadingResult(Filing filing) {
		return new ModelAndView("redirect:/view/" + filing.getFilingId() + "/loading");
	}

	private ModelAndView onDemandViewer(Filing filing) {
		this.processFiling(filing);
		return loadingResult(filing);
	}

	private void processFiling(Filing filing) {
		UUID filingId = filing.getFilingId();
		if (!FilingStatus.isProcessingAllowed(filing.getStatus())) {
			throw new ResponseStatusException(NOT_FOUND, "Filing has been deleted.");
		}
		try {
			LOG.info("Processing filing on demand: {}", filingId);
			CompletableFuture<InvokeResponse> invokeResponse = lambdaManager.invokeAsync(new FilingPayload(
					filingId,
					filing.getDownloadUrl(),
					filing.getFormat(),
					filing.getRegistryCode()
			));
			invokeFutures.put(filingId, invokeResponse);
		} catch (Exception e) {
			invokeFutures.remove(filingId);
			throw e;
		}
	}

	private ModelAndView viewerResult(UUID filingId, String stubViewerUrl) {
		return new ModelAndView("redirect:/view/" + filingId + "/" + stubViewerUrl);
	}

	@GetMapping("/view/{filingId}/loading")
	public ModelAndView loadingPage(
			@PathVariable("filingId") String filingId
	) {
		ModelAndView model = new ModelAndView("view/loading");
		model.addObject("iframeSrc", "/view/" + filingId + "/public");
		return model;
	}

	@GetMapping("/download/{filingId}/{format}")
	@ResponseBody
	public ModelAndView download(
			HttpServletResponse response,
			@PathVariable("filingId") String filingId,
			@PathVariable("format") String format) throws IOException, InterruptedException {
		OimFormat oimFormat = OimFormat.fromFormat(format);
		if (oimFormat == null) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return null;
		}
		LOG.info("[ANALYTICS] DOWNLOAD (filingId=\"{}\",format=\"{}\")", filingId, oimFormat.getFormat());
		UUID filingUuid = UUID.fromString(filingId);
		Filing filing = databaseManager.getFiling(filingUuid);
		boolean internalError = false;
		if (filing.getStatus().equals(FilingStatus.PENDING.toString()) || filing.getStatus().equals(FilingStatus.QUEUED.toString())) {
			CompletableFuture<InvokeResponse> future = invokeFutures.get(filingUuid);
			if (future == null) {
				// No request in progress. We'll start one.
				this.processFiling(filing);
			}
			internalError = !waitForFiling(filingUuid);
			if (!internalError) {
				// Reload the filing object from the database now with processed filename details.
				filing = databaseManager.getFiling(filingUuid);
			}
		}
		if (
				FilingStatus.isViewerUnavailable(filing.getStatus()) ||
				filing.getFilename() == null ||
				filing.getOimDirectory() == null
		) {
			internalError = true;
		}
		if (internalError) {
			return unavailableResult("Download unavailable. The requested filing could not be converted to " + oimFormat.getFormat() + " OIM format.");
		}
		response.setContentType("application/zip");
		response.setStatus(HttpStatus.OK.value());
		String filename = String.format("%s.%s.zip", filing.getFilenameStem(), oimFormat.getFormat().toLowerCase());
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream())) {
			reportPackageProvider.writeReportPackage(filing, oimFormat, zipOutputStream);
			return null;
		}
	}

	private boolean filingSupportsPreview(Filing filing) {
		if (Objects.equals(filing.getRegistryCode(), RegistryCode.FCA.getCode())) {
			return true;
		}
		return !filing.getFormat().equals(FilingFormat.ZIP.getFormat());
	}

	@GetMapping("/view/{filingId}/public")
	public void publicPage(
			HttpServletResponse response,
			@PathVariable("filingId") String filingId
	) throws InterruptedException {

		UUID filingUuid = UUID.fromString(filingId);
		Filing filing = databaseManager.getFiling(filingUuid);
		if (!filingSupportsPreview(filing)) {
			return;
		}
		String filingUrl = filing.getExternalViewUrl();

		response.setStatus(HttpStatus.OK.value());
		ResponseExtractor<Void> responseExtractor = resp -> {
			try (
					InputStream inputStream = resp.getBody();
					OutputStream outputStream = response.getOutputStream()
			) {
				MediaType contentType = resp.getHeaders().getContentType();
				if (contentType == null) {
					contentType = MediaType.APPLICATION_XHTML_XML; // application/xhtml+xml
				}
				response.setContentType(contentType.toString());
				inputStream.transferTo(outputStream);
			} catch (IOException e) {
				LOG.warn("Unable to write response body.", e);
				response.setStatus(HttpStatus.NOT_FOUND.value());
			}
			return null;
		};

		// Wait for rate limiting
		publicPageRateLimiter.waitForRapidRateLimit();

		// Register timestamp for rate limiting
		publicPageRateLimiter.registerTimestamp();

		restTemplate.execute(
				filingUrl,
				HttpMethod.GET,
				null,
				responseExtractor
		);

		// Register another timestamp for rate limiting, so long-running requests
		// aren't ignored.
		publicPageRateLimiter.registerTimestamp();
	}

	/**
	 * This endpoint is an alternative entry point for the viewer, using the external filing ID.
	 */
	@RequestMapping("/view/{registryCode}/{externalFilingId}/viewer")
	public ModelAndView viewerPageByExternalFilingId(
			@PathVariable("registryCode") String registryCode,
			@PathVariable("externalFilingId") String externalFilingId
	) {
		UUID filingId = databaseManager.getFilingId(registryCode, externalFilingId);
		if (filingId == null) {
			throw new ResponseStatusException(NOT_FOUND, "Unable to find filing.");
		}
		return viewerPage(filingId.toString());
	}

	/**
	 * This endpoint serves as a proxy to the S3 bucket hosting the stub viewer.
	 */
	@RequestMapping("/view/{filingId}/viewer")
	public ModelAndView viewerPage(
			@PathVariable("filingId") String filingId
	) {
		UUID filingUuid = UUID.fromString(filingId);
		Filing filing = databaseManager.getFiling(filingUuid);
		LOG.info("[ANALYTICS] VIEWER (filingId=\"{}\",companyNumber=\"{}\")", filingId, filing.getCompanyNumber());
		if (filing.getStatus().equals(FilingStatus.COMPLETED.toString())) {
			// Already completed, redirect directly to viewer.
			return viewerResult(filing.getFilingId(), filing.getStubViewerUrl());
		}
		if (FilingStatus.isViewerUnavailable(filing.getStatus())) {
			// Generation failed, show error message.
			return unavailableResult("Sorry, this viewer is currently unavailable.");
		}
		CompletableFuture<InvokeResponse> future = invokeFutures.get(filingUuid);
		if (future == null) {
			// No request in progress. We'll start one.
			return onDemandViewer(filing);
		} else {
			// A request is already in progress, we'll redirect to the loading page to wait.
			return loadingResult(filing);
		}
	}

	/**
	 * This endpoint serves as a proxy to the S3 bucket hosting the stub viewer.
	 */
	@RequestMapping("/view/{jobId}/{assetKey}")
	@ResponseBody
	public void viewerAssetPage(
			HttpServletResponse response,
			@PathVariable("jobId") String jobId,
			@PathVariable("assetKey") String assetKey
	) throws IOException {
		String key = jobId + "/" + assetKey;
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(properties.s3ResultsBucketName())
				.key(key)
				.build();
		response.setContentType(MediaType.TEXT_HTML_VALUE);
		response.setStatus(HttpStatus.OK.value());
		try(
				ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);
				OutputStream outputStream = response.getOutputStream()
		) {
			responseInputStream.transferTo(outputStream);
		} catch (NoSuchKeyException e) {
			throw new ResponseStatusException(NOT_FOUND, "Filing asset not found.");
		}
	}

	private ModelAndView unavailableResult(String msg) {
		ModelAndView modelAndView = new ModelAndView("view/unavailable");
		modelAndView.addObject("message", msg);
		return modelAndView;
	}

	@GetMapping("/view/{filingId}/wait")
	public ModelAndView waitPage(
			@PathVariable("filingId") String filingId
	) {
		LOG.info("[ANALYTICS] LOADING (filingId=\"{}\")", filingId);
		UUID filingUuid = UUID.fromString(filingId);
		if (!invokeFutures.containsKey(filingUuid)) {
			// We want to invoke on-demand processing here, but not actually return the /loading result.
			onDemandViewer(databaseManager.getFiling(filingUuid));
		}
		if (waitForFiling(filingUuid)) {
			return new ModelAndView("redirect:/view/" + filingId + "/viewer");
		} else {
			return unavailableResult("Processing failed. Please try again later.");
		}
	}

	private boolean waitForFiling(UUID filingUuid) {
		CompletableFuture<InvokeResponse> future = invokeFutures.get(filingUuid);
		try {
			LOG.info("Awaiting Lambda result for filing: {}", filingUuid);
			InvokeResponse invokeResponse = future.get();
			// Synchronize this block to ensure that only one request
			// applies the result and removes the future.
			synchronized (future) {
				if (invokeFutures.containsKey(filingUuid)) {
					FilingResultRequest result = lambdaManager.parseResult(invokeResponse);
					// Apply the result before we remove from the map to ensure that no requests
					// occur after a future is removed but before the result is applied.
					databaseManager.applyFilingResult(result);
					invokeFutures.remove(filingUuid);
					if (result.isSuccess()) {
						LOG.info("[ANALYTICS] PROCESSING_COMPLETED (filingId=\"{}\")", filingUuid);
					} else {
						LOG.info("[ANALYTICS] PROCESSING_FAILED (filingId=\"{}\")", filingUuid);
					}
					return result.isSuccess();
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Encountered exception while awaiting Lambda result for filing: {}", filingUuid, e);
			return false;
		}
		return true;
	}
}
