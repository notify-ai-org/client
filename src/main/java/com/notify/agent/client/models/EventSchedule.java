package com.notify.agent.client.models;

import java.time.Instant;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = false)
public class EventSchedule  {
    private String id;

    private String eventName;

    private String description;

    private String triggerType;

    private Instant scheduledAt; // exact time (if delayed)

    private String cronExpression; // for recurring

}
