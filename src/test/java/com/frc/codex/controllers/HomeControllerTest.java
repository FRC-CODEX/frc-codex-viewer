package com.frc.codex.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class HomeControllerTest {

	@Autowired
	private RestTestClient restClient;

	@Test
	void healthPage() {
		restClient.get().uri("/health")
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	void indexPage() {
		restClient.get().uri("/")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.value(body -> assertThat(body).contains("UK iXBRL Viewer"));
	}

	@Test
	void notFoundPage() {
		restClient.get().uri("/nonexistent")
			.exchange()
			.expectStatus().isNotFound();
	}

}
