package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class EmailSubject extends Subject {

    @JsonProperty("email")
    private final String email;
    private final String cc;
    private final String bcc;

    public EmailSubject(
            String email,
            String cc,
            String bcc,
            String correlationId,
            Map<String, String> attributes) {

        super(email, Channel.EMAIL, correlationId, attributes);
        this.email = Objects.requireNonNull(email, "email");
        this.cc = cc;
        this.bcc = bcc;
    }

    @Override
    public String getAddress() {
        return email;
    }

    public String getEmail() {
        return email;
    }

    public String getCc() {
        return cc;
    }

    public String getBcc() {
        return bcc;
    }

    @Override
    public String addressFingerprint() {
        return "email:" + email.toLowerCase();
    }
}
