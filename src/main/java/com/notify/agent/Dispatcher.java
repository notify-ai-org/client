package com.notify.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.NoOffsetForPartitionException;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.EventCapture;
import com.notify.agent.client.models.EventSchedule;
import com.notify.agent.client.models.subject.Subject;

/**
 * Thread that pulls records from the Buffer and sends them to acp-server (or
 * notification engine) based on RecordType. Applies routing by type only.
 * Fetches a new token via the provided supplier when the current one is expired
 * or 401.
 */
public class Dispatcher implements Runnable {

    private final Buffer buffer;
    private final AcpServerClient acpClient;
    private final TokenHolder tokenSupplier;
    private final Runnable onTokenExpired;
    private final EventListener eventListener;
    private volatile boolean running = true;
    private final String PRODUCER_TOPIC = "notify-v1-events";
    private final String CONSUMER_TOPIC = "notify-v1-scheduled-events";
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
    private final int partitions;

    private final KafkaConsumer<String, EventSchedule> consumer;
    private final KafkaProducer<String, EventCapture> producer;

    public Dispatcher(Buffer buffer, AcpServerClient acpClient,
            TokenHolder tokenSupplier, Runnable onTokenExpired,
            KafkaConsumer<String, EventSchedule> consumer, KafkaProducer<String, EventCapture> producer,
            EventListener eventListener) {
        this.buffer = buffer;
        this.acpClient = acpClient;
        this.tokenSupplier = tokenSupplier;
        this.onTokenExpired = onTokenExpired != null ? onTokenExpired : () -> {
        };
        this.consumer = consumer;
        this.producer = producer;
        this.eventListener = eventListener;
        this.partitions = producer.partitionsFor(PRODUCER_TOPIC).size();
    }

    private Thread consumerThread;

    public void stop() {
        running = false;
        if (consumerThread != null && consumerThread.isAlive()) {
            consumer.wakeup(); // Interrupt the consumer poll
            try {
                consumerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        producer.flush();
        producer.close();
    }

    @Override
    public void run() {
        List<Buffer.Record> batch = new ArrayList<>();
        List<EventCapture> eventBatch = new ArrayList<>();
        log.info("Dispatcher run() started");
        spawnConsumer();
        while (running) {
            try {
                int n = buffer.drainTo(batch, buffer.getBatchSize());
                if (n == 0) {
                    Thread.sleep(100);
                    continue;
                }
                log.debug("Drained {} record(s) from buffer", n);

                for (Buffer.Record r : batch) {
                    log.debug("Processing buffer record of type: {}", r.getType());
                    switch (r.getType()) {
                        case VOCABULARY:
                            @SuppressWarnings("unchecked")
                            List<ClassModel> vocab = (List<ClassModel>) r.getPayload();
                            log.debug("Posting {} vocabulary model(s) to acp-server", vocab.size());
                            postWithAuthRetry(() -> acpClient.postVocabulary(vocab, tokenSupplier.getToken()));
                            log.debug("Vocabulary post completed");
                            break;
                        case RULE:
                            @SuppressWarnings("unchecked")
                            Map<String, Object> rule = (Map<String, Object>) r.getPayload();
                            log.debug("Posting rule '{}' to acp-server", rule.get("name"));
                            postWithAuthRetry(() -> acpClient.postRule(rule, tokenSupplier.getToken()));
                            log.debug("Rule post completed");
                            break;
                        case EVENT_CAPTURE:
                            log.debug("Queuing EVENT_CAPTURE record into event batch");
                            eventBatch.add((EventCapture) r.getPayload());
                            break;
                    }
                }

                if (!eventBatch.isEmpty()) {
                    List<EventCapture> toSend = new ArrayList<>(eventBatch);
                    eventBatch.clear();
                    log.debug("Preparing to send {} event capture(s) to Kafka topic '{}'", toSend.size(),
                            PRODUCER_TOPIC);
                    for (EventCapture event : toSend) {
                        if (event.getSubjectResult() == null) {
                            String token = tokenSupplier.getHeaderToken();
                            if (token == null || token.isBlank()) {
                                log.debug("No Kafka header token available, falling back to access token");
                                token = tokenSupplier.getToken();
                            } else {
                                log.debug("Using Kafka header token for Authorization header");
                            }
                            String tenantId = extractTenantId(token);
                            String key = tenantId + ":" + event.getEvent().getName();
                            Integer partition = (Math.abs(Objects.hashCode(tenantId)) % partitions);
                            log.debug("Sending event '{}' to topic '{}', partition {}, key '{}'",
                                    event.getEvent().getName(), PRODUCER_TOPIC, partition, key);
                            ProducerRecord<String, EventCapture> record = new ProducerRecord<>(PRODUCER_TOPIC,
                                    partition, key, event);
                            if (token != null && !token.isBlank()) {
                                String headerValue = token.startsWith("Bearer ") ? token : "Bearer " + token;
                                record.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                                        "Authorization",
                                        headerValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                                log.debug("Authorization header injected into Kafka record");
                            } else {
                                log.debug("No auth token available; sending Kafka record without Authorization header");
                            }
                            producer.send(record);
                            log.debug("Kafka record sent for event '{}'", event.getEvent().getName());
                        } else {
                            int subjectCount = event.getSubjectResult().getSubjects().size();
                            log.debug("Event '{}' has {} subject(s); sending per-subject records",
                                    event.getEvent().getName(), subjectCount);
                            for (Subject subject : event.getSubjectResult().getSubjects()) {
                                String token = tokenSupplier.getHeaderToken();
                                if (token == null || token.isBlank()) {
                                    log.debug("No Kafka header token for subject '{}', falling back to access token",
                                            subject.getSubjectId());
                                    token = tokenSupplier.getToken();
                                } else {
                                    log.debug("Using Kafka header token for subject '{}'", subject.getSubjectId());
                                }
                                String tenantId = extractTenantId(token);
                                String key = tenantId + ":" + event.getEvent().getName() + ":"
                                        + subject.getSubjectId();
                                Integer partition = (Math.abs(Objects.hashCode(subject.getSubjectId())) % partitions);
                                log.debug("Sending event '{}' for subject '{}' to topic '{}', partition {}, key '{}'",
                                        event.getEvent().getName(), subject.getSubjectId(), PRODUCER_TOPIC, partition,
                                        key);
                                ProducerRecord<String, EventCapture> record = new ProducerRecord<>(PRODUCER_TOPIC,
                                        partition, key, event);
                                if (token != null && !token.isBlank()) {
                                    String headerValue = token.startsWith("Bearer ") ? token : "Bearer " + token;
                                    record.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                                            "Authorization",
                                            headerValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                                    log.debug("Authorization header injected for subject '{}'", subject.getSubjectId());
                                } else {
                                    log.debug("No auth token for subject '{}'; sending without Authorization header",
                                            subject.getSubjectId());
                                }
                                producer.send(record);
                                log.debug("Kafka record sent for event '{}', subject '{}'",
                                        event.getEvent().getName(), subject.getSubjectId());
                            }
                        }
                        Thread.sleep(100);
                    }
                    // postWithAuthRetry(() -> acpClient.postEventCaptures(toSend,
                    // tokenSupplier.get()));
                }

                log.debug("Batch of {} record(s) fully processed; marking buffer flushed", batch.size());
                buffer.markFlushed();
                batch.clear();
            } catch (InterruptedException e) {
                log.warn("Dispatcher run() interrupted; shutting down");
                Thread.currentThread().interrupt();
                running = false;
                break;
            } catch (Exception e) {
                log.error("Unhandled exception in Dispatcher run() loop; continuing", e);
            }
        }
    }

    private String extractTenantId(String token) {
        if (token == null || token.isEmpty())
            return "unknown-tenant";
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(payload);
                if (node.has("tenantId"))
                    return node.get("tenantId").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to extract tenantId from token", e);
        }
        return "unknown-tenant";
    }

    private void spawnConsumer() {
        // Subscribe to the topic and let Kafka's assignor handle partition distribution
        consumer.subscribe(Collections.singletonList(CONSUMER_TOPIC), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                log.info("Revoked partitions: {}", partitions);
                try {
                    // Synchronous fallback on rebalance
                    consumer.commitSync();
                } catch (Exception e) {
                    log.error("Failed to commit sync during partition revocation", e);
                }
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                log.info("Assigned partitions: {}", partitions);
            }

            @Override
            public void onPartitionsLost(Collection<TopicPartition> partitions) {
                log.info("Lost partitions: {}", partitions);
            }
        });

        log.info("Consumer thread spawned and subscribed to topic {}", CONSUMER_TOPIC);
        consumerThread = new Thread(() -> consumeMessages(consumer), "notify-kafka-consumer");
        consumerThread.start();
    }

    private void consumeMessages(KafkaConsumer<String, EventSchedule> consumer) {
        log.info("Kafka consumer thread started - polling for messages");
        try {
            while (running) {
                try {
                    ConsumerRecords<String, EventSchedule> records = consumer.poll(Duration.ofMillis(100));
                    if (!records.isEmpty()) {
                        for (ConsumerRecord<String, EventSchedule> record : records) {
                            String messageKey = record.key();
                            EventSchedule messageValue = record.value();
                            String topic = record.topic();
                            int partition = record.partition();
                            long offset = record.offset();

                            log.info("Record - Topic: {}, Partition: {}, Offset: {}, Key: {}, Value: {}",
                                    topic, partition, offset, messageKey, messageValue);
                            eventListener.onScheduledEvent(messageValue);
                        }

                    }
                } catch (OffsetOutOfRangeException | NoOffsetForPartitionException e) {
                    log.warn("Invalid or no offset found, and auto.reset.policy unset, using latest");
                    consumer.seekToEnd(e.partitions());
                    consumer.commitSync();
                } catch (Exception e) {
                    if (e instanceof org.apache.kafka.common.errors.WakeupException) {
                        log.info("Kafka consumer woke up");
                        break;
                    }
                    log.error("Error polling Kafka", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Fatal error in consumer thread, terminating thread.", t);
            if (running) {
                log.info("Respawning consumer thread...");
                try {
                    consumer.close();
                } catch (Exception ignore) {
                }
            }
        } finally {
            try {
                if (running) {
                    consumer.commitSync();
                }
            } catch (Exception e) {
                log.warn("Failed to commit synchronously on shutdown/exit", e);
            }
            try {
                consumer.close();
            } catch (Exception e) {
                log.warn("Failed to close consumer cleanly", e);
            }
        }
        log.info("Kafka consumer thread stopped");
    }

    @FunctionalInterface
    private interface PostOp {
        int run() throws Exception;
    }

    private void postWithAuthRetry(PostOp op) throws Exception {
        try {
            op.run();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                onTokenExpired.run();
                op.run();
            } else {
                throw e;
            }
        }
    }
}
