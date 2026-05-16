package com.notify.agent.client.models.subject;

import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class WebhookSubject extends Subject {

    private final String url;

    public WebhookSubject(
            String subjectId,
            String url,
            String correlationId,
            Map<String, String> attributes) {

        super(subjectId, Channel.WEBHOOK, correlationId, attributes);
        this.url = Objects.requireNonNull(url, "url");
    }

    @Override
    public String getAddress() {
        return url;
    }

    @Override
    public String addressFingerprint() {
        return "webhook:" + url;
    }
}
