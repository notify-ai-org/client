package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class PushSubject extends Subject {

    @JsonProperty("deviceToken")
    private final String deviceToken;

    public PushSubject(
            String deviceToken,
            String correlationId,
            Map<String, String> attributes) {

        super(deviceToken, Channel.PUSH, correlationId, attributes);
        this.deviceToken = Objects.requireNonNull(deviceToken, "deviceToken");
    }

    @Override
    public String getAddress() {
        return deviceToken;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    @Override
    public String addressFingerprint() {
        return "push:" + deviceToken;
    }
}
