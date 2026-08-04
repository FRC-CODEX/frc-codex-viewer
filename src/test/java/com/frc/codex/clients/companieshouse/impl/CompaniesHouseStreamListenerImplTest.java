package com.frc.codex.clients.companieshouse.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frc.codex.clients.companieshouse.CompaniesHouseClient;
import com.frc.codex.database.DatabaseManager;

@ExtendWith(MockitoExtension.class)
public class CompaniesHouseStreamListenerImplTest {

	@Mock
	private CompaniesHouseClient companiesHouseClient;

	@Mock
	private DatabaseManager databaseManager;

	private CompaniesHouseStreamListenerImpl listener;

	@BeforeEach
	public void setUp() {
		listener = new CompaniesHouseStreamListenerImpl(companiesHouseClient, databaseManager);
	}

	@Test
	public void postConstruct_resumesThePersistedReceiptDate() {
		Instant persisted = Instant.parse("2026-08-03T09:00:00Z");
		when(databaseManager.getLastStreamEventReceivedDate()).thenReturn(persisted);

		listener.postConstruct();

		assertEquals(persisted, listener.getLastEventReceivedDate());
	}

	@Test
	public void postConstruct_seedsFromNowWhenNothingWasEverReceived() {
		when(databaseManager.getLastStreamEventReceivedDate()).thenReturn(null);

		listener.postConstruct();

		Instant seeded = listener.getLastEventReceivedDate();
		assertNotNull(seeded);
		assertTrue(Duration.between(seeded, Instant.now()).toSeconds() < 60);
	}

	@Test
	public void postConstruct_publishesNoAgeWhenTheRecordCannotBeRead() {
		when(databaseManager.getLastStreamEventReceivedDate())
				.thenThrow(new RuntimeException("database unavailable"));

		listener.postConstruct();

		assertNull(listener.getLastEventReceivedDate());
		assertTrue(listener.getStatus().contains("Last event received: unknown"));
	}

	@Test
	public void run_persistsTheFirstReceiptThenThrottles() throws Exception {
		when(databaseManager.getLastStreamEventReceivedDate())
				.thenReturn(Instant.now().minus(Duration.ofHours(1)));
		listener.postConstruct();
		streamEvents(2);

		listener.run(() -> true);

		verify(databaseManager, times(2)).createStreamEvent(any(Long.class), any(String.class));
		verify(databaseManager, times(1)).updateLastStreamEventReceivedDate(any(Instant.class));
	}

	@Test
	public void run_retriesTheWriteWhenItFails() throws Exception {
		when(databaseManager.getLastStreamEventReceivedDate())
				.thenReturn(Instant.now().minus(Duration.ofHours(1)));
		listener.postConstruct();
		doThrow(new RuntimeException("database unavailable"))
				.when(databaseManager).updateLastStreamEventReceivedDate(any(Instant.class));
		streamEvents(2);

		listener.run(() -> true);

		verify(databaseManager, times(2)).updateLastStreamEventReceivedDate(any(Instant.class));
		assertNotNull(listener.getLastEventReceivedDate());
	}

	@Test
	public void run_recordsNoReceiptForAnEventWithoutATimepoint() throws Exception {
		when(databaseManager.getLastStreamEventReceivedDate())
				.thenReturn(Instant.now().minus(Duration.ofHours(1)));
		listener.postConstruct();
		Instant seeded = listener.getLastEventReceivedDate();
		streamJson(List.of("{\"resource_kind\":\"filing-history\"}"));

		listener.run(() -> true);

		verify(databaseManager, never()).createStreamEvent(any(Long.class), any(String.class));
		verify(databaseManager, never()).updateLastStreamEventReceivedDate(any(Instant.class));
		assertEquals(seeded, listener.getLastEventReceivedDate());
	}

	private void streamEvents(int count) throws Exception {
		streamJson(IntStream.rangeClosed(1, count)
				.mapToObj(i -> "{\"resource_kind\":\"filing-history\",\"event\":{\"timepoint\":%d}}".formatted(i))
				.toList());
	}

	private void streamJson(List<String> events) throws Exception {
		doAnswer(invocation -> {
			Function<String, Boolean> callback = invocation.getArgument(1);
			events.forEach(callback::apply);
			return null;
		}).when(companiesHouseClient).streamFilings(any(), any());
	}
}
