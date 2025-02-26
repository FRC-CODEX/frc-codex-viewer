package com.frc.codex.model;

import static java.util.Objects.requireNonNull;

import java.util.List;

public class SearchFilingsResult {
	private final List<Filing> filings;
	private final int lastPageNumber;
	private final int maximumPageNumber;
	private final int pageNumber;
	private final int pageSize;
	private final long totalFilings;

	private SearchFilingsResult(Builder b) {
		this.filings = b.filings;
		this.maximumPageNumber = b.maximumPageNumber;
		this.pageNumber = b.pageNumber;
		this.pageSize = b.pageSize;
		this.totalFilings = b.totalFilings;

		this.lastPageNumber = (int) Math.min(maximumPageNumber, totalFilings / pageSize + 1);
	}

	public List<Filing> getFilings() {
		return filings;
	}

	public int getLastPageNumber() {
		return lastPageNumber;
	}

	public int getMaximumPageNumber() {
		return maximumPageNumber;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public int getPageSize() {
		return pageSize;
	}

	public long getTotalFilings() {
		return totalFilings;
	}

	public boolean isFirstPage() {
		return getPageNumber() <= 1;
	}

	public boolean isLastPage() {
		return getPageNumber() >= getLastPageNumber();
	}

	public boolean isMaximumResultsReturned() {
		return totalFilings >= (long) maximumPageNumber * pageSize;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private List<Filing> filings;
		private int maximumPageNumber;
		private int pageNumber;
		private int pageSize;
		private long totalFilings;

		public SearchFilingsResult build() {
			return new SearchFilingsResult(this);
		}

		public SearchFilingsResult.Builder filings(List<Filing> filings) {
			this.filings = filings;
			return this;
		}

		public SearchFilingsResult.Builder maximumPageNumber(int maximumPageNumber) {
			this.maximumPageNumber = maximumPageNumber;
			return this;
		}

		public SearchFilingsResult.Builder pageNumber(int pageNumber) {
			this.pageNumber = pageNumber;
			return this;
		}

		public SearchFilingsResult.Builder pageSize(int pageSize) {
			this.pageSize = pageSize;
			return this;
		}

		public SearchFilingsResult.Builder totalFilings(long totalFilings) {
			this.totalFilings = totalFilings;
			return this;
		}
	}
}
