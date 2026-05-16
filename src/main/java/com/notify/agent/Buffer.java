package com.notify.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.EventCapture;

/**
 * Manages a batch of records to be sent to acp-server. Flushes when either
 * batch size or timeout is reached. Configurable batch size and flush timeout.
 */
public class Buffer {

    public enum RecordType {
        VOCABULARY, // payload: List<ClassModelDto>
        RULE, // payload: Map with eventName, ruleName, ruleDescription, payload
        EVENT_CAPTURE // payload: EventCaptureDto
    }

    public static final class Record {
        private final RecordType type;
        private final Object payload;

        public Record(RecordType type, Object payload) {
            if (type == null) throw new NullPointerException("RecordType must not be null");
            this.type = type;
            this.payload = payload;
        }

        public RecordType getType() {
            return type;
        }

        public Object getPayload() {
            return payload;
        }
    }

    private final BlockingQueue<Record> queue = new LinkedBlockingQueue<>();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final int batchSize;
    private final long flushTimeoutMs;
    private volatile long lastFlushAt;

    public Buffer(int batchSize, long flushTimeoutMs) {
        this.batchSize = batchSize <= 0 ? 100 : batchSize;
        this.flushTimeoutMs = flushTimeoutMs <= 0 ? 5_000 : flushTimeoutMs;
        this.lastFlushAt = System.currentTimeMillis();
    }

    public void add(RecordType type, Object payload) {
        queue.add(new Record(type, payload));
    }

    /**
     * Enqueues vocabulary models.
     * <p>If {@code list} is null or empty the call is a no-op.
     */
    public void addVocabulary(List<ClassModel> list) {
        if (list == null || list.isEmpty()) return;   // null-check BEFORE ArrayList copy
        add(RecordType.VOCABULARY, new ArrayList<>(list));
    }

    /**
     * Enqueues a rule descriptor.
     * <p>If both {@code ruleName} and {@code eventName} are null or blank the
     * call is a no-op. Individual null fields are normalised to empty strings.
     */
    public void addRule(String eventName, String ruleName, String ruleDescription,
            java.util.Map<String, Object> payload) {
        if (isBlank(ruleName) && isBlank(eventName)) return;
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("eventName",       eventName       != null ? eventName       : "");
        m.put("ruleName",        ruleName        != null ? ruleName        : "");
        m.put("ruleDescription", ruleDescription != null ? ruleDescription : "");
        if (payload != null)
            m.put("payload", payload);
        add(RecordType.RULE, m);
    }

    /**
     * Enqueues an event capture DTO.
     * <p>If {@code dto} is null the call is a no-op.
     */
    public void addEventCapture(EventCapture dto) {
        if (dto == null) return;
        add(RecordType.EVENT_CAPTURE, dto);
    }

    /**
     * Drain up to {@code max} records into {@code out}. Returns the number drained.
     *
     * <p>If {@code out} is null, returns 0 without modifying the queue.
     * If {@code max} is {@code <= 0} the effective ceiling is {@link #batchSize}.
     */
    public int drainTo(List<Record> out, int max) {
        if (out == null) return 0;                    // null-check BEFORE queue.drainTo
        return queue.drainTo(out, max <= 0 ? batchSize : max);
    }

    /**
     * Take one record, blocking until available. For use by a single dispatcher
     * thread.
     */
    public Record take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getFlushTimeoutMs() {
        return flushTimeoutMs;
    }

    public boolean shouldFlushBySize() {
        return queue.size() >= batchSize;
    }

    public boolean shouldFlushByTimeout() {
        return (System.currentTimeMillis() - lastFlushAt) >= flushTimeoutMs;
    }

    public void markFlushed() {
        lastFlushAt = System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
