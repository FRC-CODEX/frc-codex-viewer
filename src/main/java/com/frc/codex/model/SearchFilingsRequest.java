package com.frc.codex.model;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.thymeleaf.util.StringUtils;

public class SearchFilingsRequest {
	private Boolean admin;
	private String searchText;
	private Integer minDocumentDateDay;
	private Integer minDocumentDateMonth;
	private Integer minDocumentDateYear;
	private Integer maxDocumentDateDay;
	private Integer maxDocumentDateMonth;
	private Integer maxDocumentDateYear;
	private Integer minFilingDateDay;
	private Integer minFilingDateMonth;
	private Integer minFilingDateYear;
	private Integer maxFilingDateDay;
	private Integer maxFilingDateMonth;
	private Integer maxFilingDateYear;
	private int pageNumber;
	private int pageSize;
	private String registryCode;
	private String status;

	public static LocalDateTime buildDate(Integer day, Integer month, Integer year, boolean end) {
		if (day == null && month == null && year == null) {
			return null;
		}
		if (year == null) {
			throw new DateTimeException("Year portion of date is required.");
		}
		if (month == null) {
			month = end ? 12 : 1;
		}
		if (day == null) {
			day = end ? YearMonth.of(year, month).lengthOfMonth() : 1;
		}
		return LocalDateTime.of(year, month, day, 0, 0);
	}

	public String getSearchText() {
		return searchText;
	}

	public long getLimit() {
		return pageSize;
	}

	public String getLink(int pageNumber) {
		String query = buildQuery("searchText", getSearchText()) +
				buildQuery("pageNumber", pageNumber) +
				buildQuery("minDocumentDateDay", getMinDocumentDateDay()) +
				buildQuery("minDocumentDateMonth", getMinDocumentDateMonth()) +
				buildQuery("minDocumentDateYear", getMinDocumentDateYear()) +
				buildQuery("maxDocumentDateDay", getMaxDocumentDateDay()) +
				buildQuery("maxDocumentDateMonth", getMaxDocumentDateMonth()) +
				buildQuery("maxDocumentDateYear", getMaxDocumentDateYear()) +
				buildQuery("minFilingDateDay", getMinFilingDateDay()) +
				buildQuery("minFilingDateMonth", getMinFilingDateMonth()) +
				buildQuery("minFilingDateYear", getMinFilingDateYear()) +
				buildQuery("maxFilingDateDay", getMaxFilingDateDay()) +
				buildQuery("maxFilingDateMonth", getMaxFilingDateMonth()) +
				buildQuery("maxFilingDateYear", getMaxFilingDateYear()) +
				buildQuery("registryCode", getRegistryCode()) +
				buildQuery("admin", getAdmin());
		return ("/?" + query);
	}

	public long getOffset() {
		return (long) (pageNumber - 1) * pageSize;
	}

	private String buildQuery(String paramName, Object paramValue) {
		if (paramValue != null && paramValue != "") {
			return paramName + "=" + paramValue + "&";
		}
		return "";
	}

	public Boolean getAdmin() {
		return admin;
	}

	public LocalDateTime getMinDocumentDate() {
		return buildDate(minDocumentDateDay, minDocumentDateMonth, minDocumentDateYear, false);
	}

	public Integer getMinDocumentDateDay() {
		return minDocumentDateDay;
	}

	public Integer getMinDocumentDateMonth() {
		return minDocumentDateMonth;
	}

	public Integer getMinDocumentDateYear() {
		return minDocumentDateYear;
	}

	public LocalDateTime getMaxDocumentDate() {
		return buildDate(maxDocumentDateDay, maxDocumentDateMonth, maxDocumentDateYear, true);
	}

	public Integer getMaxDocumentDateDay() {
		return maxDocumentDateDay;
	}

	public Integer getMaxDocumentDateMonth() {
		return maxDocumentDateMonth;
	}

	public Integer getMaxDocumentDateYear() {
		return maxDocumentDateYear;
	}

	public LocalDateTime getMinFilingDate() {
		return buildDate(minFilingDateDay, minFilingDateMonth, minFilingDateYear, false);
	}

	public Integer getMinFilingDateDay() {
		return minFilingDateDay;
	}

	public Integer getMinFilingDateMonth() {
		return minFilingDateMonth;
	}

	public Integer getMinFilingDateYear() {
		return minFilingDateYear;
	}

	public LocalDateTime getMaxFilingDate() {
		return buildDate(maxFilingDateDay, maxFilingDateMonth, maxFilingDateYear, true);
	}

	public Integer getMaxFilingDateDay() {
		return maxFilingDateDay;
	}

	public Integer getMaxFilingDateMonth() {
		return maxFilingDateMonth;
	}

	public Integer getMaxFilingDateYear() {
		return maxFilingDateYear;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public String getRegistryCode() {
		return registryCode;
	}

	public String getStatus() {
		return status;
	}

	public boolean isEmpty() {
		return StringUtils.isEmpty(searchText);
	}

	public void setAdmin(Boolean admin) {
		this.admin = admin;
	}

	public void setSearchText(String searchText) {
		this.searchText = searchText;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public void setMinDocumentDateDay(Integer day) {
		this.minDocumentDateDay = day;
	}

	public void setMinDocumentDateMonth(Integer month) {
		this.minDocumentDateMonth = month;
	}

	public void setMinDocumentDateYear(Integer year) {
		this.minDocumentDateYear = year;
	}

	public void setMaxDocumentDateDay(Integer day) {
		this.maxDocumentDateDay = day;
	}

	public void setMaxDocumentDateMonth(Integer month) {
		this.maxDocumentDateMonth = month;
	}

	public void setMaxDocumentDateYear(Integer year) {
		this.maxDocumentDateYear = year;
	}

	public void setMinFilingDateDay(Integer day) {
		this.minFilingDateDay = day;
	}

	public void setMinFilingDateMonth(Integer month) {
		this.minFilingDateMonth = month;
	}

	public void setMinFilingDateYear(Integer year) {
		this.minFilingDateYear = year;
	}

	public void setMaxFilingDateDay(Integer day) {
		this.maxFilingDateDay = day;
	}

	public void setMaxFilingDateMonth(Integer month) {
		this.maxFilingDateMonth = month;
	}

	public void setMaxFilingDateYear(Integer year) {
		this.maxFilingDateYear = year;
	}

	public void setRegistryCode(String registryCode) {
		this.registryCode = registryCode;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
