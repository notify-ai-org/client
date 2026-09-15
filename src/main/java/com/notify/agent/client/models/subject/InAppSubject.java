package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.notify.agent.client.enums.Channel;
import java.util.Map;

public final class InAppSubject extends Subject {
    @JsonProperty("url")
    private final String url;
    @JsonProperty("userId")
    private final String userId;

    public InAppSubject(String url, String correlationId, Map<String, String> attributes) {
        this(url, null, null, correlationId, attributes);
    }

    public InAppSubject(String url, String userId, String correlationId, Map<String, String> attributes) {
        this(url, null, userId, correlationId, attributes);
    }

    @JsonCreator
    public InAppSubject(
            @JsonProperty("url") String url,
            @JsonProperty("address") String address,
            @JsonProperty("userId") String userId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("attributes") Map<String, String> attributes) {
        super(resolve(url, address), Channel.IN_APP, correlationId, attributes);
        this.url = resolve(url, address);
        this.userId = userId;
    }

    private static String resolve(String value, String address) {
        String resolved = value != null ? value : address;
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("IN_APP subject requires a non-blank 'url' field");
        }
        return resolved.trim();
    }

    public String getUserId() {
        return userId;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String getAddress() {
        return url;
    }

    @Override
    public String addressFingerprint() {
        return "in_app:" + url + ":" + (userId == null ? "" : userId);
    }
}
