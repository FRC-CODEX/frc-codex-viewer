package com.frc.codex.model;

public enum FilingStatus {
	PENDING ("pending"),
	QUEUED ("queued"),
	COMPLETED ("completed"),
	DELETED ("deleted"),
	FAILED ("failed");

	private final String name;

	private FilingStatus(String s) {
		name = s;
	}

	public String toString() {
		return this.name;
	}

	public static boolean isProcessingAllowed(String name) {
		return !DELETED.toString().equals(name);
	}
	public static boolean isViewerUnavailable(String name) {
		return FAILED.toString().equals(name) || DELETED.toString().equals(name);
	}
}
