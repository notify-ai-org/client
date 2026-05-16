package com.notify.agent.client.models;

import lombok.Data;


@Data
public class ExecutionResult {
    private Boolean success;
    
    private String returnValue; // serialized return value
}
