package com.notify.agent.client.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
public class Event  {
    private String id;

    private String name; // e.g. "ORDER_PLACED", "PAYMENT_FAILED"

    private String description; // event description

    private int priority; // resolve conflicts, tie-breaker

    private EventStatus status; // NEW, PROCESSED, FAILED

    private String scheduleIntent;

    private String preferredTimeWindow;

    private String eventType;

    // One-to-many relationship with EventCapture
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    
    private List<EventCapture> eventCaptures;

    // Constructors
    public enum EventStatus {
        NEW,
        PROCESSED,
        FAILED
    }

}
