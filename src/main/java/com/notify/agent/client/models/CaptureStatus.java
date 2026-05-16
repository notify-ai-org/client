package com.notify.agent.client.models;

/**
 * Lifecycle status of an {@link EventCapture}.
 */
public enum CaptureStatus {
    /** The capture has been received and is being processed by the agent. */
    PROCESSING,

    PROCESSED,

    /** A notification job was successfully dispatched for this capture. */
    DISPATCHED,

    /** The capture was suppressed by the agent (no notification needed). */
    SUPPRESSED,

    /** Processing failed with an unrecoverable error. */
    FAILED
}
