package com.notify.agent.client.models.metadata;

import java.lang.reflect.Method;

public class SubjectSupplierMetadata {
    private final String event;
    private final String description;
    private final Method method;
    private final Class<?> declaringClass;

    public SubjectSupplierMetadata(String event, String description, Method method, Class<?> declaringClass) {
        this.event = event;
        this.description = description;
        this.method = method;
        this.declaringClass = declaringClass;
    }

    public String getEvent() { return event; }
    public String getDescription() { return description; }
    public Method getMethod() { return method; }
    public Class<?> getDeclaringClass() { return declaringClass; }
}
