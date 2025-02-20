package com.frc.codex.model;

import java.sql.Timestamp;
import java.util.UUID;

public class StreamEvent {
	private final UUID streamEventId;
	private final Timestamp createdDate;
	private final String json;

	public StreamEvent(Builder b) {
		this.streamEventId = b.streamEventId;
		this.createdDate = b.createdDate;
		this.json = b.json;
	}

	public UUID getStreamEventId() {
		return streamEventId;
	}

	public Timestamp getCreatedDate() {
		return createdDate;
	}

	public String getJson() {
		return json;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private UUID streamEventId;
		private Timestamp createdDate;
		private String json;

		public StreamEvent build() {
			return new StreamEvent(this);
		}

		public Builder streamEventId(String streamEventId) {
			this.streamEventId = UUID.fromString(streamEventId);
			return this;
		}

		public Builder createdDate(Timestamp createdDate) {
			this.createdDate = createdDate;
			return this;
		}

		public Builder json(String json) {
			this.json = json;
			return this;
		}
	}
}
