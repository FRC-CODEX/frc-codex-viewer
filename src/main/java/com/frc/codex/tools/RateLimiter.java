package com.frc.codex.tools;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimiter
{
	private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);
	private static final int MONITOR_FACTOR = 100;
	protected final Queue<Long> monitorTimestamps;
	protected final int rapidRateLimit;
	protected final int rapidRateWindow;
	private long waitUntilMs;

	public RateLimiter(int rapidRateLimit, int rapidRateWindow) {
		this.rapidRateLimit = rapidRateLimit;
		this.rapidRateWindow = rapidRateWindow;
		this.monitorTimestamps = new ConcurrentLinkedQueue<>();
		waitUntilMs = System.currentTimeMillis();
	}

	public synchronized void registerTimestamp() {
		monitorTimestamps.add(System.currentTimeMillis());

		// Remove timestamps older than one minute
		long threshold = System.currentTimeMillis() - (long) rapidRateWindow * MONITOR_FACTOR;
		while (!monitorTimestamps.isEmpty() && monitorTimestamps.peek() < threshold) {
			monitorTimestamps.poll();
		}
		waitUntilMs = System.currentTimeMillis() + rapidRateWindow;
	}

	public synchronized void waitForRapidRateLimit() throws InterruptedException {
		long waitTime = waitUntilMs - System.currentTimeMillis();
		Long oldest = monitorTimestamps.peek();
		int requests = monitorTimestamps.size();
		double seconds = 0;
		double approximateRate = 0;
		if (oldest != null) {
			seconds = (System.currentTimeMillis() - oldest) / 1000.0;
			approximateRate = (((double) requests / seconds) + (((double) requests - 1) / seconds)) * 0.5;
		}
		if (waitTime > 0) {
			LOG.info(
					"Waiting for rapid rate limit: {} ms (requests={},seconds={},requestsPerSecond={})",
					waitTime, requests, String.format("%.2f", seconds), String.format("%.2f", approximateRate)
			);
			Thread.sleep(waitTime);
		}
	}
}
