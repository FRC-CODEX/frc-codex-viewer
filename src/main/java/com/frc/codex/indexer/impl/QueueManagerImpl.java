package com.frc.codex.indexer.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.frc.codex.FilingIndexProperties;
import com.frc.codex.indexer.QueueManager;
import com.frc.codex.model.Filing;
import com.frc.codex.model.FilingResultRequest;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Component
public class QueueManagerImpl implements QueueManager {
	private static final Logger LOG = LoggerFactory.getLogger(IndexerImpl.class);
	private static final DateTimeFormatter MESSAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private final FilingIndexProperties properties;

	public QueueManagerImpl(FilingIndexProperties properties) {
		this.properties = properties;
	}

	/*
	 * Adds a list of filings to the jobs queue, invoking a callback on each filing.
	 */
	public void addJobs(List<Filing> filings, Consumer<Filing> callback) {
		try (SqsClient sqsClient = getSqsClient()) {
			String queueUrl = sqsClient
					.getQueueUrl(builder -> builder.queueName(properties.sqsJobsQueueName()))
					.queueUrl();
			for (Filing filing : filings) {
				String filingId = filing.getFilingId().toString();
				LOG.info("Queueing filing: {}", filingId);
				MessageAttributeValue downloadUrl = MessageAttributeValue.builder()
						.stringValue(filing.getDownloadUrl())
						.dataType("String")
						.build();
				MessageAttributeValue registryCode = MessageAttributeValue.builder()
						.stringValue(filing.getRegistryCode())
						.dataType("String")
						.build();
				SendMessageRequest request = SendMessageRequest.builder()
						.queueUrl(queueUrl)
						.messageBody(filing.getFilingId().toString())
						.messageAttributes(Map.of(
								"DownloadUrl", downloadUrl,
								"RegistryCode", registryCode
						))
						.build();
				SendMessageResponse response = sqsClient.sendMessage(request);
				if (response.sdkHttpResponse().isSuccessful()) {
					callback.accept(filing);
					LOG.info("Queued filing: {}", filingId);
				} else {
					LOG.error(
							"Failed to queue filing {}: ({}) {}",
							filingId,
							response.sdkHttpResponse().statusCode(),
							response.sdkHttpResponse().statusText()
					);
				}
			}
		}
	}

	private SqsClient getSqsClient() {
		return SqsClient.builder()
				.build();
	}

	public String getStatus() {
		StringBuilder status = new StringBuilder();
		for (String queueName : List.of(properties.sqsJobsQueueName(), properties.sqsResultsQueueName())) {
			try (SqsClient sqsClient = getSqsClient()) {
				status.append(queueName).append(":\n");
				String queueUrl = sqsClient
						.getQueueUrl(builder -> builder.queueName(queueName))
						.queueUrl();
				GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
						.queueUrl(queueUrl)
						.attributeNamesWithStrings(
								"ApproximateNumberOfMessages",
								"ApproximateNumberOfMessagesDelayed",
								"ApproximateNumberOfMessagesNotVisible",
								"LastModifiedTimestamp"
						)
						.build();
				sqsClient.getQueueAttributes(request)
						.attributes()
						.forEach((key, value) -> status
								.append("\t")
								.append(key)
								.append(": ")
								.append(value)
								.append("\n"));
			}
		}
		return status.toString();
	}

	private String getMessageAttribute(Message message, String key) {
		MessageAttributeValue attribute = message.messageAttributes().get(key);
		return attribute == null ? null : attribute.stringValue();
	}

	/*
	 * Retrieves messages from the results queue.
	 */
	public void processResults(Function<FilingResultRequest, Boolean> callback) {
		try (SqsClient sqsClient = getSqsClient()) {
			String queueUrl = sqsClient
					.getQueueUrl(builder -> builder.queueName(properties.sqsResultsQueueName()))
					.queueUrl();
			ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
					.queueUrl(queueUrl)
					.messageAttributeNames("All")
					.maxNumberOfMessages(10)
					.waitTimeSeconds(5)
					.build();
			List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
			LOG.info("Received results messages: {}", messages.size());
			for(Message message : messages) {
				boolean success = Objects.equals(getMessageAttribute(message, "Success"), "true");
				String error = null;
				String filename = null;
				String oimDirectory = null;
				String viewerEntrypoint = null;
				if (!success) {
					error = getMessageAttribute(message, "Error");
				} else {
					filename = getMessageAttribute(message, "Filename");
					oimDirectory = getMessageAttribute(message, "OimDirectory");
					viewerEntrypoint = getMessageAttribute(message, "ViewerEntrypoint");
				}
				String companyName = getMessageAttribute(message, "CompanyName");
				companyName = companyName == null ? null : companyName.toUpperCase();
				String companyNumber = getMessageAttribute(message, "CompanyNumber");
				String documentDateStr = getMessageAttribute(message, "DocumentDate");
				LocalDateTime documentDate = null;
				if (documentDateStr != null) {
					documentDate = LocalDate.parse(documentDateStr, MESSAGE_DATE_FORMAT).atStartOfDay();
				}
				UUID filingId = UUID.fromString(Objects.requireNonNull(getMessageAttribute(message, "FilingId")));
				String logs = message.body();
				FilingResultRequest filingResultRequest = FilingResultRequest.builder()
						.companyName(companyName)
						.companyNumber(companyNumber)
						.documentDate(documentDate)
						.error(error)
						.filingId(filingId)
						.logs(logs)
						.filename(filename)
						.oimDirectory(oimDirectory)
						.stubViewerUrl(viewerEntrypoint)
						.success(success)
						.build();
				if (callback.apply(filingResultRequest)) {
					sqsClient.deleteMessage(builder -> builder
							.queueUrl(queueUrl)
							.receiptHandle(message.receiptHandle()));
				}
			}
		}
	}
}
