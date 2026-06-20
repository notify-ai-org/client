package com.notify.agent;

import com.notify.agent.config.NotifyProperties;
import com.notify.agent.service.JwtService;
import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.ClientRegistrationDto;
import com.notify.agent.client.models.EventCapture;
import com.notify.agent.client.models.EventSchedule;
import com.notify.agent.client.models.TokenRefreshDto;
import com.notify.agent.client.models.metadata.EventMetadata;
import com.notify.agent.client.models.metadata.RuleMetadata;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(Bootstrapper.class);

    private final NotifyProperties props;
    private final AnnotationProcessor annotationProcessor;
    private final VocabularyManager vocabularyManager;
    private final Buffer buffer;
    private final AcpServerClient acpClient;
    private final TokenHolder tokenHolder;
    private final InvokeManager invokeManager;
    private final JwtService jwtService;
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
            JwtService jwtService,
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
        this.jwtService = jwtService;
    }

    /**
     * Called by Spring initMethod. Runs scanning, registration, enqueue, and starts
     * Dispatcher.
     */
    public void bootstrap() {
        log.info("Starting Notification Engine SDK bootstrap sequence...");
        annotationProcessor.process();
        invokeManager.buildFrom(annotationProcessor);

        String clientId = props.getClientToken();

        ClientRegistrationDto.Response resp;

        try {
            log.info("Registering client with acp-server at: {}", props.getAcpServerUrl());
            ClientRegistrationDto.Request reg = new ClientRegistrationDto.Request();
            reg.setClientId(clientId);
            reg.setApplicationName(props.getApplicationName());
            reg.setBasePackage(props.getBasePackage());
            resp = acpClient.register(reg, null);
            if (resp != null && resp.getToken() != null) {
                tokenHolder.setTokens(resp.getToken(), resp.getRefreshToken(), resp.getKafkaHeaderToken(),
                        resp.getExpiresInMs());
                log.info("Client registration successful. Access token received and saved.");
            } else {
                log.warn("Client registration response did not contain tokens.");
            }
        } catch (Exception e) {
            log.warn("Client registration failed (continuing without authentication): {}", e.getMessage());
            return;
        }

        if (props.isKafkaEnabled()) {
            initializeKafkaClients(resp, clientId);
        } else {
            log.info("Kafka is disabled for this client; event captures will use HTTP transport.");
        }

        List<EventMetadata> events = annotationProcessor.getEvents();
        log.info("Found {} scanned event(s) to register.", events.size());
        if (!events.isEmpty()) {
            events.forEach(
                    (event) -> {
                        log.debug("Enqueuing REGISTER event for event name: {}", event.getEvent().getName());
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
        log.info("Found {} vocabulary models to register.", vocab.size());
        if (!vocab.isEmpty()) {
            buffer.addVocabulary(vocab);
        }

        List<RuleMetadata> rules = annotationProcessor.getRules();
        log.info("Found {} scanned rule(s) to register.", rules.size());
        for (RuleMetadata r : rules) {
            log.debug("Enqueuing rule registration: {}", r.getName());
            String ev = (r.getEvent() != null && !r.getEvent().isEmpty()) ? r.getEvent() : "*";
            buffer.addRule(ev, r.getName(), r.getDescription(), null);
        }

        log.info("Initializing Dispatcher with background worker thread.");
        dispatcher = new Dispatcher(buffer, acpClient, jwtService, tokenHolder,
                this::refreshToken, consumer, producer, eventListener);
        dispatcherThread = new Thread(dispatcher, "notify-dispatcher");
        dispatcherThread.setDaemon(false);
        dispatcherThread.start();
        log.info("Dispatcher background thread started successfully.");
    }

    private void initializeKafkaClients(ClientRegistrationDto.Response resp, String clientId) {
        log.info("Initializing dynamic Kafka client properties...");
        Map<String, Object> producerProps = kafkaConfig.producerProperties();
        Map<String, Object> consumerProps = kafkaConfig.consumerProperties();

        String apiKey = resp.getApiKey();
        String apiSecret = resp.getApiSecret();
        if (apiKey != null && !apiKey.isEmpty() && apiSecret != null && !apiSecret.isEmpty()) {
            log.debug("Configuring Kafka SASL authentication");
            String jaasConfig = String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username='%s' password='%s';",
                    apiKey, apiSecret);
            producerProps.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            consumerProps.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            producerProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            consumerProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            producerProps.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            consumerProps.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        } else {
            log.debug("No SASL credentials found in token; initializing without SASL.");
        }

        if (clientId != null && !clientId.isEmpty()) {
            producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, clientId + "-producer");
            consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "-consumer");
        }

        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventCaptureKafkaSerializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                EventScheduleKafkaDeserializer.class.getName());

        log.debug("Instantiating Kafka producer and consumer...");
        this.producer = new KafkaProducer<>(producerProps);
        this.consumer = new KafkaConsumer<>(consumerProps);
        log.info("Kafka clients instantiated successfully.");
    }

    /**
     * Package-private for unit testing: decodes the Base64 token JSON and
     * returns the extracted credentials without touching Kafka or network.
     *
     * @param rawToken         Base64-encoded JSON string, may be null/empty
     * @param fallbackClientId the clientId from properties to use when the token
     *                         has none
     */
    DecodedToken decodeToken(String rawToken, String fallbackClientId) {
        String clientId = fallbackClientId;
        String apiKey = "";
        String apiSecret = "";
        if (rawToken != null && !rawToken.isEmpty()) {
            try {
                String decodedJson = new String(Base64.getDecoder().decode(rawToken));
                JsonNode node = new ObjectMapper().readTree(decodedJson);
                if (node.has("clientId"))
                    clientId = node.get("clientId").asText();
                if (node.has("apiKey"))
                    apiKey = node.get("apiKey").asText();
                if (node.has("apiSecret"))
                    apiSecret = node.get("apiSecret").asText();
            } catch (Exception e) {
                log.error("Failed to parse clientToken. Kafka initialization may fail.", e);
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
            this.clientId = clientId != null ? clientId : "";
            this.apiKey = apiKey != null ? apiKey : "";
            this.apiSecret = apiSecret != null ? apiSecret : "";
        }
    }

    private void refreshToken() {
        String ref = tokenHolder.getRefreshToken();
        if (ref == null || ref.isEmpty()) {
            log.debug("No refresh token available, skipping refresh.");
            return;
        }
        try {
            log.info("Refreshing client token for client ID: {}", clientId);
            TokenRefreshDto.Request req = new TokenRefreshDto.Request();
            req.setClientId(clientId);
            req.setRefreshToken(ref);
            TokenRefreshDto.Response resp = acpClient.refreshToken(req);
            tokenHolder.setToken(resp.getToken(), resp.getExpiresInMs());
            log.info("Client token refreshed successfully.");
        } catch (Exception e) {
            log.error("Failed to refresh client token: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Bootstrapper, stopping Dispatcher background worker...");
        if (dispatcher != null)
            dispatcher.stop();
        if (dispatcherThread != null) {
            try {
                dispatcherThread.join(5_000);
                log.debug("Dispatcher background worker thread stopped successfully.");
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for Dispatcher thread shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
        if (metricsManager != null && acpClient != null) {
            log.info("Sending final client metrics to acp-server...");
            metricsManager.sendToAcpServer(acpClient, tokenHolder != null ? tokenHolder.getToken() : null);
        }
        log.info("Bootstrapper shutdown sequence complete.");
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
