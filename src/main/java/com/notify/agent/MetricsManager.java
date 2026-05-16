package com.notify.agent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects metrics from InvokeManager, EventListener, and other sources.
 * Can send them to acp-server via AcpServerClient when an /api/metrics endpoint exists.
 */
public class MetricsManager {

    private final AtomicLong ruleInvokeCount = new AtomicLong(0);
    private final AtomicLong vocabularySupplierInvokeCount = new AtomicLong(0);
    private final AtomicLong subjectSupplierInvokeCount = new AtomicLong(0);
    private final AtomicLong eventCaptureCount = new AtomicLong(0);
    private final AtomicLong totalRuleInvokeMs = new AtomicLong(0);
    private final AtomicLong totalVocabularySupplierMs = new AtomicLong(0);
    private final AtomicLong totalSubjectSupplierMs = new AtomicLong(0);

    public void recordRuleInvoke(String ruleName, long durationMs) {
        ruleInvokeCount.incrementAndGet();
        totalRuleInvokeMs.addAndGet(durationMs);
    }

    public void recordVocabularySupplierInvoke(String eventKey, long durationMs) {
        vocabularySupplierInvokeCount.incrementAndGet();
        totalVocabularySupplierMs.addAndGet(durationMs);
    }

    public void recordSubjectSupplierInvoke(String eventKey, long durationMs) {
        subjectSupplierInvokeCount.incrementAndGet();
        totalSubjectSupplierMs.addAndGet(durationMs);
    }

    public void recordEventCapture(String eventKey, long durationMs) {
        eventCaptureCount.incrementAndGet();
    }

    public long getRuleInvokeCount() { return ruleInvokeCount.get(); }
    public long getVocabularySupplierInvokeCount() { return vocabularySupplierInvokeCount.get(); }
    public long getSubjectSupplierInvokeCount() { return subjectSupplierInvokeCount.get(); }
    public long getEventCaptureCount() { return eventCaptureCount.get(); }

    /**
     * Send current snapshot to acp-server if it exposes POST /api/metrics.
     * No-op if client is null or endpoint absent.
     */
    public void sendToAcpServer(AcpServerClient client, String bearerToken) {
        if (client == null) return;
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("ruleInvokeCount", ruleInvokeCount.get());
            payload.put("vocabularySupplierInvokeCount", vocabularySupplierInvokeCount.get());
            payload.put("subjectSupplierInvokeCount", subjectSupplierInvokeCount.get());
            payload.put("eventCaptureCount", eventCaptureCount.get());
            payload.put("totalRuleInvokeMs", totalRuleInvokeMs.get());
            payload.put("totalVocabularySupplierMs", totalVocabularySupplierMs.get());
            payload.put("totalSubjectSupplierMs", totalSubjectSupplierMs.get());
            // AcpServerClient doesn't have postMetrics yet; we could add it.
            // For now we no-op. Client can be extended with postMetrics when acp-server supports it.
        } catch (Exception ignored) {}
    }
}
