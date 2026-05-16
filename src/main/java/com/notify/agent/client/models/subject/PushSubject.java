package com.notify.agent.client.models.subject;

import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class PushSubject extends Subject {

    private final String deviceToken;

    public PushSubject(
            String subjectId,
            String deviceToken,
            String correlationId,
            Map<String, String> attributes) {

        super(subjectId, Channel.PUSH, correlationId, attributes);
        this.deviceToken = Objects.requireNonNull(deviceToken, "deviceToken");
    }

    @Override
    public String getAddress() {
        return deviceToken;
    }

    @Override
    public String addressFingerprint() {
        return "push:" + deviceToken;
    }
}
