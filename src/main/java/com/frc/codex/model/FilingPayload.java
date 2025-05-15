package com.frc.codex.model;

import java.util.UUID;

public record FilingPayload (
		UUID filingId,
		String filingUrl,
		String format,
		String registryCode) {

	public String toString() {
		return String.format(
				"{\"filing_id\": \"%s\", \"filing_url\": \"%s\", \"format\": \"%s\", \"registry_code\": \"%s\"}",
				filingId.toString(), filingUrl, format, registryCode
		);
	}
}
