package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.notify.agent.client.enums.Channel;
import java.util.Map;

public final class WhatsAppSubject extends Subject {
    @JsonProperty("phoneNumber")
    private final String phoneNumber;

    public WhatsAppSubject(String phoneNumber, String correlationId, Map<String, String> attributes) {
        this(phoneNumber, null, correlationId, attributes);
    }

    @JsonCreator
    public WhatsAppSubject(
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("address") String address,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("attributes") Map<String, String> attributes) {
        super(resolve(phoneNumber, address), Channel.WHATSAPP, correlationId, attributes);
        this.phoneNumber = resolve(phoneNumber, address);
    }

    private static String resolve(String value, String address) {
        String resolved = value != null ? value : address;
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("WHATSAPP subject requires a non-blank 'phoneNumber' field");
        }
        return resolved.trim();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    @Override
    public String getAddress() {
        return phoneNumber;
    }

    @Override
    public String addressFingerprint() {
        return "whatsapp:" + phoneNumber;
    }
}

