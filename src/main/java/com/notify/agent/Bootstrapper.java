package com.notify.agent;

import com.notify.agent.config.NotifyProperties;
import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.ClientRegistrationDto;
import com.notify.agent.client.models.EventCapture;
import com.notify.agent.client.models.EventSchedule;
import com.notify.agent.client.models.TokenRefreshDto;
import com.notify.agent.client.models.metadata.EventMetadata;
import com.notify.agent.client.models.metadata.RuleMetadata;

import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.List;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.Map;
import com.notify.agent.config.KafkaConfig;

/**
 * Bootstraps the Notification Engine SDK: runs AnnotationProcessor and
 * VocabularyManager, collects client metadata and client id, initializes
 * Buffer,
 * registers with acp-server, obtains token, enqueues vocabulary/rules into the
 * Buffer, starts the Dispatcher, and handles graceful shutdown.
 */
public class Bootstrapper {

    private final NotifyProperties props;
    private final AnnotationProcessor annotationProcessor;
    private final VocabularyManager vocabularyManager;
    private final Buffer buffer;
    private final AcpServerClient acpClient;
    private final TokenHolder tokenHolder;
    private final InvokeManager invokeManager;
    private final MetricsManager metricsManager;
    private final EventListener eventListener;
    private final KafkaConfig kafkaConfig;
    
    private KafkaConsumer<String, EventSchedule> consumer;
    private KafkaProducer<String, EventCapture> producer;

    private String clientId;
    private Dispatcher dispatcher;
    private Thread dispatcherThread;

    public Bootstrapper(NotifyProperties props,
            AnnotationProcessor annotationProcessor,
            VocabularyManager vocabularyManager,
            Buffer buffer,
            AcpServerClient acpClient,
            TokenHolder tokenHolder,
            InvokeManager invokeManager,
            KafkaConfig kafkaConfig,
            MetricsManager metricsManager, EventListener eventListener) {
        this.props = props;
        this.annotationProcessor = annotationProcessor;
        this.vocabularyManager = vocabularyManager;
        this.buffer = buffer;
        this.acpClient = acpClient;
        this.tokenHolder = tokenHolder;
        this.invokeManager = invokeManager;
        this.metricsManager = metricsManager;
        this.eventListener = eventListener;
        this.kafkaConfig = kafkaConfig;
    }

    /**
     * Called by Spring initMethod. Runs scanning, registration, enqueue, and starts
     * Dispatcher.
     */
    public void bootstrap() {
        annotationProcessor.process();
        invokeManager.buildFrom(annotationProcessor);

        String rawToken = props.getClientToken();
        DecodedToken decoded = decodeToken(rawToken, props.getClientId());
        this.clientId = decoded.clientId;
        String apiKey = decoded.apiKey;
        String apiSecret = decoded.apiSecret;


        // Initialize Kafka Producer and Consumer dynamically
        Map<String, Object> producerProps = kafkaConfig.producerProperties();
        Map<String, Object> consumerProps = kafkaConfig.consumerProperties();
        
        if (!apiKey.isEmpty() && !apiSecret.isEmpty()) {
            String jaasConfig = String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username='%s' password='%s';", apiKey, apiSecret);
            producerProps.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            consumerProps.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            producerProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            consumerProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            producerProps.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            consumerProps.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        }
        
        if (clientId != null && !clientId.isEmpty()) {
            producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, clientId + "-producer");
            consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "-consumer");
        }

        this.producer = new KafkaProducer<>(producerProps);
        this.consumer = new KafkaConsumer<>(consumerProps);

        try {
            ClientRegistrationDto.Request reg = new ClientRegistrationDto.Request();
            reg.setClientId(clientId);
            reg.setRawToken(rawToken);
            reg.setApplicationName(props.getApplicationName());
            reg.setBasePackage(props.getBasePackage());
            ClientRegistrationDto.Response resp = acpClient.register(reg, null);
            if (resp != null && resp.getToken() != null) {
                tokenHolder.setTokens(resp.getToken(), resp.getRefreshToken(), resp.getExpiresInMs());
            }
        } catch (Exception e) {
            // acp-server may not have /api/client/register; continue without auth
        }

        List<EventMetadata> events = annotationProcessor.getEvents();
        if (!events.isEmpty()) {
            events.forEach(
                    (event) -> {
                        EventCapture dto = new EventCapture();
                        dto.setEvent(event.getEvent());
                        dto.getEvent().setEventType("REGISTER");
                        dto.setOccuredAt(Instant.now());
                        dto.setPayload(vocabularyManager.toFlattenedMap(event));
                        dto.setServiceName(event.getDeclaringClass().getSimpleName());
                        buffer.addEventCapture(dto);
                    });
        }

        List<ClassModel> vocab = vocabularyManager.toClassModelDtoList();
        if (!vocab.isEmpty())
            buffer.addVocabulary(vocab);

        for (RuleMetadata r : annotationProcessor.getRules()) {
            String ev = (r.getEvent() != null && !r.getEvent().isEmpty()) ? r.getEvent() : "*";
            buffer.addRule(ev, r.getName(), r.getDescription(), null);
        }

        dispatcher = new Dispatcher(buffer, acpClient, tokenHolder::getToken, this::refreshToken,
                consumer, producer, eventListener);
        dispatcherThread = new Thread(dispatcher, "notify-dispatcher");
        dispatcherThread.setDaemon(false);
        dispatcherThread.start();
    }

    /**
     * Package-private for unit testing: decodes the Base64 token JSON and
     * returns the extracted credentials without touching Kafka or network.
     *
     * @param rawToken  Base64-encoded JSON string, may be null/empty
     * @param fallbackClientId  the clientId from properties to use when the token has none
     */
    DecodedToken decodeToken(String rawToken, String fallbackClientId) {
        String clientId = fallbackClientId;
        String apiKey = "";
        String apiSecret = "";
        if (rawToken != null && !rawToken.isEmpty()) {
            try {
                String decodedJson = new String(Base64.getDecoder().decode(rawToken));
                JsonNode node = new ObjectMapper().readTree(decodedJson);
                if (node.has("clientId")) clientId = node.get("clientId").asText();
                if (node.has("apiKey")) apiKey = node.get("apiKey").asText();
                if (node.has("apiSecret")) apiSecret = node.get("apiSecret").asText();
            } catch (Exception e) {
                System.err.println("Failed to parse clientToken. Kafka initialization may fail.");
            }
        }
        return new DecodedToken(clientId, apiKey, apiSecret);
    }

    /** Value object holding decoded credential fields. */
    static final class DecodedToken {
        final String clientId;
        final String apiKey;
        final String apiSecret;

        DecodedToken(String clientId, String apiKey, String apiSecret) {
            this.clientId  = clientId  != null ? clientId  : "";
            this.apiKey    = apiKey    != null ? apiKey    : "";
            this.apiSecret = apiSecret != null ? apiSecret : "";
        }
    }

    private void refreshToken() {
        String ref = tokenHolder.getRefreshToken();
        if (ref == null || ref.isEmpty())
            return;
        try {
            TokenRefreshDto.Request req = new TokenRefreshDto.Request();
            req.setClientId(clientId);
            req.setRefreshToken(ref);
            TokenRefreshDto.Response resp = acpClient.refreshToken(req);
            tokenHolder.setToken(resp.getToken(), resp.getExpiresInMs());
        } catch (Exception ignored) {
        }
    }

    @PreDestroy
    public void shutdown() {
        if (dispatcher != null)
            dispatcher.stop();
        if (dispatcherThread != null) {
            try {
                dispatcherThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (metricsManager != null && acpClient != null) {
            metricsManager.sendToAcpServer(acpClient, tokenHolder != null ? tokenHolder.getToken() : null);
        }
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public InvokeManager getInvokeManager() {
        return invokeManager;
    }

    public MetricsManager getMetricsManager() {
        return metricsManager;
    }

    public AnnotationProcessor getAnnotationProcessor() {
        return annotationProcessor;
    }
}
