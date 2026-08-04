package com.frc.codex.indexer.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.clients.companieshouse.CompaniesHouseStreamListener;
import com.frc.codex.database.DatabaseManager;
import com.frc.codex.indexer.MetricManager;
import com.frc.codex.properties.FilingIndexProperties;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

@Component
public class MetricManagerImpl implements MetricManager {
	private static final Logger LOG = LoggerFactory.getLogger(MetricManagerImpl.class);
	private final CloudWatchClient client;
	private final CompaniesHouseClient companiesHouseClient;
	private final CompaniesHouseStreamListener companiesHouseStreamListener;
	private final DatabaseManager databaseManager;
	private final FilingIndexProperties properties;

	public MetricManagerImpl(
			CompaniesHouseClient companiesHouseClient,
			CompaniesHouseStreamListener companiesHouseStreamListener,
			DatabaseManager databaseManager,
			FilingIndexProperties properties
	) {
		this.companiesHouseClient = companiesHouseClient;
		this.companiesHouseStreamListener = companiesHouseStreamListener;
		this.databaseManager = databaseManager;
		this.properties = properties;
		this.client = CloudWatchClient.create();
	}

	public void uploadMetrics() {
		if (this.properties.metricNamespace() == null) {
			LOG.debug("No metric namespace configured, skipping metric upload.");
			return;
		}
		List<MetricDatum> metricData = new ArrayList<>();
		collect(metricData, "stream discovery delay", this::addStreamDiscoveryDelayMetricDatum);
		collect(metricData, "stream received events age", this::addStreamReceivedEventsAgeMetricDatum);
		collect(metricData, "stream events", this::addStreamEventsMetricDatum);
		if (metricData.isEmpty()) {
			LOG.debug("No metrics to upload, skipping metric upload.");
			return;
		}
		PutMetricDataRequest request = PutMetricDataRequest.builder()
				.namespace(this.properties.metricNamespace())
				.metricData(metricData)
				.build();
		PutMetricDataResponse response = this.client.putMetricData(request);
		if (!response.sdkHttpResponse().isSuccessful()) {
			LOG.error("Failed to upload metric: {} {}", response.sdkHttpResponse().statusCode(), response.sdkHttpResponse().statusText());
		}
	}

	private void collect(List<MetricDatum> metricData, String description, Consumer<List<MetricDatum>> collector) {
		try {
			collector.accept(metricData);
		} catch (RuntimeException e) {
			LOG.warn("Failed to collect the {} metric.", description, e);
		}
	}

	private void addMetricDatum(List<MetricDatum> metricData, String metricName, long value, StandardUnit unit) {
		metricData.add(MetricDatum.builder()
				.metricName(metricName)
				.value((double) value)
				.unit(unit)
				.build());
	}

	private void addStreamDiscoveryDelayMetricDatum(List<MetricDatum> metricData) {
		if (this.properties.streamDiscoveryDelayMetric() == null) {
			LOG.debug("No stream discovery delay metric name configured, skipping that metric.");
			return;
		}
		LocalDateTime latestStreamDiscoveredDate = this.databaseManager.getLatestStreamDiscoveredDate();
		long streamDiscoveryDelay = 0;
		if (latestStreamDiscoveredDate != null) {
			streamDiscoveryDelay = Duration.between(latestStreamDiscoveredDate, LocalDateTime.now()).toSeconds();
			if (streamDiscoveryDelay < 0) {
				streamDiscoveryDelay = 0;
			}
		}
		addMetricDatum(metricData, this.properties.streamDiscoveryDelayMetric(), streamDiscoveryDelay, StandardUnit.SECONDS);
	}

	private void addStreamEventsMetricDatum(List<MetricDatum> metricData) {
		if (this.properties.streamEventsMetric() == null) {
			LOG.debug("No stream events metric name configured, skipping that metric.");
			return;
		}
		addMetricDatum(metricData, this.properties.streamEventsMetric(), this.databaseManager.getStreamEventsCount(), StandardUnit.COUNT);
	}

	private void addStreamReceivedEventsAgeMetricDatum(List<MetricDatum> metricData) {
		if (this.properties.streamReceivedEventsAgeMetric() == null) {
			LOG.debug("No stream received events age metric name configured, skipping that metric.");
			return;
		}
		if (!this.companiesHouseClient.isEnabled()) {
			LOG.debug("Companies House client is disabled, skipping stream received events age metric.");
			return;
		}
		Instant lastEventReceivedDate = this.companiesHouseStreamListener.getLastEventReceivedDate();
		if (lastEventReceivedDate == null) {
			LOG.debug("No stream event receipt date available, skipping stream received events age metric.");
			return;
		}
		long receivedEventsAge = Duration.between(lastEventReceivedDate, Instant.now()).toSeconds();
		if (receivedEventsAge < 0) {
			receivedEventsAge = 0;
		}
		addMetricDatum(metricData, this.properties.streamReceivedEventsAgeMetric(), receivedEventsAge, StandardUnit.SECONDS);
	}

}
