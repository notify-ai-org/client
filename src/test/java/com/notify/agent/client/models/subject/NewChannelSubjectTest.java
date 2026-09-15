package com.notify.agent.client.models.subject;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.agent.client.enums.Channel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NewChannelSubjectTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void roundTripsBothChannelsWithTheirRecipientAndAttributes() throws Exception {
        for (Subject original : new Subject[] {
                new InAppSubject("https://app.example/inbox", "user-1", "correlation-1", Map.of("locale", "en")),
                new WhatsAppSubject("+14155552671", "correlation-1", Map.of("locale", "en"))}) {
            Subject restored = json.readValue(json.writeValueAsString(original), Subject.class);
            assertEquals(original.getClass(), restored.getClass());
            assertEquals(original.getAddress(), restored.getAddress());
            assertEquals(original.getChannel(), restored.getChannel());
            assertEquals(original.getCorrelationId(), restored.getCorrelationId());
            assertEquals(original.getAttributes(), restored.getAttributes());
            if (restored instanceof InAppSubject inApp) assertEquals("user-1", inApp.getUserId());
        }
    }

    @Test
    void deserializesChannelPayloadsAndRejectsMissingRecipients() throws Exception {
        assertEquals(Channel.IN_APP, json.readValue(
                "{\"channel\":\"IN_APP\",\"url\":\"https://app.example/inbox\",\"userId\":\"user-1\"}", Subject.class).getChannel());
        assertEquals(Channel.WHATSAPP, json.readValue(
                "{\"channel\":\"WHATSAPP\",\"phoneNumber\":\"+14155552671\"}", Subject.class).getChannel());
        assertThrows(Exception.class, () -> json.readValue("{\"channel\":\"IN_APP\"}", Subject.class));
        assertThrows(Exception.class, () -> json.readValue(
                "{\"channel\":\"IN_APP\",\"userId\":\"user-1\"}", Subject.class));
        assertThrows(Exception.class, () -> json.readValue("{\"channel\":\"WHATSAPP\"}", Subject.class));
    }
}
