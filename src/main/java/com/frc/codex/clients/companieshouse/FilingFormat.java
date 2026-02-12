package com.frc.codex.clients.companieshouse;

public enum FilingFormat {
	// "application/xml" is reported for some older CH filings, but "application/xhtml+xml" is used for new filings.
	// Included here and mapped to the same "xhtml" format value to ensure equivalent processing logic.
	XML ("xhtml", "application/xml", 1),
	XHTML ("xhtml", "application/xhtml+xml", 2),
	ZIP ("zip", "application/zip", 3);

	private final String format;
	private final String contentType;
	private final int priority;


	private FilingFormat(String format, String contentType, int priority) {
		this.format = format;
		this.contentType = contentType;
		this.priority = priority;
	}

	public String getFormat() {
		return format;
	}

	public String getContentType() {
		return contentType;
	}

	public int getPriority() {
		return priority;
	}
}
