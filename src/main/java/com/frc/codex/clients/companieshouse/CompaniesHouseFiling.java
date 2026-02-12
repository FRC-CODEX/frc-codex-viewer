package com.frc.codex.clients.companieshouse;

import java.time.LocalDateTime;

public record CompaniesHouseFiling(
		LocalDateTime actionDate,
		String category,
		String companyNumber,
		LocalDateTime date,
		String eventType,
		String resourceId,
		String resourceKind,
		Long timepoint,
		String transactionId
) {
}
