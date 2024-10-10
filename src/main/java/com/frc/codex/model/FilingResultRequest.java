package com.frc.codex.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public class FilingResultRequest {
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private final String companyName;
	private final String companyNumber;
	private final LocalDateTime documentDate;
	private final String error;
	private final UUID filingId;
	private final String logs;
	private final String stubViewerUrl;
	private final boolean success;

	private FilingResultRequest(Builder builder) {
		this.companyName = builder.companyName;
		this.companyNumber = builder.companyNumber;
		this.documentDate = builder.documentDate;
		this.error = builder.error;
		this.filingId = builder.filingId;
		this.logs = builder.logs;
		this.stubViewerUrl = builder.stubViewerUrl;
		this.success = builder.success;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getCompanyName() {
		return companyName;
	}

	public String getCompanyNumber() {
		return companyNumber;
	}

	public LocalDateTime getDocumentDate() {
		return documentDate;
	}

	public String getError() {
		return error;
	}

	public UUID getFilingId() {
		return filingId;
	}

	public String getLogs() {
		return logs;
	}

	public String getStubViewerUrl() {
		return stubViewerUrl;
	}

	public FilingStatus getStatus() {
		return success ? FilingStatus.COMPLETED : FilingStatus.FAILED;
	}

	public static class Builder {
		private String companyName;
		private String companyNumber;
		private LocalDateTime documentDate;
		private String error;
		private UUID filingId;
		private String logs;
		private String stubViewerUrl;
		private boolean success;

		public Builder companyName(String companyName) {
			this.companyName = companyName;
			return this;
		}

		public Builder companyNumber(String companyNumber) {
			this.companyNumber = companyNumber;
			return this;
		}

		public Builder documentDate(LocalDateTime documentDate) {
			this.documentDate = documentDate;
			return this;
		}

		public Builder documentDate(String documentDate) {
			if (documentDate == null) {
				this.documentDate = null;
			} else {
				this.documentDate = LocalDate.parse(documentDate, DATE_FORMAT).atStartOfDay();
			}
			return this;
		}

		public Builder error(String error) {
			this.error = error;
			return this;
		}

		public Builder filingId(UUID filingId) {
			this.filingId = filingId;
			return this;
		}

		public Builder json(JsonNode jsonNode) {
			boolean success = Objects.equals(jsonNode.get("Success").asText(), "true");
			String error = null;
			String viewerEntrypoint = null;
			if (!success) {
				error = jsonNode.get("Error").asText();
			} else {
				viewerEntrypoint = jsonNode.get("ViewerEntrypoint").asText();
			}
			String companyName = jsonNode.get("CompanyName").asText();
			companyName = companyName == null ? null : companyName.toUpperCase();
			String companyNumber = jsonNode.get("CompanyNumber").asText();
			String documentDate = null;
			if (jsonNode.has("DocumentDate")) {
				documentDate = jsonNode.get("DocumentDate").asText();
			}
			UUID filingId = UUID.fromString(Objects.requireNonNull(jsonNode.get("FilingId").asText()));
			String logs = jsonNode.get("Logs").asText();
			return this
					.companyName(companyName)
					.companyNumber(companyNumber)
					.documentDate(documentDate)
					.error(error)
					.filingId(filingId)
					.logs(logs)
					.stubViewerUrl(viewerEntrypoint)
					.success(success);
		}

		public Builder logs(String logs) {
			this.logs = logs;
			return this;
		}

		public Builder stubViewerUrl(String stubViewerUrl) {
			this.stubViewerUrl = stubViewerUrl;
			return this;
		}

		public Builder success(boolean success) {
			this.success = success;
			return this;
		}

		public FilingResultRequest build() {
			return new FilingResultRequest(this);
		}
	}
}
