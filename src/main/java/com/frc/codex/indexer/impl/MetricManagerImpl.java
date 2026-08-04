package com.frc.codex.indexer.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
	private final DatabaseManager databaseManager;
	private final FilingIndexProperties properties;

	public MetricManagerImpl(DatabaseManager databaseManager, FilingIndexProperties properties) {
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
		addStreamDiscoveryDelayMetricDatum(metricData);
		addStreamEventsMetricDatum(metricData);
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

}
