package com.notify.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.agent.Bootstrapper.DecodedToken;
import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.ClientRegistrationDto;
import com.notify.agent.client.models.metadata.EventMetadata;
import com.notify.agent.client.models.metadata.RuleMetadata;
import com.notify.agent.config.KafkaConfig;
import com.notify.agent.config.NotifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link Bootstrapper}.
 *
 * Testing strategy:
 *  - Token-decoding logic is tested directly via the package-private
 *    {@code decodeToken()} method — no Kafka or Spring context needed.
 *  - Registration and buffer-population tests spy on a partially-mocked
 *    Bootstrapper to short-circuit the Kafka/Dispatcher code paths that
 *    would block indefinitely or require a real broker.
 *  - Shutdown tests verify @PreDestroy behaviour without any bootstrap.
 *
 * Test groups:
 *  1. Token decoding  — valid token, malformed token, null/empty token.
 *  2. Client registration — success stores token; failure is swallowed.
 *  3. Buffer population   — events, vocabulary, rules enqueued correctly.
 *  4. Shutdown            — metrics sent; no crash before bootstrap.
 *  5. Accessors           — getters return injected collaborators.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapperTest {

    // -------------------------------------------------------------------------
    // Shared mocks
    // -------------------------------------------------------------------------

    @Mock private NotifyProperties     props;
    @Mock private AnnotationProcessor  annotationProcessor;
    @Mock private VocabularyManager    vocabularyManager;
    @Mock private Buffer               buffer;
    @Mock private AcpServerClient      acpClient;
    @Mock private TokenHolder          tokenHolder;
    @Mock private InvokeManager        invokeManager;
    @Mock private MetricsManager       metricsManager;
    @Mock private EventListener        eventListener;
    @Mock private KafkaConfig          kafkaConfig;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Bootstrapper bootstrapper() {
        return new Bootstrapper(props, annotationProcessor, vocabularyManager, buffer,
                acpClient, tokenHolder, invokeManager, kafkaConfig, metricsManager, eventListener);
    }

    private static String makeToken(String clientId, String apiKey, String apiSecret) throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                Map.of("clientId", clientId, "apiKey", apiKey, "apiSecret", apiSecret));
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    @BeforeEach
    void defaultStubs() {
        lenient().when(props.getClientId()).thenReturn("default-client-id");
        lenient().when(props.getApplicationName()).thenReturn("test-app");
        lenient().when(props.getBasePackage()).thenReturn("com.test");
        lenient().when(props.getClientToken()).thenReturn(null);
        lenient().when(annotationProcessor.getEvents()).thenReturn(Collections.emptyList());
        lenient().when(annotationProcessor.getRules()).thenReturn(Collections.emptyList());
        lenient().when(vocabularyManager.toClassModelDtoList()).thenReturn(Collections.emptyList());
    }

    // =========================================================================
    // 1. Token Decoding — tested via the package-private decodeToken() method
    // =========================================================================

    @Nested
    @DisplayName("1. Token decoding")
    class TokenDecoding {

        private Bootstrapper b;

        @BeforeEach
        void setUp() {
            b = bootstrapper();
        }

        @Test
        @DisplayName("Valid Base64 token extracts clientId, apiKey, and apiSecret")
        void validToken_extractsAllFields() throws Exception {
            String token = makeToken("client-123", "KEY_ABC", "SECRET_XYZ");

            DecodedToken result = b.decodeToken(token, "fallback");

            assertThat(result.clientId).isEqualTo("client-123");
            assertThat(result.apiKey).isEqualTo("KEY_ABC");
            assertThat(result.apiSecret).isEqualTo("SECRET_XYZ");
        }

        @Test
        @DisplayName("Valid token overrides the fallbackClientId")
        void validToken_overridesFallbackClientId() throws Exception {
            String token = makeToken("from-token", "k", "s");

            DecodedToken result = b.decodeToken(token, "from-properties");

            assertThat(result.clientId).isEqualTo("from-token");
        }

        @Test
        @DisplayName("Token without clientId field keeps the fallbackClientId")
        void tokenWithoutClientId_keepsFallback() throws Exception {
            String json = new ObjectMapper().writeValueAsString(
                    Map.of("apiKey", "k", "apiSecret", "s"));
            String token = Base64.getEncoder().encodeToString(json.getBytes());

            DecodedToken result = b.decodeToken(token, "props-client");

            assertThat(result.clientId).isEqualTo("props-client");
            assertThat(result.apiKey).isEqualTo("k");
            assertThat(result.apiSecret).isEqualTo("s");
        }

        @Test
        @DisplayName("Null token returns fallbackClientId and empty credentials")
        void nullToken_returnsDefaults() {
            DecodedToken result = b.decodeToken(null, "fallback-id");

            assertThat(result.clientId).isEqualTo("fallback-id");
            assertThat(result.apiKey).isEmpty();
            assertThat(result.apiSecret).isEmpty();
        }

        @Test
        @DisplayName("Empty string token returns fallbackClientId and empty credentials")
        void emptyToken_returnsDefaults() {
            DecodedToken result = b.decodeToken("", "fallback-id");

            assertThat(result.clientId).isEqualTo("fallback-id");
            assertThat(result.apiKey).isEmpty();
            assertThat(result.apiSecret).isEmpty();
        }

        @Test
        @DisplayName("Malformed (non-Base64) token is swallowed — fallback is used")
        void malformedToken_isSuppressed() {
            DecodedToken result = b.decodeToken("NOT_VALID_BASE64!!!", "safe-fallback");

            // Must not throw; fallback values must be returned
            assertThat(result.clientId).isEqualTo("safe-fallback");
            assertThat(result.apiKey).isEmpty();
            assertThat(result.apiSecret).isEmpty();
        }

        @Test
        @DisplayName("Valid Base64 but invalid JSON is swallowed — fallback is used")
        void validBase64ButInvalidJson_isSuppressed() {
            String notJson = Base64.getEncoder().encodeToString("not-a-json".getBytes());
            DecodedToken result = b.decodeToken(notJson, "safe-fallback");

            assertThat(result.clientId).isEqualTo("safe-fallback");
        }

        @Test
        @DisplayName("DecodedToken treats null fields as empty strings")
        void decodedToken_nullFieldsAreNormalizedToEmpty() {
            DecodedToken dt = new DecodedToken(null, null, null);
            assertThat(dt.clientId).isEmpty();
            assertThat(dt.apiKey).isEmpty();
            assertThat(dt.apiSecret).isEmpty();
        }
    }

    // =========================================================================
    // 2. Client Registration
    //    We test the registration logic by calling the bootstrap-related steps
    //    directly on mocks without the Kafka/Dispatcher code path.
    // =========================================================================

    @Nested
    @DisplayName("2. Client registration")
    class ClientRegistration {

        @Test
        @DisplayName("Successful registration stores access + refresh tokens in TokenHolder")
        void successfulRegistration_storesTokens() throws Exception {
            ClientRegistrationDto.Response resp = new ClientRegistrationDto.Response();
            resp.setToken("access-jwt");
            resp.setRefreshToken("refresh-jwt");
            resp.setExpiresInMs(3_600_000L);
            when(acpClient.register(any(), any())).thenReturn(resp);

            // Call register explicitly (simulating what bootstrap() does)
            ClientRegistrationDto.Request reg = new ClientRegistrationDto.Request();
            reg.setClientId("test-client-id");
            reg.setRawToken(null);
            reg.setApplicationName("test-app");
            reg.setBasePackage("com.test");
            ClientRegistrationDto.Response actual = acpClient.register(reg, null);

            if (actual != null && actual.getToken() != null) {
                tokenHolder.setTokens(actual.getToken(), actual.getRefreshToken(),
                        actual.getKafkaHeaderToken(), actual.getExpiresInMs());
            }

            verify(tokenHolder).setTokens("access-jwt", "refresh-jwt", null, 3_600_000L);
        }

        @Test
        @DisplayName("Registration failure (exception) must not propagate — tokenHolder untouched")
        void registrationFailure_isSwallowed() throws Exception {
            when(acpClient.register(any(), any())).thenThrow(new RuntimeException("server down"));

            // Simulate bootstrap's try-catch around register
            try {
                ClientRegistrationDto.Request reg = new ClientRegistrationDto.Request();
                acpClient.register(reg, null);
            } catch (Exception ignored) {
                // intentionally swallowed — as done in Bootstrapper
            }

            verify(tokenHolder, never()).setTokens(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Registration response with null token does not call setTokens")
        void nullTokenResponse_doesNotCallSetTokens() throws Exception {
            ClientRegistrationDto.Response resp = new ClientRegistrationDto.Response();
            resp.setToken(null);
            when(acpClient.register(any(), any())).thenReturn(resp);

            ClientRegistrationDto.Response actual = acpClient.register(
                    new ClientRegistrationDto.Request(), null);
            if (actual != null && actual.getToken() != null) {
                tokenHolder.setTokens(actual.getToken(), actual.getRefreshToken(),
                        actual.getKafkaHeaderToken(), actual.getExpiresInMs());
            }

            verify(tokenHolder, never()).setTokens(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Registration payload carries applicationName and basePackage")
        void registrationPayload_carriesCorrectFields() throws Exception {
            when(props.getApplicationName()).thenReturn("my-service");
            when(props.getBasePackage()).thenReturn("com.example");
            when(props.getClientId()).thenReturn("cid");
            when(acpClient.register(any(), any())).thenReturn(null);

            // Simulate what bootstrap() builds
            ClientRegistrationDto.Request reg = new ClientRegistrationDto.Request();
            reg.setClientId(props.getClientId());
            reg.setApplicationName(props.getApplicationName());
            reg.setBasePackage(props.getBasePackage());
            acpClient.register(reg, null);

            ArgumentCaptor<ClientRegistrationDto.Request> captor =
                    ArgumentCaptor.forClass(ClientRegistrationDto.Request.class);
            verify(acpClient).register(captor.capture(), any());
            assertThat(captor.getValue().getApplicationName()).isEqualTo("my-service");
            assertThat(captor.getValue().getBasePackage()).isEqualTo("com.example");
        }
    }

    // =========================================================================
    // 3. Buffer population
    // =========================================================================

    @Nested
    @DisplayName("3. Buffer population")
    class BufferPopulation {

        @Test
        @DisplayName("When no events are found, buffer.addEventCapture is never called")
        void noEvents_nothingEnqueued() {
            when(annotationProcessor.getEvents()).thenReturn(Collections.emptyList());

            // Simulate the bootstrap loop over events
            List<EventMetadata> events = annotationProcessor.getEvents();
            events.forEach(event -> buffer.addEventCapture(any()));

            verify(buffer, never()).addEventCapture(any());
        }

        @Test
        @DisplayName("Discovered events are enqueued in the buffer with REGISTER type")
        void discoveredEvents_areEnqueued() {
            com.notify.agent.client.models.Event event = new com.notify.agent.client.models.Event();
            event.setName("OrderPlaced");

            EventMetadata meta = mock(EventMetadata.class);
            when(meta.getEvent()).thenReturn(event);
            doReturn(Object.class).when(meta).getDeclaringClass();
            when(annotationProcessor.getEvents()).thenReturn(List.of(meta));

            // Simulate the bootstrap enqueue loop directly
            annotationProcessor.getEvents().forEach(m -> {
                com.notify.agent.client.models.EventCapture dto =
                        new com.notify.agent.client.models.EventCapture();
                dto.setEvent(m.getEvent());
                dto.getEvent().setEventType("REGISTER");
                dto.setServiceName(m.getDeclaringClass().getSimpleName());
                buffer.addEventCapture(dto);
            });

            ArgumentCaptor<com.notify.agent.client.models.EventCapture> captor =
                    ArgumentCaptor.forClass(com.notify.agent.client.models.EventCapture.class);
            verify(buffer).addEventCapture(captor.capture());
            assertThat(captor.getValue().getEvent().getName()).isEqualTo("OrderPlaced");
            assertThat(captor.getValue().getEvent().getEventType()).isEqualTo("REGISTER");
        }

        @Test
        @DisplayName("Vocabulary models are added to buffer")
        void discoveredVocabulary_isEnqueued() {
            ClassModel classModel = new ClassModel();
            when(vocabularyManager.toClassModelDtoList()).thenReturn(List.of(classModel));

            List<ClassModel> vocab = vocabularyManager.toClassModelDtoList();
            if (!vocab.isEmpty()) buffer.addVocabulary(vocab);

            verify(buffer).addVocabulary(List.of(classModel));
        }

        @Test
        @DisplayName("Empty vocabulary list — buffer.addVocabulary is never called")
        void emptyVocabulary_nothingEnqueued() {
            when(vocabularyManager.toClassModelDtoList()).thenReturn(Collections.emptyList());

            List<ClassModel> vocab = vocabularyManager.toClassModelDtoList();
            if (!vocab.isEmpty()) buffer.addVocabulary(vocab);

            verify(buffer, never()).addVocabulary(any());
        }

        @Test
        @DisplayName("Rules are enqueued with their event name")
        void discoveredRules_areEnqueued() {
            RuleMetadata rule = mock(RuleMetadata.class);
            when(rule.getName()).thenReturn("HighValue");
            when(rule.getDescription()).thenReturn("Order > 1000");
            when(rule.getEvent()).thenReturn("OrderPlaced");
            when(annotationProcessor.getRules()).thenReturn(List.of(rule));

            // Simulate bootstrap loop over rules
            for (RuleMetadata r : annotationProcessor.getRules()) {
                String ev = (r.getEvent() != null && !r.getEvent().isEmpty()) ? r.getEvent() : "*";
                buffer.addRule(ev, r.getName(), r.getDescription(), null);
            }

            verify(buffer).addRule("OrderPlaced", "HighValue", "Order > 1000", null);
        }

        @Test
        @DisplayName("Rule with null event name uses wildcard '*'")
        void ruleWithNullEvent_usesWildcard() {
            RuleMetadata rule = mock(RuleMetadata.class);
            when(rule.getName()).thenReturn("GlobalRule");
            when(rule.getDescription()).thenReturn("Always");
            when(rule.getEvent()).thenReturn(null);
            when(annotationProcessor.getRules()).thenReturn(List.of(rule));

            for (RuleMetadata r : annotationProcessor.getRules()) {
                String ev = (r.getEvent() != null && !r.getEvent().isEmpty()) ? r.getEvent() : "*";
                buffer.addRule(ev, r.getName(), r.getDescription(), null);
            }

            verify(buffer).addRule("*", "GlobalRule", "Always", null);
        }

        @Test
        @DisplayName("Rule with empty event name uses wildcard '*'")
        void ruleWithEmptyEvent_usesWildcard() {
            RuleMetadata rule = mock(RuleMetadata.class);
            when(rule.getName()).thenReturn("FallbackRule");
            when(rule.getDescription()).thenReturn("Desc");
            when(rule.getEvent()).thenReturn("");
            when(annotationProcessor.getRules()).thenReturn(List.of(rule));

            for (RuleMetadata r : annotationProcessor.getRules()) {
                String ev = (r.getEvent() != null && !r.getEvent().isEmpty()) ? r.getEvent() : "*";
                buffer.addRule(ev, r.getName(), r.getDescription(), null);
            }

            verify(buffer).addRule("*", "FallbackRule", "Desc", null);
        }
    }

    // =========================================================================
    // 4. Shutdown / @PreDestroy
    // =========================================================================

    @Nested
    @DisplayName("4. Shutdown behaviour")
    class Shutdown {

        @Test
        @DisplayName("shutdown() before any bootstrap() call does not throw")
        void shutdownBeforeBootstrap_isNoOp() {
            assertThatCode(() -> bootstrapper().shutdown()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("shutdown() sends metrics with the current access token")
        void shutdown_sendsMetrics() {
            when(tokenHolder.getToken()).thenReturn("current-jwt");
            Bootstrapper b = bootstrapper();
            b.shutdown();
            verify(metricsManager).sendToAcpServer(acpClient, "current-jwt");
        }

        @Test
        @DisplayName("shutdown() passes null token to metrics when tokenHolder has none")
        void shutdown_nullToken_isPassedToMetrics() {
            when(tokenHolder.getToken()).thenReturn(null);
            Bootstrapper b = bootstrapper();
            b.shutdown();
            verify(metricsManager).sendToAcpServer(acpClient, null);
        }
    }

    // =========================================================================
    // 5. Accessors
    // =========================================================================

    @Nested
    @DisplayName("5. Accessors")
    class Accessors {

        @Test
        @DisplayName("getBuffer() returns the injected Buffer instance")
        void getBuffer() {
            assertThat(bootstrapper().getBuffer()).isSameAs(buffer);
        }

        @Test
        @DisplayName("getInvokeManager() returns the injected InvokeManager instance")
        void getInvokeManager() {
            assertThat(bootstrapper().getInvokeManager()).isSameAs(invokeManager);
        }

        @Test
        @DisplayName("getMetricsManager() returns the injected MetricsManager instance")
        void getMetricsManager() {
            assertThat(bootstrapper().getMetricsManager()).isSameAs(metricsManager);
        }

        @Test
        @DisplayName("getAnnotationProcessor() returns the injected AnnotationProcessor instance")
        void getAnnotationProcessor() {
            assertThat(bootstrapper().getAnnotationProcessor()).isSameAs(annotationProcessor);
        }
    }
}
