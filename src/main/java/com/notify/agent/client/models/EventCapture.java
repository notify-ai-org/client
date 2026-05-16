package com.notify.agent.client.models;

import com.notify.agent.client.models.dto.RuleResultDto;
import com.notify.agent.client.models.dto.SubjectResultDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.notify.agent.client.models.deserializers.FlatteningMapDeserializer;
import java.util.Map;



@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class EventCapture  {
    private String id;
    private String tenantId;
    private Instant timestamp;
    private String correlationId;

    @JsonDeserialize(using = FlatteningMapDeserializer.class)
    private Map<String, Object> payload;

    
    private Event event = new Event();

    // Execution data
    
    private CallStack callStack; // captured stack frames

    
    private ExecutionResult result; // success / failure

    
    private ExceptionInfo exception; // if failed

    // Performance
    private long durationMillis; // execution time

    private String serviceName; // service name

    // Agent outputs
    
    private String agentThoughtProcess;

    // We store the bullet points as a single comma-separated text string to natively match SQL rows
    
    private String bulletReasons;

    /** Lifecycle status of this capture. */
    private CaptureStatus status;

    // Subject and Rule execution results
    
    private SubjectResultDto subjectResult; // result from subject supplier

    
    private List<RuleResultDto> ruleResults; // results from rule executions

    public Instant getOccuredAt() {
        return this.timestamp;
    }

    public void setOccuredAt(Instant occuredAt) {
        this.timestamp = occuredAt;
    }
}