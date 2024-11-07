package com.frc.codex.discovery.companieshouse.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frc.codex.RegistryCode;
import com.frc.codex.discovery.companieshouse.CompaniesHouseClient;
import com.frc.codex.discovery.companieshouse.CompaniesHouseCompany;
import com.frc.codex.discovery.companieshouse.CompaniesHouseConfig;
import com.frc.codex.discovery.companieshouse.CompaniesHouseFiling;
import com.frc.codex.discovery.companieshouse.CompaniesHouseRateLimiter;
import com.frc.codex.model.NewFilingRequest;

@Component
@Profile("application")
public class CompaniesHouseClientImpl implements CompaniesHouseClient {
	private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of("application/xml", "application/xhtml+xml");
	private static final DateTimeFormatter CH_JSON_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final Set<String> IGNORED_CONTENT_TYPES = Set.of("application/pdf", "application/json", "text/csv");
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private final Logger LOG = LoggerFactory.getLogger(CompaniesHouseClientImpl.class);
	private final CompaniesHouseConfig config;
	private final CompaniesHouseHttpClient document;
	private final boolean enabled;
	private final CompaniesHouseHttpClient information;
	public final CompaniesHouseStreamClient stream;
	private final HashSet<String> companiesHouseExcludeCategories;
	private final HashSet<String> companiesHouseIncludeCategories;

	public CompaniesHouseClientImpl(CompaniesHouseConfig config, CompaniesHouseRateLimiter rateLimiter) {
		this.config = requireNonNull(config);
		String restApiKey = config.restApiKey();
		String streamApiKey = config.streamApiKey();
		if (restApiKey != null && streamApiKey != null) {
			this.enabled = true;
			this.document = new CompaniesHouseHttpClient(rateLimiter, new RestTemplate(), config.documentApiBaseUrl(), config.restApiKey());
			this.information = new CompaniesHouseHttpClient(rateLimiter, new RestTemplate(), config.informationApiBaseUrl(), config.restApiKey());
			this.stream = new CompaniesHouseStreamClient(config.streamApiBaseUrl(), config.streamApiKey());
		} else {
			LOG.info("Companies House API key(s) not set. Client disabled.");
			this.enabled = false;
			this.document = null;
			this.information = null;
			this.stream = null;
		}
		// TODO: Make dynamic
		this.companiesHouseExcludeCategories = new HashSet<>(List.of(
				"address",
				"capital",
				"change-of-name",
				"confirmation-statement",
				"dissolution",
				"incorporation",
				"insolvency",
				"mortgage",
				"officers",
				"other",
				"persons-with-significant-controlxR",
				"resolution"
		));
		this.companiesHouseIncludeCategories = new HashSet<>(List.of(
				"accounts"
		));
	}

	public boolean filterCategory(String category) {
		if (category == null) {
			// Not clear what category being unset indicates, but probably safe to assume
			// it won't have IXBRL until the filing is categorized.
			return false;
		}
		if (companiesHouseExcludeCategories.contains(category)) {
			return false;
		}
		if (!companiesHouseIncludeCategories.contains(category)) {
			LOG.warn("Unknown filing category: {}", category);
		}
		return true;
	}

	public CompaniesHouseCompany getCompany(String companyNumber) throws JsonProcessingException {
		throwExceptionIfDisabled();
		String json = information.get("/company/" + companyNumber);
		JsonNode root = OBJECT_MAPPER.readTree(json);
		String companyName = root.get("company_name").asText();
		return CompaniesHouseCompany.builder()
				.companyName(companyName)
				.companyNumber(companyNumber)
				.build();
	}

	public Set<String> getCompanyFilingUrls(String companyNumber, String filingId) throws JsonProcessingException {
		throwExceptionIfDisabled();
		String json;
		try {
			json = information.get("/company/" + companyNumber + "/filing-history/" + filingId);
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				LOG.warn("Filing not found: companyNumber={} filingId={}", companyNumber, filingId, e);
				return Set.of();
			}
			throw e;
		}
		JsonNode node = OBJECT_MAPPER.readTree(json);
		return getCompanyFilingUrls(node);
	}

	public String getCompanyFilingHistory(String companyNumber, int itemsPerPage, int startIndex) {
		throwExceptionIfDisabled();
		return information.get("/company/" + companyNumber + "/filing-history?category=accounts&items_per_page=" + itemsPerPage + "&start_index=" + startIndex);
	}

	public List<NewFilingRequest> getCompanyFilings(String companyNumber, String companyName) throws JsonProcessingException {
		throwExceptionIfDisabled();
		List<NewFilingRequest> filings = new ArrayList<>();
		int index = 0;
		int itemsPerPage = 100;
		int totalItems = Integer.MAX_VALUE;
		while (index + itemsPerPage < totalItems) {
			String json = getCompanyFilingHistory(companyNumber, itemsPerPage, index);
			JsonNode node = OBJECT_MAPPER.readTree(json);
			JsonNode items = node.get("items");
			if (items == null) {
				break;
			}
			totalItems = node.get("total_count").asInt();
			for (JsonNode item : items) {
				String category = item.get("category").asText();
				if (!filterCategory(category)) {
					continue;
				}
				LocalDateTime filingDate = parseDate(item.get("date"));
				LocalDateTime documentDate = parseDate(item.get("action_date"));
				String externalFilingId = item.get("transaction_id").asText();
				Set<String> filingUrls = getCompanyFilingUrls(item);
				if (!filingUrls.isEmpty()) {
					// There are matching IXBRL filing URLs
					String downloadUrl = "https://find-and-update.company-information.service.gov.uk/company/"
							+ companyNumber + "/filing-history/" + externalFilingId
							+ "/document?format=xhtml&download=0";
					NewFilingRequest newFilingRequest = NewFilingRequest.builder()
						.companyName(companyName)
						.companyNumber(companyNumber)
						.documentDate(documentDate)
						.downloadUrl(downloadUrl)
						.externalFilingId(externalFilingId)
						.externalViewUrl(downloadUrl)
						.filingDate(filingDate)
						.registryCode(RegistryCode.COMPANIES_HOUSE.getCode())
						.build();
					filings.add(newFilingRequest);
				}
			}
			index += itemsPerPage;
		}
		return filings;
	}

	private Set<String> getCompanyFilingUrls(JsonNode node) throws JsonProcessingException {
		throwExceptionIfDisabled();
		Set<String> filingUrls = new HashSet<>();
		JsonNode links = node.get("links");
		if (links == null) {
			return filingUrls;
		}
		JsonNode documentMetadata = links.get("document_metadata");
		if (documentMetadata == null) {
			return filingUrls;
		}
		String documentMetadataUrl = documentMetadata.asText();
		String documentId = documentMetadataUrl.substring(documentMetadataUrl.lastIndexOf("/") + 1);
		String metadata = document.get("/document/" + documentId);
		JsonNode metadataNode = OBJECT_MAPPER.readTree(metadata);
		JsonNode resources = metadataNode.get("resources");
		if (resources != null) {
			for (Iterator<Map.Entry<String, JsonNode>> it = resources.fields(); it.hasNext(); ) {
				Map.Entry<String, JsonNode> field = it.next();
				String key = field.getKey();
				if (IGNORED_CONTENT_TYPES.contains(key)) {
					continue;
				}
				if (!ACCEPTED_CONTENT_TYPES.contains(key)) {
					LOG.error("Unexpected content type: {}", key);
					continue;
				}
				// `contentType` query parameter is only added to indicate which content type is available at the URL
				// It is not a functional use of the Companies House Documents API.
				String contentType = URLEncoder.encode(key, StandardCharsets.UTF_8);
				filingUrls.add(config.documentApiBaseUrl() + "/document/" + documentId + "/content?contentType=" + contentType);
			}
		}
		return filingUrls;
	}

	public CompaniesHouseFiling getFiling(String companyNumber, String filingId) throws JsonProcessingException {
		throwExceptionIfDisabled();
		String json = information.get("/company/" + companyNumber + "/filing-history/" + filingId);
		return parseFiling(json, companyNumber);
	}

	private LocalDateTime parseDate(JsonNode dateNode) {
		LocalDateTime date = null;
		if (dateNode != null) {
			String dateStr = dateNode.asText();
			try {
				date = LocalDate.parse(dateStr, CH_JSON_DATE_FORMAT).atStartOfDay();
			} catch (Exception e) {
				throw new RuntimeException("Failed to parse date: " + dateStr, e);
			}
		}
		return date;
	}

	public CompaniesHouseFiling parseFiling(String json, String companyNumber) throws JsonProcessingException {
		JsonNode data = OBJECT_MAPPER.readTree(json);

		// Note: `action_date` is not a documented field but appears to consistently represent
		// the document date for the filing.
		LocalDateTime actionDate = parseDate(data.get("action_date"));
		String category = data.get("category").asText();
		LocalDateTime date = parseDate(data.get("date"));
		String transactionId = data.get("transaction_id").asText();
		return new CompaniesHouseFiling(
				actionDate,
				category,
				companyNumber,
				date,
				null,
				transactionId,
				null,
				null,
				transactionId
		);
	}

	public CompaniesHouseFiling parseStreamedFiling(String json) throws JsonProcessingException {
		JsonNode filing = OBJECT_MAPPER.readTree(json);
		JsonNode data = filing.get("data");
		JsonNode event = filing.get("event");
		String resourceUri = filing.get("resource_uri").asText();
		String[] resourceUriSplit = resourceUri.split("/");

		// Note: `action_date` is not a documented field but appears to consistently represent
		// the document date for the filing.
		LocalDateTime actionDate = parseDate(data.get("action_date"));
		String category = null;
		if (data.has("category")) {
			category = data.get("category").asText();
		}
		String companyNumber = resourceUriSplit[2];
		LocalDateTime date = parseDate(data.get("date"));
		String eventType = event.get("type").asText();
		String resourceKind = filing.get("resource_kind").asText();
		String resourceId = filing.get("resource_id").asText();
		long timepoint = event.get("timepoint").asLong();
		String transactionId = data.get("transaction_id").asText();
		return new CompaniesHouseFiling(
				actionDate,
				category,
				companyNumber,
				date,
				eventType,
				resourceId,
				resourceKind,
				timepoint,
				transactionId
		);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void streamFilings(Long timepoint, Function<CompaniesHouseFiling, Boolean> callback) throws IOException {
		Function<String, Boolean> parseCallback = json -> {
			if (json == null || json.length() <= 1) {
				// The stream emits blank "heartbeat" lines.
				return true;
			}
			CompaniesHouseFiling filing;
			try {
				filing = parseStreamedFiling(json);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
			return callback.apply(filing);
		};
		throwExceptionIfDisabled();
		stream.streamFilings(timepoint, parseCallback);
	}

	private void throwExceptionIfDisabled() {
		if (!enabled) {
			throw new RuntimeException("Companies House API key(s) not set. Client disabled.");
		}
	}
}
