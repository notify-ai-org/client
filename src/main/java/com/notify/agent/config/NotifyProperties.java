package com.notify.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Notification Engine SDK.
 * Prefix: notify
 */
@ConfigurationProperties(prefix = "notify.ai.properties")
public class NotifyProperties {

    /**
     * Base package to scan for @Event, @Rule, @Model, etc.
     * From @EnableNotify(basePackage) or here.
     */
    private String basePackage = "com.notify";

    /** acp-server base URL, e.g. http://localhost:8080 */
    private String acpServerUrl = "http://localhost:8080";

    /** Application name for client registration and metrics. */
    private String applicationName = "notify-client";

    /** Constant/hardcoded client identifier for registration. */
    private String clientId = "";

    /** Base64 encoded JSON token for Kafka and client registration. */
    private String clientToken = "";

    /** Buffer batch size before flush. */
    private int bufferBatchSize = 100;

    /** Buffer flush timeout in milliseconds. */
    private long bufferFlushTimeoutMs = 5_000;

    /** Kafka topic for scheduled events from acp-server. */
    private String kafkaTopic = "notify-scheduled-events";

    /** Kafka group id for the client consumer. */
    private String kafkaGroup = "notify-client-group";

    /** Enable Kafka listener for scheduled events. */
    private boolean kafkaEnabled = false;

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getAcpServerUrl() {
        return acpServerUrl;
    }

    public void setAcpServerUrl(String acpServerUrl) {
        this.acpServerUrl = acpServerUrl;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public int getBufferBatchSize() {
        return bufferBatchSize;
    }

    public void setBufferBatchSize(int bufferBatchSize) {
        this.bufferBatchSize = bufferBatchSize;
    }

    public long getBufferFlushTimeoutMs() {
        return bufferFlushTimeoutMs;
    }

    public void setBufferFlushTimeoutMs(long bufferFlushTimeoutMs) {
        this.bufferFlushTimeoutMs = bufferFlushTimeoutMs;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public String getKafkaGroup() {
        return kafkaGroup;
    }

    public void setKafkaGroup(String kafkaGroup) {
        this.kafkaGroup = kafkaGroup;
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }

    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }
}
