package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class WebhookSubject extends Subject {

    @JsonProperty("url")
    private final String url;

    public WebhookSubject(
            String url,
            String correlationId,
            Map<String, String> attributes) {

        super(url, Channel.WEBHOOK, correlationId, attributes);
        this.url = Objects.requireNonNull(url, "url");
    }

    @Override
    public String getAddress() {
        return url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String addressFingerprint() {
        return "webhook:" + url;
    }
}
