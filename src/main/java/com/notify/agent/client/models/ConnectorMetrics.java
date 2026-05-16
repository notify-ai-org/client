package com.notify.agent.client.models;

import java.util.concurrent.atomic.AtomicLong;

public class ConnectorMetrics {

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong retried = new AtomicLong();

    public void markSent() { sent.incrementAndGet(); }
    public void markFailed() { failed.incrementAndGet(); }
    public void markRetried() { retried.incrementAndGet(); }

    public long sent() { return sent.get(); }
    public long failed() { return failed.get(); }
    public long retried() { return retried.get(); }
}
