package com.frc.codex.model;

public enum ArchiveType {
	ARCHIVE ("archive", false),
	DAILY ("daily", true),
	MONTHLY ("monthly", false);

	private final String code;
	private final boolean resetsCompany;


	private ArchiveType(String code, boolean resetsCompany) {
		this.code = code;
		this.resetsCompany = resetsCompany;
	}

	public String getCode() {
		return this.code;
	}

	public boolean isResetsCompany() {
		return this.resetsCompany;
	}
}
