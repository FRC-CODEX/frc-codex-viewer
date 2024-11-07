package com.frc.codex.discovery.fca.impl;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.util.Objects.requireNonNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frc.codex.FilingIndexProperties;
import com.frc.codex.discovery.fca.FcaClient;
import com.frc.codex.discovery.fca.FcaFiling;

@Component
@Profile("application")
public class FcaClientImpl implements FcaClient {
	private static final DateTimeFormatter INCOMING_JSON_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	private static final DateTimeFormatter OUTGOING_JSON_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final Logger LOG = LoggerFactory.getLogger(FcaClientImpl.class);
	private final String dataApiBaseUrl;
	private final int pageSize;
	private final RestTemplate restTemplate;
	private final String searchApiUrl;

	public FcaClientImpl(FilingIndexProperties properties, RestTemplate restTemplate) {
		this.dataApiBaseUrl = properties.fcaDataApiBaseUrl();
		this.pageSize = 1000;
		this.restTemplate = requireNonNull(restTemplate);
		this.searchApiUrl = properties.fcaSearchApiUrl();
	}

	/*
	 * Builds a JSON request body for fetching FCA filings.
	 * @param sinceDate The date to fetch filings since.
	 * @param page The page number to fetch.
	 * @return The JSON request body.
	 */
	private String buildJson(LocalDateTime sinceDate, int page) {
		ObjectNode node = new ObjectMapper().createObjectNode();
		node.put("from", this.pageSize * (page - 1));
		node.put("size", this.pageSize);
		node.put("sort", "submitted_date");
		node.put("sortorder", "desc");
		ObjectNode criteriaObj = node.putObject("criteriaObj");
		ObjectNode criteria = criteriaObj.putArray("criteria").addObject();
		criteria.put("name", "tag_esef");
		criteria.putArray("value").add("Tagged");
		ObjectNode dateCriteria = criteriaObj.putArray("dateCriteria").addObject();
		dateCriteria.put("name", "submitted_date");
		ObjectNode dateValue = dateCriteria.putObject("value");
		if (sinceDate == null) {
			dateValue.putNull("from");
		} else {
			dateValue.put("from", OUTGOING_JSON_DATE_FORMAT.format(sinceDate));
		}
		dateValue.putNull("to");
		return node.toString();
	}

	/*
	 * Fetches all FCA filings submitted since a given date.
	 * @param sinceDate The date to fetch filings since.
	 * @return A list of FCA filings.
	 */
	public List<FcaFiling> fetchAllSinceDate(LocalDateTime sinceDate) {
		List<FcaFiling> filings = new ArrayList<>();
		int page = 1;
		boolean more = true;
		while (more) {
			LOG.info("Fetching page {} of FCA filings since {}", page, sinceDate);
			more = fetchPage(sinceDate, page, filings);
			page += 1;
		}
		return filings;
	}

	/*
	 * Fetches a page of FCA filings submitted since a given date.
	 * Returns true if there are more pages to fetch.
	 * @param sinceDate The date to fetch filings since.
	 * @param page The page number to fetch.
	 * @param filings The list to add fetched filings to.
	 * @return true if there are more pages to fetch.
	 */
	private boolean fetchPage(LocalDateTime sinceDate, int page, List<FcaFiling> filings) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		String body = buildJson(sinceDate, page);
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		ResponseEntity<String> response = restTemplate.exchange(
				this.searchApiUrl,
				HttpMethod.POST,
				entity,
				String.class
		);
		int processed = processPage(response, filings);
		return processed >= this.pageSize;
	}

	private LocalDateTime parseDate(String dateStr) {
		try {
			TemporalAccessor documentDateAccessor = INCOMING_JSON_DATE_FORMAT.parse(dateStr);
			return LocalDateTime.from(documentDateAccessor);
		} catch (DateTimeParseException e) {
			// document_date is sometimes YYYY-MM-DD, for example in the sample response in FCA.md.
			// https://github.com/Arelle/frc-codex/blob/27a87ba7f4cec53f0d9990391899415efacb2103/src/main/java/com/frc/codex/discovery/fca/FCA.md#L70
			TemporalAccessor documentDateAccessor = ISO_LOCAL_DATE.parse(dateStr);
			return LocalDate.from(documentDateAccessor).atStartOfDay();
		}
	}

	/*
	 * Processes a page of FCA filings.
	 * @param response The response to process.
	 * @param filings The list to add fetched filings to.
	 * @return The number of filings processed.
	 */
	private int processPage(ResponseEntity<String> response, List<FcaFiling> filings) {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root;
		try {
			root = mapper.readTree(response.getBody());
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		int processed = 0;
		for(JsonNode hit : root.get("hits").get("hits")) {
			JsonNode source = hit.get("_source");
			String downloadLink = source.get("download_link").asText();
			String sequenceId = source.get("seq_id").asText();
			String documentDateStr = source.get("document_date").asText();
			LocalDateTime documentDate = parseDate(documentDateStr);
			String submittedDateStr = source.get("submitted_date").asText();
			LocalDateTime submittedDate = parseDate(submittedDateStr);
			String companyName = source.get("company").asText();
			companyName = companyName.toUpperCase();
			String lei = source.get("lei").asText();
			String[] downloadLinkSplit = downloadLink.split("/");
			String filename = downloadLinkSplit[downloadLinkSplit.length - 1];
			String downloadUrl = this.dataApiBaseUrl + downloadLink;
			String infoUrl = this.dataApiBaseUrl + source.get("html_link").asText();
			filings.add(new FcaFiling(
					companyName,
					documentDate,
					downloadUrl,
					filename,
					infoUrl,
					lei,
					sequenceId,
					submittedDate
			));
			processed += 1;
		}
		return processed;
	}
}
