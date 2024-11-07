package com.frc.codex.discovery.companieshouse.impl;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.frc.codex.discovery.companieshouse.CompaniesHouseRateLimiter;
import com.frc.codex.discovery.companieshouse.RateLimitException;

public class CompaniesHouseHttpClient {
	private final Logger LOG = LoggerFactory.getLogger(CompaniesHouseHttpClient.class);
	private final CompaniesHouseRateLimiter rateLimiter;
	private final String baseUrl;
	private final HttpEntity<String> getJsonEntity;
	private final RestTemplate restTemplate;

	public CompaniesHouseHttpClient(
		CompaniesHouseRateLimiter rateLimiter,
		RestTemplate restTemplate,
		String baseUrl,
		String apiKey) {
		this.rateLimiter = requireNonNull(rateLimiter);
		this.baseUrl = requireNonNull(baseUrl);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		// https://developer-specs.company-information.service.gov.uk/guides/authorisation
		headers.setBasicAuth(requireNonNull(apiKey), "");
		this.getJsonEntity = new HttpEntity<>("parameters", headers);
		this.restTemplate = requireNonNull(restTemplate);
	}

	public String get(String relativeUrl) {
		String url = baseUrl + relativeUrl;
		LOG.info("CH API GET: {}", url);
		if (!rateLimiter.isHealthy()) {
			throw new RateLimitException("Companies House rate limiter state is unhealthy.");
		}
		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(
					url,
					HttpMethod.GET,
					this.getJsonEntity,
					String.class
			);
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatusCode.valueOf(429)) {
				rateLimiter.notifyRejection();
				throw new RateLimitException("Rate limit exceeded.", e);
			}
			throw e;
		}
		rateLimiter.updateLimits(response, url);
		return response.getBody();
	}
}
