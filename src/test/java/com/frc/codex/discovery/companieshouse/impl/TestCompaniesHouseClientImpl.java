package com.frc.codex.discovery.companieshouse.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frc.codex.discovery.companieshouse.CompaniesHouseClient;
import com.frc.codex.discovery.companieshouse.CompaniesHouseConfig;

import jdk.jshell.spi.ExecutionControl;

@Component
@Profile("test")
public class TestCompaniesHouseClientImpl implements CompaniesHouseClient {

	public String getCompany(String companyNumber) {
		return null;
	}

	public Set<String> getCompanyFilingUrls(String companyNumber, String filingId) {
		return null;
	}

	public String getCompanyFilingHistory(String companyNumber) {
		return null;
	}

	public Set<String> getCompanyFilingUrls(String companyNumber) {
		return null;
	}

	public void streamFilings(Long timepoint, Function<String, Boolean> callback) {

	}
}
