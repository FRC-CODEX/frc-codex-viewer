package com.frc.codex.clients.companieshouse;

public class FilingUrl {
	private final FilingFormat filingFormat;
	private final String downloadUrl;

	public FilingUrl(FilingFormat filingFormat, String downloadUrl) {
		this.filingFormat = filingFormat;
		this.downloadUrl = downloadUrl;
	}

	public FilingFormat getFilingFormat() {
		return filingFormat;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}
}
