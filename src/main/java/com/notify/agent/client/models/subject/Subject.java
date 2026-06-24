package com.notify.agent.client.models.subject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.notify.agent.client.enums.Channel;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "channel")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailSubject.class, name = "EMAIL"),
        @JsonSubTypes.Type(value = SmsSubject.class, name = "SMS"),
        @JsonSubTypes.Type(value = PushSubject.class, name = "PUSH"),
        @JsonSubTypes.Type(value = WebhookSubject.class, name = "WEBHOOK")
})
@JsonIgnoreProperties({ "subjectId", "address", "addressFingerprint" })
public abstract class Subject implements Serializable {

    protected final String subjectId;
    protected final Channel channel;
    protected final String correlationId;
    protected final Map<String, String> attributes;

    protected Subject(
            String subjectId,
            Channel channel,
            String correlationId,
            Map<String, String> attributes) {

        this.subjectId = subjectId;
        this.channel = channel;
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.attributes = attributes;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public abstract String getAddress();

    public abstract String addressFingerprint();
}
