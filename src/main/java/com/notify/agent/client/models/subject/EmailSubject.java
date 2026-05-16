package com.notify.agent.client.models.subject;

import com.notify.agent.client.enums.Channel;

import java.util.Map;
import java.util.Objects;

public final class EmailSubject extends Subject {

    private final String email;
    private final String cc;
    private final String bcc;

    public EmailSubject(
            String subjectId,
            String email,
            String cc,
            String bcc,
            String correlationId,
            Map<String, String> attributes) {

        super(subjectId, Channel.EMAIL, correlationId, attributes);
        this.email = Objects.requireNonNull(email, "email");
        this.cc = cc;
        this.bcc = bcc;
    }

    @Override
    public String getAddress() {
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
