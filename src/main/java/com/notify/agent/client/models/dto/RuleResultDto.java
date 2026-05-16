package com.notify.agent.client.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the result of executing a rule-annotated method.
 * Contains the rule evaluation result and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleResultDto {

    /** Name of the rule that was executed */
    private String ruleName;

    /** Event key this rule was executed for */
    private String eventKey;

    /** Result returned by the rule method (typically Boolean or custom object) */
    private Object result;

    /** Execution time in milliseconds */
    private long executionTimeMillis;

    /** Whether the execution was successful */
    private boolean success;

    /** Error message if execution failed */
    private String errorMessage;
}
