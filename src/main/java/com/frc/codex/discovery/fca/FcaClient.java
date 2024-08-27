package com.frc.codex.discovery.fca;

import java.util.Date;
import java.util.List;

import com.frc.codex.discovery.fca.FcaFiling;

public interface FcaClient {
	List<FcaFiling> fetchAllSinceDate(Date sinceDate);
}
