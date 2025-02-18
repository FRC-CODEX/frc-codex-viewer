package com.frc.codex.clients.companieshouse.impl;

import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CompaniesHouseStreamClient {
	private final Logger LOG = LoggerFactory.getLogger(CompaniesHouseStreamClient.class);
	private final String apiKey;
	private final String baseUrl;

	public CompaniesHouseStreamClient(String baseUrl, String apiKey) {
		this.apiKey = requireNonNull(apiKey);
		this.baseUrl = requireNonNull(baseUrl);
	}

	private void stream(String url, Function<String, Boolean> callback) throws IOException {
		URL fullUrl = URI.create(baseUrl + url).toURL();
		HttpURLConnection conn = (HttpURLConnection) fullUrl.openConnection();
		conn.setRequestMethod("GET");
		// https://developer-specs.company-information.service.gov.uk/streaming-api/guides/authentication
		String encodedApiKey = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
		conn.setRequestProperty("Authorization", "Basic " + encodedApiKey);

		BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			if (!callback.apply(line)) {
				break;
			}
		}
		reader.close();
	}

	public void streamFilings(Long timepoint, Function<String, Boolean> callback) throws IOException {
		if (timepoint == null) {
			stream("/filings", callback);
		} else {
			try {
				stream("/filings?timepoint=" + timepoint, callback);
			} catch (IOException e) {
				if (e.getMessage().contains("HTTP response code: 416")) {
					// 416 response code most likely indicates the timepoint is too far in the past.
					// https://developer-specs.company-information.service.gov.uk/streaming-api/guides/overview#timepoints
					// Retry without timepoint specified.
					// This may lead to filings being missed but is preferable of falling behind indefinitely.
					LOG.warn("Timepoint {} is too far in the past, retrying without timepoint.", timepoint);
					stream("/filings", callback);
				} else {
					throw e;
				}
			}
		}
	}
}
