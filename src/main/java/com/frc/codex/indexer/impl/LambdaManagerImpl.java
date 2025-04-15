package com.frc.codex.indexer.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frc.codex.properties.FilingIndexProperties;
import com.frc.codex.indexer.LambdaManager;
import com.frc.codex.model.FilingPayload;
import com.frc.codex.model.FilingResultRequest;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

@Component
public class LambdaManagerImpl implements LambdaManager {
	private static final Logger LOG = LoggerFactory.getLogger(LambdaManagerImpl.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private final LambdaAsyncClient client;
	private final FilingIndexProperties properties;

	public LambdaManagerImpl(FilingIndexProperties properties) {
		this.properties = properties;
		this.client = LambdaAsyncClient.builder()
				.httpClientBuilder(NettyNioAsyncHttpClient.builder()
						.maxConcurrency(100)
						.readTimeout(Duration.ofSeconds(properties.awsLambdaTimeoutSeconds()))
						.writeTimeout(Duration.ofSeconds(properties.awsLambdaTimeoutSeconds()))
						.connectionTimeout(Duration.ofSeconds(properties.awsLambdaTimeoutSeconds()))
				)
				.overrideConfiguration(ClientOverrideConfiguration.builder()
						.apiCallAttemptTimeout(Duration.ofSeconds(properties.awsLambdaTimeoutSeconds()))
						.apiCallTimeout(Duration.ofSeconds(properties.awsLambdaTimeoutSeconds()))
						.build())
				.build();
	}

	private SdkBytes serializePayload(FilingPayload payload) {
		return SdkBytes.fromString(payload.toString(), StandardCharsets.UTF_8);
	}

	public CompletableFuture<InvokeResponse> invokeAsync(FilingPayload payload) {
		InvokeRequest request = InvokeRequest.builder()
				.functionName(properties.awsLambdaFunctionName())
				.payload(serializePayload(payload))
				.build();
		return client.invoke(request);
	}

	public InvokeResponse invokeSync(FilingPayload payload) throws ExecutionException, InterruptedException {
		if (properties.isAws()) {
			CompletableFuture<InvokeResponse> future = invokeAsync(payload);
			return future.get();
		} else {
			// Local Lambda container can't currently handle concurrent requests (as it's meant to emulate
			// a single Lambda container which is never tasked with concurrent requests).
			// This may be improved upon in the future:
			// https://github.com/aws/aws-lambda-runtime-interface-emulator/issues/97
			synchronized (this) {
				LOG.info("Invoking lambda function...");
				try {
					CompletableFuture<InvokeResponse> future = invokeAsync(payload);
					return future.get();
				} finally {
					LOG.info("Completed lambda function invocation.");
				}
			}
		}
	}

	public FilingResultRequest parseResult(InvokeResponse invokeResponse) {
		JsonNode root;
		byte[] byteArray;
		try {
			byteArray = invokeResponse.payload().asByteArray();
			root = OBJECT_MAPPER.readTree(byteArray);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			return FilingResultRequest.builder()
					.json(root)
					.build();
		} catch (Exception e) {
			String str = new String(byteArray, StandardCharsets.UTF_8);
			LOG.error("Failed to parse result: {}", str, e);
			return null;
		}
	}
}
