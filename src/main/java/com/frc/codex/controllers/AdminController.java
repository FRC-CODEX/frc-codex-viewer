package com.frc.codex.controllers;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.frc.codex.properties.FilingIndexProperties;
import com.frc.codex.model.RegistryCode;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.database.impl.DatabaseManagerImpl;
import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseCompany;
import com.frc.codex.clients.companieshouse.CompaniesHouseHistoryClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseRateLimiter;
import com.frc.codex.clients.fca.FcaClient;
import com.frc.codex.clients.fca.FcaFiling;
import com.frc.codex.indexer.Indexer;
import com.frc.codex.indexer.QueueManager;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingStatus;
import com.frc.codex.model.NewFilingRequest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AdminController extends BaseController {
	private static final Logger LOG = LoggerFactory.getLogger(DatabaseManagerImpl.class);
	private final CompaniesHouseClient companiesHouseClient;
	private final CompaniesHouseHistoryClient companiesHouseHistoryClient;
	private final CompaniesHouseRateLimiter companiesHouseRateLimiter;
	private final DatabaseManager databaseManager;
	private final FcaClient fcaClient;
	private final Indexer indexer;
	private final QueueManager queueManager;

	public AdminController(
			FilingIndexProperties properties,
			CompaniesHouseClient companiesHouseClient,
			CompaniesHouseHistoryClient companiesHouseHistoryClient,
			CompaniesHouseRateLimiter companiesHouseRateLimiter,
			DatabaseManager databaseManager,
			FcaClient fcaClient,
			Indexer indexer,
			QueueManager queueManager
	) {
		super(properties);
		this.companiesHouseClient = companiesHouseClient;
		this.companiesHouseHistoryClient = companiesHouseHistoryClient;
		this.companiesHouseRateLimiter = companiesHouseRateLimiter;
		this.databaseManager = databaseManager;
		this.fcaClient = fcaClient;
		this.indexer = indexer;
		this.queueManager = queueManager;
	}

	@GetMapping("/admin/login/{key}")
	public String adminLogin(HttpServletResponse response,	@PathVariable("key") String key) {
		Cookie cookie = new Cookie(properties.adminCookieName(), key);
		cookie.setMaxAge(60 * 60 * 24); // 1 day
		cookie.setPath("/");
		response.addCookie(cookie);
		return "redirect:/admin";
	}

	@GetMapping("/admin/logout")
	public String adminLogout(HttpServletResponse response) {
		Cookie cookie = new Cookie(properties.adminCookieName(), "");
		cookie.setMaxAge(0);
		cookie.setPath("/");
		response.addCookie(cookie);
		return "redirect:/";
	}

	@GetMapping("/admin")
	public Object indexPage(HttpServletRequest request, Model model) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		model.addAttribute("chDocumentUrl", properties.companiesHouseDocumentApiBaseUrl());
		model.addAttribute("chInformationUrl", properties.companiesHouseInformationApiBaseUrl());
		model.addAttribute("chRateLimiter", companiesHouseRateLimiter.toString());
		model.addAttribute("chRestApiKey", !StringUtils.isEmpty(properties.companiesHouseRestApiKey()));
		model.addAttribute("chStreamApiKey", !StringUtils.isEmpty(properties.companiesHouseStreamApiKey()));
		model.addAttribute("chStreamUrl", properties.companiesHouseStreamApiBaseUrl());
		model.addAttribute("fcaDataApiBaseUrl", properties.fcaDataApiBaseUrl());
		model.addAttribute("fcaSearchApiUrl", properties.fcaSearchApiUrl());
		return "admin/index";
	}

	@PostMapping("/admin/filing/reset")
	public Object smokeTestSqsPage(HttpServletRequest request, @RequestParam("filingId") String filingId) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		databaseManager.resetFiling(UUID.fromString(filingId));
		return "redirect:/admin";
	}

	/**
	 * This endpoint demonstrates the Companies House client functionality
	 * by loading a company's information JSON.
	 */
	@GetMapping("/admin/smoketest/companieshouse/company/{companyNumber}")
	public Object smokeTestCompanyPage(
			HttpServletRequest request,
			Model model,
			@PathVariable("companyNumber") String companyNumber
	) throws JsonProcessingException {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		if (!companiesHouseClient.isEnabled()) {
			return new ResponseEntity<>("Companies House client is disabled", HttpStatus.SERVICE_UNAVAILABLE);
		}
		CompaniesHouseCompany company = this.companiesHouseClient.getCompany(companyNumber);
		model.addAttribute("company", company);
		List<NewFilingRequest> filings = this.companiesHouseClient.getCompanyFilings(companyNumber, "");
		String filingUrls = filings.stream()
				.map(NewFilingRequest::getDownloadUrl)
				.collect(Collectors.joining("\n"));
		model.addAttribute("filingUrls", filingUrls);
		return "admin/smoketest/companieshouse/company";
	}

	@GetMapping("/admin/smoketest/companieshouse/history")
	public Object smokeTestHistoryPage(
			HttpServletRequest request,
			Model model
	) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		List<URI> dailyLinks = this.companiesHouseHistoryClient.getDailyDownloadLinks();
		List<URI> monthlyLinks = this.companiesHouseHistoryClient.getMonthlyDownloadLinks();
		List<URI> archiveLinks = this.companiesHouseHistoryClient.getArchiveDownloadLinks();
		model.addAttribute("dailyLinks", dailyLinks);
		model.addAttribute("monthlyLinks", monthlyLinks);
		model.addAttribute("archiveLinks", archiveLinks);
		return "admin/smoketest/companieshouse/history";
	}

	/**
	 * This endpoint demonstrates the database client functionality
	 * by loading filing data from the database.
	 */
	@GetMapping("/admin/smoketest/database")
	public Object smokeTestDatabasePage(HttpServletRequest request) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		ModelAndView model = new ModelAndView("admin/smoketest/database");
		List<Filing> pendingFilings = this.databaseManager.getFilingsByStatus(FilingStatus.PENDING);
		List<Filing> queuedFilings = this.databaseManager.getFilingsByStatus(FilingStatus.QUEUED);
		List<Filing> unprocessedFilings = Stream.concat(pendingFilings.stream(), queuedFilings.stream()).toList();
		model.addObject("unprocessedFilings", unprocessedFilings);
		List<Filing> failedFilings = this.databaseManager.getFilingsByStatus(FilingStatus.FAILED);
		model.addObject("failedFilings", failedFilings);
		List<Filing> completedFilings = this.databaseManager.getFilingsByStatus(FilingStatus.COMPLETED);
		model.addObject("completedFilings", completedFilings);
		boolean healthy = completedFilings.size() > 0 && failedFilings.size() == 0;
		model.setStatus(healthy ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
		return model;
	}

	/**
	 * This endpoint demonstrates progress of the indexer by showing
	 * its progress in discovering filings.
	 */
	@GetMapping("/admin/smoketest/indexer")
	public Object smokeTestIndexerPage(HttpServletRequest request) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		ModelAndView model = new ModelAndView("admin/smoketest/indexer");
		String indexerStatus = indexer.getStatus();
		model.addObject("indexerStatus", indexerStatus);
		boolean healthy = indexerStatus.contains("Healthy");
		model.setStatus(healthy ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
		return model;
	}

	/**
	 * This endpoint waits until a pending filing exists and then invokes it.
	 * The user is redirected to the /wait endpoint for the filing.
	 */
	@GetMapping("/admin/smoketest/invoke")
	public String smokeTestInvokePage() throws InterruptedException {
		Filing filing = null;
		while (filing == null) {
			List<Filing> pendingFilings = this.databaseManager.getFilingsByStatus(FilingStatus.PENDING);
			if (pendingFilings.size() > 0) {
				filing = pendingFilings.get(0);
			} else {
				LOG.info("Invocation smoke test is waiting for pending filing...");
				Thread.sleep(5000);
			}
		}
		return "redirect:/view/" + filing.getFilingId() + "/wait";
	}

	/*
	 * This endpoint demonstrates the FCA client functionality
	 * by loading the last week's worth of filings.
	 */
	@GetMapping("/admin/smoketest/fca")
	public Object smokeTestFcaPage(HttpServletRequest request) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		ModelAndView model = new ModelAndView("admin/smoketest/fca");
		LocalDateTime sinceDate = LocalDateTime.now().minusDays(30);
		List<FcaFiling> filings = this.fcaClient.fetchAllSinceDate(sinceDate);
		model.addObject("sinceDate", sinceDate);
		model.addObject("filings", filings);
		boolean healthy = filings.size() > 0;
		model.setStatus(healthy ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
		return model;
	}

	/*
	 * This endpoint demonstrates progress of the indexer and processor by showing
	 * statistics of the associated SQS queues.
	 */
	@GetMapping("/admin/smoketest/queue")
	public Object smokeTestSqsPage(HttpServletRequest request) {
		if (!authenticateAdmin(request)) {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
		ModelAndView model = new ModelAndView("admin/smoketest/queue");
		String queueStatus = queueManager.getStatus();
		model.addObject("queueStatus", queueStatus);
		return model;
	}

	/**
	 * Waits for certain criteria to be met
	 */
	@GetMapping("/admin/smoketest/wait")
	public ResponseEntity<String> smokeTestWaitPage() throws InterruptedException {
		HashMap<RegistryCode, Filing> filings = new HashMap<>();
		boolean filingsReady = false;
		while (!filingsReady) {
			for(RegistryCode registryCode : RegistryCode.values()) {
				if (!filings.containsKey(registryCode)) {
					List<Filing> pendingFilings = this.databaseManager.getFilingsByStatus(FilingStatus.PENDING, registryCode);
					if (pendingFilings.size() > 0) {
						filings.put(registryCode, pendingFilings.get(0));
					}
				}
			}
			filingsReady = filings.size() >= RegistryCode.values().length;
			if (!filingsReady) {
				LOG.info("Waiting for filings to be ready for smoke testing...");
				Thread.sleep(5000);
			}
		}
		return new ResponseEntity<>("Ready", HttpStatus.OK);
	}
}
