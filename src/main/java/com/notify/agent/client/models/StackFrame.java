package com.notify.agent.client.models;

import lombok.Data;


@Data
public class StackFrame {
    private String className; // "com.notify.OrderService"
    private String methodName; // "createOrder"
    private int lineNumber; // line number in code
    private String fileName; // source file
}
