package com.notify.agent.client.models.subject;

import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class SmsSubject extends Subject {

    private final String phoneNumber;

    public SmsSubject(
            String phoneNumber,
            String correlationId,
            Map<String, String> attributes) {

        super(phoneNumber, Channel.SMS, correlationId, attributes);
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
    }

    @Override
    public String getAddress() {
        return phoneNumber;
    }

    @Override
    public String addressFingerprint() {
        return "sms:" + phoneNumber;
    }
}
