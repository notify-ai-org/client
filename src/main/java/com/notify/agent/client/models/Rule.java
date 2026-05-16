package com.notify.agent.client.models;

import lombok.Data;


@Data
public class Rule {
    
    private String id;

    private String name;

    private String eventName;         // Event this rule applies to

    private String description;

    private String conditionExpr;     // SpEL, MVEL, SQL-like DSL, etc.
    
    private boolean enabled;
    
    private int priority;             // resolve conflicts, tie-breaker

}
