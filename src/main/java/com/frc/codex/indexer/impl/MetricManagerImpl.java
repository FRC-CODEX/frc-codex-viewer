package com.frc.codex.indexer.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
		List<MetricDatum> metricData = Stream.of(
						buildStreamDiscoveryDelayMetricDatum(),
						buildStreamEventsMetricDatum())
				.filter(Objects::nonNull)
				.toList();
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

	private MetricDatum buildStreamDiscoveryDelayMetricDatum() {
		if (this.properties.streamDiscoveryDelayMetric() == null) {
			LOG.debug("No stream discovery delay metric name configured, skipping metric upload.");
			return null;
		}
		LocalDateTime latestStreamDiscoveredDate = this.databaseManager.getLatestStreamDiscoveredDate();
		int streamDiscoveryDelay = 0;
		if (latestStreamDiscoveredDate != null) {
			streamDiscoveryDelay = (int) Duration.between(latestStreamDiscoveredDate, LocalDateTime.now()).toSeconds();
			if (streamDiscoveryDelay < 0) {
				streamDiscoveryDelay = 0;
			}
		}
		return MetricDatum.builder()
				.metricName(this.properties.streamDiscoveryDelayMetric())
				.value((double) streamDiscoveryDelay)
				.unit(StandardUnit.SECONDS)
				.build();
	}

	private MetricDatum buildStreamEventsMetricDatum() {
		if (this.properties.streamEventsMetric() == null) {
			LOG.debug("No stream events metric name configured, skipping metric upload.");
			return null;
		}
		long streamEventsCount = this.databaseManager.getStreamEventsCount();
		return MetricDatum.builder()
				.metricName(this.properties.streamEventsMetric())
				.value((double) streamEventsCount)
				.unit(StandardUnit.COUNT)
				.build();
	}

}
