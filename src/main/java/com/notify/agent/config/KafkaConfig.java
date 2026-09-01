package com.notify.agent.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka properties used by the standalone client SDK.
 *
 * <p>This configuration intentionally lives in the client artifact so the SDK
 * has no dependency on Notify.ai server modules.</p>
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers:pkc-41p56.asia-south1.gcp.confluent.cloud:9092}")
    private String bootstrapServers;

    @Value("${kafka.key-deserializer:org.apache.kafka.common.serialization.StringDeserializer}")
    private String keyDeserializer;

    @Value("${kafka.value-deserializer:org.apache.kafka.common.serialization.StringDeserializer}")
    private String valueDeserializer;

    @Value("${kafka.client-id:notify-ai-client}")
    private String clientId;

    @Value("${kafka.fetch-max-wait-ms:500}")
    private int fetchMaxWaitMs;

    @Value("${kafka.fetch-min-bytes:1}")
    private int fetchMinBytes;

    @Value("${kafka.fetch-max-bytes:52428800}")
    private int fetchMaxBytes;

    @Value("${kafka.max-partition-fetch-bytes:1048576}")
    private int maxPartitionFetchBytes;

    @Value("${kafka.max-message-bytes:1048588}")
    private int maxMessageBytes;

    @Value("${kafka.isolation-level:read_uncommitted}")
    private String isolationLevel;

    @Value("${kafka.enable-auto-commit:false}")
    private boolean enableAutoCommit;

    @Value("${kafka.heartbeat-interval-ms:3000}")
    private int heartbeatIntervalMs;

    @Value("${kafka.session-timeout-ms:45000}")
    private int sessionTimeoutMs;

    @Value("${kafka.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${kafka.group-id:vocab-agent-group}")
    private String groupId;

    @Value("${kafka.group-instance-id:}")
    private String groupInstanceId;

    @Value("${kafka.max-poll-interval-ms:300000}")
    private int maxPollIntervalMs;

    @Value("${kafka.max-poll-records:500}")
    private int maxPollRecords;

    @Value("${kafka.producer.key-serializer:org.apache.kafka.common.serialization.StringSerializer}")
    private String keySerializer;

    @Value("${kafka.producer.value-serializer:org.apache.kafka.common.serialization.StringSerializer}")
    private String valueSerializer;

    @Value("${kafka.producer.acks:all}")
    private String acks;

    @Value("${kafka.producer.retries:3}")
    private int retries;

    @Value("${kafka.producer.max-request-size:1048576}")
    private int maxRequestSize;

    @Value("${kafka.producer.batch-size:16384}")
    private int batchSize;

    @Value("${kafka.producer.linger-ms:5}")
    private int lingerMs;

    @Value("${kafka.producer.compression-type:snappy}")
    private String compressionType;

    @Value("${kafka.producer.buffer-memory:33554432}")
    private long bufferMemory;

    @Value("${kafka.producer.request-timeout-ms:30000}")
    private int requestTimeoutMs;

    @Value("${kafka.producer.delivery-timeout-ms:120000}")
    private int deliveryTimeoutMs;

    @Value("${kafka.producer.enable-idempotence:true}")
    private boolean enableIdempotence;

    @Value("${kafka.producer.transactional-id:}")
    private String transactionalId;

    @Value("${kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${kafka.security.sasl-mechanism:PLAIN}")
    private String saslMechanism;

    @Value("${kafka.security.sasl-jaas-config:}")
    private String saslJaasConfig;

    @Value("${kafka.security.ssl.truststore-location:}")
    private String sslTruststoreLocation;

    @Value("${kafka.security.ssl.truststore-password:}")
    private String sslTruststorePassword;

    @Value("${kafka.security.ssl.keystore-location:}")
    private String sslKeystoreLocation;

    @Value("${kafka.security.ssl.keystore-password:}")
    private String sslKeystorePassword;

    @Value("${kafka.security.ssl.key-password:}")
    private String sslKeyPassword;

    @Value("${kafka.security.ssl.truststore-type:JKS}")
    private String sslTruststoreType;

    @Value("${kafka.security.ssl.keystore-type:JKS}")
    private String sslKeystoreType;

    /** Returns a fresh map suitable for constructing the SDK Kafka consumer. */
    public Map<String, Object> consumerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        properties.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, fetchMaxBytes);
        properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, maxPartitionFetchBytes);
        properties.put("max.message.bytes", maxMessageBytes);
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        properties.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        properties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        if (hasText(groupInstanceId)) {
            properties.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, groupInstanceId);
        }
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        applySecurityProperties(properties);
        return properties;
    }

    /** Returns a fresh map suitable for constructing the SDK Kafka producer. */
    public Map<String, Object> producerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        properties.put(ProducerConfig.ACKS_CONFIG, acks);
        properties.put(ProducerConfig.RETRIES_CONFIG, retries);
        properties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence);
        if (hasText(transactionalId)) {
            properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        }
        applySecurityProperties(properties);
        return properties;
    }

    private void applySecurityProperties(Map<String, Object> properties) {
        if (!hasText(securityProtocol) || "PLAINTEXT".equalsIgnoreCase(securityProtocol)) {
            return;
        }

        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        if (hasText(saslMechanism)) {
            properties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }
        if (hasText(saslJaasConfig)) {
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        }

        if (securityProtocol.toUpperCase(java.util.Locale.ROOT).contains("SSL")) {
            if (hasText(sslTruststoreLocation)) {
                properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslTruststoreLocation);
                properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslTruststorePassword);
                properties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
            }
            if (hasText(sslKeystoreLocation)) {
                properties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreLocation);
                properties.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystorePassword);
                properties.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, sslKeyPassword);
                properties.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, sslKeystoreType);
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
