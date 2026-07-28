package com.portfoliomanager.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data")
public class MarketDataProperties {

    private String provider = "twelve-data";
    private String apiKey = "";
    private String syncCron = "0 */5 9-16 * * MON-FRI";
    private String timeZone = "America/New_York";
    private int batchSize = 1;
    private int requestTimeoutSeconds = 10;
    private int maxRetries = 2;
    private long retryBackoffMillis = 250;
    private long requestIntervalMillis = 8000;
    private long manualPollIntervalMs = 2000;
    private String intradayInterval = "1min";
    private int intradayLookbackDays = 5;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSyncCron() {
        return syncCron;
    }

    public void setSyncCron(String syncCron) {
        this.syncCron = syncCron;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public long getRequestIntervalMillis() {
        return requestIntervalMillis;
    }

    public void setRequestIntervalMillis(long requestIntervalMillis) {
        this.requestIntervalMillis = requestIntervalMillis;
    }

    public long getManualPollIntervalMs() {
        return manualPollIntervalMs;
    }

    public void setManualPollIntervalMs(long manualPollIntervalMs) {
        this.manualPollIntervalMs = manualPollIntervalMs;
    }

    public String getIntradayInterval() {
        return intradayInterval;
    }

    public void setIntradayInterval(String intradayInterval) {
        this.intradayInterval = intradayInterval;
    }

    public int getIntradayLookbackDays() {
        return intradayLookbackDays;
    }

    public void setIntradayLookbackDays(int intradayLookbackDays) {
        this.intradayLookbackDays = intradayLookbackDays;
    }
}
