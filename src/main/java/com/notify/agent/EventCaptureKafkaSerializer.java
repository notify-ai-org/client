package com.notify.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notify.agent.client.models.EventCapture;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

/**
 * Kafka {@link Serializer} for {@link EventCapture} objects used by the client-side
 * Kafka producer inside {@link AcpServerClient}.
 */
public class EventCaptureKafkaSerializer implements Serializer<EventCapture> {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No extra configuration required
    }

    @Override
    public byte[] serialize(String topic, EventCapture data) {
        if (data == null) return null;
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize EventCapture to byte[]", e);
        }
    }

    @Override
    public void close() {
        // Stateless — nothing to close
    }
}
