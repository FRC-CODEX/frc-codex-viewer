package com.frc.codex.discovery.fca;

public record FcaFiling(
		String filename,
		String downloadUrl,
		String infoUrl,
		String sequenceId,
		String submittedDate
) {
}
