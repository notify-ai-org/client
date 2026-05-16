package com.notify.agent;

import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.EventCapture;
import com.notify.agent.client.models.ClientRegistrationDto;
import com.notify.agent.client.models.TokenRefreshDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * HTTP client for acp-server: registration, token refresh, vocabulary, rules,
 * event captures.
 * Handles auth via Bearer token. Caller is responsible for token refresh on
 * 401.
 */
public class AcpServerClient implements AutoCloseable {

    /** Number of partitions on the {@value #EVENTS_TOPIC} topic. Must match broker config. */
    private static final int NUM_PARTITIONS = 12;
    private static final String EVENTS_TOPIC = "notify-v1-events";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    // --- Kafka producer (lazily initialised) ---
    private volatile KafkaProducer<String, EventCapture> kafkaProducer;
    private final Object producerLock = new Object();

    /**
     * Minimal config needed to build a Kafka producer.
     *
     * @param bootstrapServers comma-separated {@code host:port} list
     * @param bearerToken      JWT that will be attached to every Kafka record header;
     *                         may be {@code null} for unauthenticated clusters
     */
    public record KafkaConfig(String bootstrapServers, String bearerToken) {}

    private KafkaConfig kafkaConfig;

    public AcpServerClient(String baseUrl) {
        this.baseUrl = baseUrl == null ? "http://localhost:8080" : baseUrl.replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Register client. Response includes token and refreshToken.
     */
    public ClientRegistrationDto.Response register(ClientRegistrationDto.Request req, String bearerToken)
            throws Exception {
        String json = mapper.writeValueAsString(req);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/client/register"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearerToken != null && !bearerToken.isEmpty())
            b.header("Authorization", "Bearer " + bearerToken);

        HttpResponse<String> r = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400)
            throw new RuntimeException("Register failed: " + r.statusCode() + " " + r.body());
        return mapper.readValue(r.body(), ClientRegistrationDto.Response.class);
    }

    /**
     * Refresh token. Returns new access token and expiresInMs.
     */
    public TokenRefreshDto.Response refreshToken(TokenRefreshDto.Request req) throws Exception {
        String json = mapper.writeValueAsString(req);
        HttpResponse<String> r = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/auth/token/refresh"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400)
            throw new RuntimeException("Token refresh failed: " + r.statusCode() + " " + r.body());
        return mapper.readValue(r.body(), TokenRefreshDto.Response.class);
    }

    /**
     * POST /api/vocabulary with List of ClassModelDto. acp-server expects
     * List&lt;ClassModel&gt; (same JSON shape).
     */
    public int postVocabulary(List<ClassModel> list, String bearerToken) throws Exception {
        return postVocabulary(list, bearerToken, null);
    }

    public int postVocabulary(List<ClassModel> list, String bearerToken, String idempotencyKey) throws Exception {
        if (list == null || list.isEmpty())
            return 0;
        String json = mapper.writeValueAsString(list);
        return post("/api/vocabulary", json, bearerToken, idempotencyKey);
    }

    /**
     * POST /api/vocabulary/rules/process with rule map (eventName, ruleName,
     * ruleDescription, payload).
     */
    public int postRule(Map<String, Object> ruleMap, String bearerToken) throws Exception {
        return postRule(ruleMap, bearerToken, null);
    }

    public int postRule(Map<String, Object> ruleMap, String bearerToken, String idempotencyKey) throws Exception {
        if (ruleMap == null)
            return 0;
        String json = mapper.writeValueAsString(ruleMap);
        return post("/api/vocabulary/rules/process", json, bearerToken, idempotencyKey);
    }

    /**
     * POST /api/event with List of EventCaptureDto. acp-server expects
     * List&lt;EventCapture&gt; (same JSON shape).
     */
    public int postEventCaptures(List<EventCapture> list, String bearerToken) throws Exception {
        return postEventCaptures(list, bearerToken, null);
    }

    public int postEventCaptures(List<EventCapture> list, String bearerToken, String idempotencyKey) throws Exception {
        if (list == null || list.isEmpty())
            return 0;
        String json = mapper.writeValueAsString(list);
        return post("/api/event", json, bearerToken, idempotencyKey);
    }

    // -------------------------------------------------------------------------
    // Kafka path
    // -------------------------------------------------------------------------

    /**
     * Configures (or replaces) the Kafka producer settings used by
     * {@link #postEventCapturesViaKafka}. Call this once during initialisation.
     *
     * @param config bootstrap servers + optional bearer token for Kafka headers
     */
    public synchronized void configureKafka(KafkaConfig config) {
        this.kafkaConfig = config;
        // Tear down any existing producer so it is rebuilt with the new config
        if (kafkaProducer != null) {
            kafkaProducer.close();
            kafkaProducer = null;
        }
    }

    /**
     * Sends each {@link EventCapture} individually to the {@value #EVENTS_TOPIC}
     * Kafka topic.
     *
     * <p>Partition assignment: {@code abs(subjectId.hashCode()) % NUM_PARTITIONS}
     * — keeps all events for the same subject on the same partition, preserving
     * order. Falls back to partition 0 when subjectId is absent.
     *
     * <p>Message key format: {@code tenantId:eventId:subjectId} (matches the
     * server-side consumer's parsing logic).
     *
     * <p>An {@code Authorization: Bearer <token>} header is attached to every
     * record when a bearer token is configured, so the server-side consumer can
     * validate the JWT on receipt.
     *
     * @param list        events to publish
     * @param bearerToken JWT used for Kafka record headers (may be null)
     * @return number of records successfully sent
     * @throws IllegalStateException if {@link #configureKafka} was not called first
     */
    public int postEventCapturesViaKafka(List<EventCapture> list, String bearerToken) throws Exception {
        if (list == null || list.isEmpty()) return 0;

        KafkaProducer<String, EventCapture> producer = getOrCreateProducer();
        int sent = 0;

        for (EventCapture capture : list) {
            // --- Derive subjectId from the capture (stored in correlationId or id) ---
            String subjectId = capture.getCorrelationId() != null
                    ? capture.getCorrelationId()
                    : (capture.getId() != null ? capture.getId() : "unknown");

            String tenantId = capture.getTenantId() != null ? capture.getTenantId() : "unknown";

            // Event name stored on the nested Event; fall back to capture id
            String eventId = (capture.getEvent() != null && capture.getEvent().getName() != null)
                    ? capture.getEvent().getName()
                    : (capture.getId() != null ? capture.getId() : "event");

            // Partition = abs(subjectId.hashCode()) % NUM_PARTITIONS
            int partition = Math.abs(subjectId.hashCode()) % NUM_PARTITIONS;

            // Key: tenantId:eventId:subjectId  (matches consumer's keyParts[0..2])
            String messageKey = tenantId + ":" + eventId + ":" + subjectId;

            ProducerRecord<String, EventCapture> record =
                    new ProducerRecord<>(EVENTS_TOPIC, partition, messageKey, capture);

            // Attach bearer token as an Authorization header on the Kafka record
            String token = bearerToken != null ? bearerToken
                    : (kafkaConfig != null ? kafkaConfig.bearerToken() : null);
            if (token != null && !token.isBlank()) {
                String headerValue = token.startsWith("Bearer ") ? token : "Bearer " + token;
                record.headers().add(new RecordHeader(
                        "Authorization", headerValue.getBytes(StandardCharsets.UTF_8)));
            }

            Future<RecordMetadata> future = producer.send(record);
            future.get(); // synchronous send — consistent with HTTP path behaviour
            sent++;
        }

        return sent;
    }

    /** Lazily creates the Kafka producer; thread-safe via double-checked locking. */
    private KafkaProducer<String, EventCapture> getOrCreateProducer() {
        if (kafkaProducer == null) {
            synchronized (producerLock) {
                if (kafkaProducer == null) {
                    if (kafkaConfig == null)
                        throw new IllegalStateException(
                                "Kafka is not configured. Call configureKafka() before postEventCapturesViaKafka().");
                    kafkaProducer = buildProducer(kafkaConfig);
                }
            }
        }
        return kafkaProducer;
    }

    /**
     * Builds a {@link KafkaProducer} for {@link EventCapture} values.
     *
     * <p>Producer settings:
     * <ul>
     *   <li>{@code acks=all} — leader + all in-sync replicas acknowledge before
     *       success (strongest durability guarantee).
     *   <li>{@code enable.idempotence=true} — exactly-once within a single
     *       producer session; prevents duplicate records on retries.
     *   <li>{@code retries=3} — automatic retry on transient failures.
     *   <li>{@code linger.ms=0} — no artificial batching delay; records are sent
     *       immediately (matches synchronous HTTP-like usage pattern).
     *   <li>{@code batch.size=16384} — standard 16 KB batch ceiling.
     *   <li>{@code compression.type=lz4} — lightweight compression reduces
     *       network overhead for JSON payloads.
     * </ul>
     */
    private static KafkaProducer<String, EventCapture> buildProducer(KafkaConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventCaptureKafkaSerializer.class.getName());

        // Durability + idempotency
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Throughput / latency
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);      // no batching delay
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384); // 16 KB
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        // Timeouts
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60_000);

        return new KafkaProducer<>(props);
    }

    private int post(String path, String json, String bearerToken, String idempotencyKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearerToken != null && !bearerToken.isEmpty())
            b.header("Authorization", "Bearer " + bearerToken);
        if (idempotencyKey != null && !idempotencyKey.isBlank())
            b.header("X-Idempotency-Key", idempotencyKey);

        HttpResponse<String> r = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        // 409 Conflict is safely returned if the idiosyncrasy filter blocks as duplicate
        if (r.statusCode() >= 400 && r.statusCode() != 409) {
            throw new RuntimeException("POST " + path + " failed: " + r.statusCode() + " " + r.body());
        }
        return r.statusCode();
    }

    /** Closes the underlying Kafka producer if one was created. */
    @Override
    public void close() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
            kafkaProducer = null;
        }
    }
}
