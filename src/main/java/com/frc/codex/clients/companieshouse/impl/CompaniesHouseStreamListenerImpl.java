package com.frc.codex.clients.companieshouse.impl;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import jakarta.annotation.PostConstruct;

import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseStreamListener;
import com.frc.codex.clients.companieshouse.RateLimitException;
import com.frc.codex.database.DatabaseManager;

@Component
public class CompaniesHouseStreamListenerImpl implements CompaniesHouseStreamListener {
	private static final Logger LOG = LoggerFactory.getLogger(CompaniesHouseStreamListenerImpl.class);
	private static final Duration RECEIPT_PERSIST_INTERVAL = Duration.ofMinutes(1);
	private final CompaniesHouseClient companiesHouseClient;
	private final DatabaseManager databaseManager;
	private int companiesHouseSessionEventCount;
	private Date companiesHouseStreamLastOpenedDate;
	private volatile Instant lastEventReceivedDate;
	private Instant lastPersistedEventReceivedDate;
	private final Pattern timepointPattern;

	public CompaniesHouseStreamListenerImpl(
			CompaniesHouseClient companiesHouseClient,
			DatabaseManager databaseManager
	) {
		this.companiesHouseClient = companiesHouseClient;
		this.databaseManager = databaseManager;
		this.timepointPattern = Pattern.compile("\"timepoint\":(\\d+)");
	}

	public Instant getLastEventReceivedDate() {
		return lastEventReceivedDate;
	}

	@PostConstruct
	public void postConstruct() {
		try {
			Instant receivedDate = databaseManager.getLastStreamEventReceivedDate();
			lastEventReceivedDate = receivedDate == null ? Instant.now() : receivedDate;
		} catch (RuntimeException e) {
			LOG.warn("Could not read the last stream event receipt date. Its age will go unpublished.", e);
			lastEventReceivedDate = null;
		}
		lastPersistedEventReceivedDate = lastEventReceivedDate;
	}

	public String getStatus() {
		Instant receivedDate = lastEventReceivedDate;
		String lastEventReceived = receivedDate == null ? "unknown" : "%s (%s seconds ago)".formatted(
				receivedDate,
				Duration.between(receivedDate, Instant.now()).toSeconds()
		);
		return String.format("""
						Companies House Stream Listener:
						\tStream last opened: %s
						\tEvents discovered this session: %s
						\tLast event received: %s""",
				companiesHouseStreamLastOpenedDate,
				companiesHouseSessionEventCount,
				lastEventReceived
		);
	}

	private void recordEventReceived(Instant receivedDate) {
		lastEventReceivedDate = receivedDate;
		if (lastPersistedEventReceivedDate != null
				&& Duration.between(lastPersistedEventReceivedDate, receivedDate).compareTo(RECEIPT_PERSIST_INTERVAL) < 0) {
			return;
		}
		try {
			databaseManager.updateLastStreamEventReceivedDate(receivedDate);
			lastPersistedEventReceivedDate = receivedDate;
		} catch (RuntimeException e) {
			LOG.warn("Could not record the last stream event receipt date.", e);
		}
	}

	public boolean isHealthy() {
		return companiesHouseStreamLastOpenedDate != null;
	}

	public void run(Supplier<Boolean> continueCallback) {
		if (!continueCallback.get()) {
			return;
		}
		LOG.info("Starting Companies House stream listener at {}", System.currentTimeMillis() / 1000);
		Function<String, Boolean> callback = (String json) -> {
			Matcher matcher = timepointPattern.matcher(json);
			if (!matcher.find()) {
				LOG.warn("Timepoint pattern did not match stream event JSON: {}", json);
				return false;
			}
			long timepoint = Long.parseLong(matcher.group(1));
			databaseManager.createStreamEvent(timepoint, json);
			companiesHouseSessionEventCount++;
			recordEventReceived(Instant.now());
			return true; // Continue streaming
		};
		Long startTimepoint = databaseManager.getLatestStreamTimepoint(null);
		companiesHouseStreamLastOpenedDate = new Date();
		try {
			companiesHouseClient.streamFilings(startTimepoint, callback);
		} catch (RateLimitException e) {
			LOG.warn("Rate limit exceeded while streaming CH filings. Resuming later.", e);
		} catch (HttpStatusCodeException e) {
			if (e.getStatusCode().is5xxServerError()) {
				LOG.warn("Companies House API responded with a 5xx server error.", e);
				return;
			}
			throw e;
		} catch (IOException | InterruptedException e) {
			if (e.getMessage().contains("Premature EOF")) {
				LOG.info("Companies house stream closed: {}", e.getMessage());
				return;
			}
			LOG.error("Companies House stream closed with an exception.", e);
		}
	}
}
