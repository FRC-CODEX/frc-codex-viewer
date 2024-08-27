package com.frc.codex.discovery.fca.impl;

import java.util.Date;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.frc.codex.discovery.fca.FcaClient;
import com.frc.codex.discovery.fca.FcaFiling;

@Component
@Profile("test")
public class TestFcaClientImpl implements FcaClient {
	public List<FcaFiling> fetchAllSinceDate(Date sinceDate) {
		return null;
	}
}
