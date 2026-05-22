package com.notify.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notify.agent.client.models.EventSchedule;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * Kafka {@link Deserializer} for {@link EventSchedule} objects used by the client-side
 * Kafka consumer inside {@link Dispatcher}.
 */
public class EventScheduleKafkaDeserializer implements Deserializer<EventSchedule> {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No extra configuration required
    }

    @Override
    public EventSchedule deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try {
            return objectMapper.readValue(data, EventSchedule.class);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize EventSchedule from byte[]", e);
        }
    }

    @Override
    public void close() {
        // Stateless — nothing to close
    }
}
