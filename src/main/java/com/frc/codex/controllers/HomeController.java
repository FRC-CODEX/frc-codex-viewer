package com.frc.codex.controllers;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import com.frc.codex.model.SearchFilingsResult;
import com.frc.codex.properties.FilingIndexProperties;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.model.Filing;
import com.frc.codex.model.SearchFilingsRequest;
import com.frc.codex.model.SurveyRequest;
import com.frc.codex.support.SupportManager;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class HomeController {
	private static final Logger LOG = LoggerFactory.getLogger(HomeController.class);
	private final DatabaseManager databaseManager;
	private final int searchMaximumPages;
	private final int searchPageSize;
	private final SupportManager supportManager;

	public HomeController(
			DatabaseManager databaseManager,
			FilingIndexProperties properties,
			SupportManager supportManager
	) {
		this.databaseManager = databaseManager;
		this.searchMaximumPages = properties.searchMaximumPages();
		this.searchPageSize = properties.searchPageSize();
		this.supportManager = supportManager;
	}

	@GetMapping("/health")
	public ResponseEntity<String> healthPage() {
		if (databaseManager.isHealthy()) {
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
	}

	private String getDateValidation(Callable<LocalDateTime> callable) throws Exception {
		try {
			callable.call();
			return null;
		} catch (DateTimeException e) {
			return "Please provide a valid date.";
		}
	}

	private List<Map.Entry<Integer,String>> buildPageLinks(SearchFilingsResult searchFilingsResult) {
		int currentPageNumber = searchFilingsResult.getPageNumber();
		int previousPageNumber = currentPageNumber - 1;
		int nextPageNumber = currentPageNumber + 1;
		int lastPageNumber = searchFilingsResult.getLastPageNumber();

		String ellipsesClass = "govuk-pagination__item--ellipses";
		String currentClass = "govuk-pagination__item--current";

		List<Map.Entry<Integer,String>> pageLinks = new java.util.ArrayList<>();
		// First Page
		if (previousPageNumber > 1) {
			pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(1, null));
		}
		// Previous Ellipsis
		if (previousPageNumber > 2) {
			pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(null, ellipsesClass));
		}
		// Previous Page
		if (previousPageNumber >= 1) {
			pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(previousPageNumber, null));
		}
		// Current Page
		pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(currentPageNumber, currentClass));
		// Next Page
		if (nextPageNumber <= lastPageNumber) {
			pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(nextPageNumber, null));
		}
		// Next Ellipsis
		if (nextPageNumber < lastPageNumber - 1) {
			pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(null, ellipsesClass));
		}
		// Last Page
		if (nextPageNumber < lastPageNumber) {
			pageLinks.add(new java.util.AbstractMap.SimpleEntry<>(lastPageNumber, null));
		}
		return pageLinks;
	}

	@GetMapping("/")
	public ModelAndView indexPage(@ModelAttribute SearchFilingsRequest searchFilingsRequest) throws Exception {
		ModelAndView model = new ModelAndView("index");
		model.addObject("minDocumentDateError", getDateValidation(searchFilingsRequest::getMinDocumentDate));
		model.addObject("minFilingDateError", getDateValidation(searchFilingsRequest::getMinFilingDate));
		model.addObject("maxDocumentDateError", getDateValidation(searchFilingsRequest::getMaxDocumentDate));
		model.addObject("maxFilingDateError", getDateValidation(searchFilingsRequest::getMaxFilingDate));
		searchFilingsRequest.setStatus(null);
		searchFilingsRequest.setPageSize(this.searchPageSize);
		searchFilingsRequest.setPageNumber(
				Math.max(1, Math.min(searchFilingsRequest.getPageNumber(), this.searchMaximumPages))
		);
		List<Filing> filings;
		long filingsCount = 0;
		String message = null;
		if (searchFilingsRequest.isEmpty()) {
			LOG.info("[ANALYTICS] HOME_PAGE");
			filings = null;
		} else {
			LOG.info("[ANALYTICS] SEARCH");
			try {
				filingsCount = databaseManager.getFilingsCount(searchFilingsRequest);
				filings = databaseManager.searchFilings(searchFilingsRequest);
			} catch (DateTimeException e) {
				filingsCount = 0;
				filings = List.of();
				message = e.getMessage();
			}
			if (filings.isEmpty()) {
				if (message == null) {
					message = "No filings matched your search criteria.";
				}
			}
		}
		SearchFilingsResult searchFilingsResult = SearchFilingsResult.builder()
				.filings(filings)
				.maximumPageNumber(this.searchMaximumPages)
				.pageNumber(searchFilingsRequest.getPageNumber())
				.pageSize(this.searchPageSize)
				.totalFilings(filingsCount)
				.build();

		List<Map.Entry<Integer,String>> pageLinks = buildPageLinks(searchFilingsResult);

		model.addObject("filingsCount", String.format("%,d", searchFilingsResult.getTotalFilings()));
		model.addObject("message", message);
		model.addObject("pageLinks", pageLinks);
		model.addObject("searchFilingsRequest", searchFilingsRequest);
		model.addObject("searchFilingsResult", searchFilingsResult);
		return model;
	}

	@GetMapping("/help")
	public ModelAndView helpPage(HttpServletRequest request) {
		ModelAndView model = new ModelAndView("help");
		model.addObject("success", null);
		model.addObject("supportEmail", supportManager.getSupportEmail());
		return model;
	}

	@GetMapping("/survey")
	public ModelAndView surveyPage() {
		SurveyRequest surveyRequest = new SurveyRequest();
		ModelAndView model = new ModelAndView("survey");
		model.addObject("success", null);
		model.addObject("surveyRequest", surveyRequest);
		return model;
	}

	@PostMapping("/survey")
	public ModelAndView surveyPost(SurveyRequest surveyRequest) {
		UUID id = supportManager.sendSurveyRequest(surveyRequest);
		ModelAndView model = new ModelAndView("survey");
		model.addObject("success", id != null);
		model.addObject("id", id);
		model.addObject("surveyRequest", id != null ? new SurveyRequest() : surveyRequest);
		model.setStatus(id == null ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK);
		return model;
	}
}
