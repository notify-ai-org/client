package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class SmsSubject extends Subject {

    @JsonProperty("phoneNumber")
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    @Override
    public String addressFingerprint() {
        return "sms:" + phoneNumber;
    }
}
