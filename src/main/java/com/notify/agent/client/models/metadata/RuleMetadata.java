package com.notify.agent.client.models.metadata;

import java.lang.reflect.Method;

public class RuleMetadata {
    private final String name;
    private final String description;
    private final String event;
    private final Method method;
    private final Class<?> declaringClass;

    public RuleMetadata(String name, String description, String event, Method method, Class<?> declaringClass) {
        this.name = name;
        this.description = description;
        this.event = event;
        this.method = method;
        this.declaringClass = declaringClass;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getEvent() { return event; }
    public Method getMethod() { return method; }
    public Class<?> getDeclaringClass() { return declaringClass; }
}
