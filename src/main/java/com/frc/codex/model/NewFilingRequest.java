package com.frc.codex.model;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;

public class NewFilingRequest {
	private final String companyName;
	private final String companyNumber;
	private final LocalDateTime documentDate;
	private final String registryCode;
	private final String downloadUrl;
	private final String externalFilingId;
	private final LocalDateTime filingDate;
	private final String format;
	private final String resourceId;
	private final Long streamTimepoint;
	private final String externalViewUrl;

	private NewFilingRequest(Builder builder) {
		this.companyName = builder.companyName;
		this.companyNumber = requireNonNull(builder.companyNumber);
		this.documentDate = builder.documentDate;
		this.downloadUrl = requireNonNull(builder.downloadUrl);
		this.externalFilingId = requireNonNull(builder.externalFilingId);
		this.externalViewUrl = requireNonNull(builder.externalViewUrl);
		this.filingDate = requireNonNull(builder.filingDate);
		this.format = requireNonNull(builder.format);
		this.registryCode = requireNonNull(builder.registryCode);
		this.resourceId = builder.resourceId;
		this.streamTimepoint = builder.streamTimepoint;
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

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public String getExternalFilingId() {
		return externalFilingId;
	}

	public String getExternalViewUrl() {
		return externalViewUrl;
	}

	public LocalDateTime getFilingDate() {
		return filingDate;
	}

	public String getFormat() {
		return format;
	}

	public String getRegistryCode() {
		return registryCode;
	}

	public String getResourceId() {
		return resourceId;
	}

	public Long getStreamTimepoint() {
		return streamTimepoint;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String companyName;
		private String companyNumber;
		private LocalDateTime documentDate;
		private String registryCode;
		private String downloadUrl;
		private String externalFilingId;
		private LocalDateTime filingDate;
		private String format;
		private String resourceId;
		private Long streamTimepoint;
		private String externalViewUrl;

		public NewFilingRequest build() {
			return new NewFilingRequest(this);
		}

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

		public Builder downloadUrl(String downloadUrl) {
			this.downloadUrl = downloadUrl;
			return this;
		}

		public Builder externalFilingId(String externalFilingId) {
			this.externalFilingId = externalFilingId;
			return this;
		}

		public Builder externalViewUrl(String externalViewUrl) {
			this.externalViewUrl = externalViewUrl;
			return this;
		}

		public Builder filingDate(LocalDateTime filingDate) {
			this.filingDate = filingDate;
			return this;
		}

		public Builder format(String format) {
			this.format = format;
			return this;
		}

		public Builder registryCode(String registryCode) {
			this.registryCode = registryCode;
			return this;
		}

		public Builder resourceId(String resourceId) {
			this.resourceId = resourceId;
			return this;
		}

		public Builder streamTimepoint(Long streamTimepoint) {
			this.streamTimepoint = streamTimepoint;
			return this;
		}

		public String getRegistryCode() {
			return registryCode;
		}

		public String getExternalFilingId() {
			return externalFilingId;
		}

		public String getDownloadUrl() {
			return downloadUrl;
		}

		public String getCompanyNumber() {
			return companyNumber;
		}

		public String getResourceId() {
			return resourceId;
		}
	}
}
