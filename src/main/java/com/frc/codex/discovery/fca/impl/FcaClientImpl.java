package com.frc.codex.discovery.fca.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
	private static final SimpleDateFormat JSON_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final Logger LOG = LoggerFactory.getLogger(FcaClientImpl.class);
	private final String dataApiBaseUrl;
	private final int pageSize;
	private final RestTemplate restTemplate;
	private final String searchApiUrl;

	public FcaClientImpl(FilingIndexProperties properties) {
		this.dataApiBaseUrl = properties.fcaDataApiBaseUrl();
		this.pageSize = 1000;
		this.restTemplate = new RestTemplate();
		this.searchApiUrl = properties.fcaSearchApiUrl();
	}

	private String buildJson(Date sinceDate, int page) {
		/*
		Builds JSON payload string in the following format:
		{
            "from": this.pageSize * (page-1),
            "size": this.pageSize,
            "sort": "submitted_date",
            "sortorder": "desc",
            "criteriaObj": {
                "criteria": [
                    {
                        "name": "tag_esef",
                        "value": [
                            "Tagged"
                        ]
                    }
                ],
                "dateCriteria": [
                    {
                        "name": "submitted_date",
                        "value": {
                            "from": submittedAfterDate
                            "to": null,
                        }
                    }
                ]
            }
        }
	 	*/
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
		dateValue.put("from", JSON_DATE_FORMAT.format(sinceDate));
		dateValue.putNull("to");
		return node.toString();
	}

	public List<FcaFiling> fetchAllSinceDate(Date sinceDate) {
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

	private boolean fetchPage(Date sinceDate, int page, List<FcaFiling> filings) {
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
		return processPage(response, filings);
	}

	private boolean processPage(ResponseEntity<String> response, List<FcaFiling> filings) {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root;
		try {
			root = mapper.readTree(response.getBody());
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		boolean foundAny = false;
		for(JsonNode hit : root.get("hits").get("hits")) {
			JsonNode source = hit.get("_source");
			String downloadLink = source.get("download_link").asText();
			String sequenceId = source.get("seq_id").asText();
			String submittedDate = source.get("submitted_date").asText();
			String[] downloadLinkSplit = downloadLink.split("/");
			String filename = downloadLinkSplit[downloadLinkSplit.length - 1];
			String downloadUrl = this.dataApiBaseUrl + downloadLink;
			String infoUrl = this.dataApiBaseUrl + source.get("html_link").asText();
			filings.add(new FcaFiling(
					filename,
					downloadUrl,
					infoUrl,
					sequenceId,
					submittedDate
			));
			foundAny = true;
		}
		return foundAny;
	}
}
