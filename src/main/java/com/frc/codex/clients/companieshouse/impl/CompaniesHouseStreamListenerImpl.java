package com.frc.codex.clients.companieshouse.impl;

import java.io.IOException;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseStreamListener;
import com.frc.codex.clients.companieshouse.RateLimitException;
import com.frc.codex.database.DatabaseManager;

@Component
public class CompaniesHouseStreamListenerImpl implements CompaniesHouseStreamListener {
	private static final Logger LOG = LoggerFactory.getLogger(CompaniesHouseStreamListenerImpl.class);
	private final CompaniesHouseClient companiesHouseClient;
	private final DatabaseManager databaseManager;
	private int companiesHouseSessionEventCount;
	private Date companiesHouseStreamLastOpenedDate;
	private final Pattern timepointPattern;

	public CompaniesHouseStreamListenerImpl(
			CompaniesHouseClient companiesHouseClient,
			DatabaseManager databaseManager
	) {
		this.companiesHouseClient = companiesHouseClient;
		this.databaseManager = databaseManager;
		this.timepointPattern = Pattern.compile("\"timepoint\":(\\d+)");
	}

	public String getStatus() {
		return String.format("""
						Companies House Stream Listener:
						\tStream last opened: %s
						\tEvents discovered this session: %s""",
				companiesHouseStreamLastOpenedDate,
				companiesHouseSessionEventCount
		);
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
			return true; // Continue streaming
		};
		long startTimepoint = databaseManager.getLatestStreamTimepoint(null);
		companiesHouseStreamLastOpenedDate = new Date();
		try {
			companiesHouseClient.streamFilings(startTimepoint, callback);
		} catch (RateLimitException e) {
			LOG.warn("Rate limit exceeded while streaming CH filings. Resuming later.", e);
		} catch (IOException | InterruptedException e) {
			LOG.error("Companies House stream closed with an exception.", e);
		}
	}
}
