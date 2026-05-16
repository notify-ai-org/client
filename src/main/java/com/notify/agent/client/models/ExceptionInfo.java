package com.notify.agent.client.models;

import lombok.Data;


@Data
public class ExceptionInfo {
    private String exceptionType; // java.lang.NullPointerException
    
    private String message; // exception message
    
    private String stackTrace; // full stack trace as string
}
