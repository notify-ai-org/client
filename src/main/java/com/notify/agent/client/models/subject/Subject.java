package com.notify.agent.client.models.subject;

import com.notify.agent.client.enums.Channel;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

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
