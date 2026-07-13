package com.notify.agent.client.models.metadata;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.notify.agent.client.models.Event;

public class EventMetadata {
    private final Event event;
    private final String version;

    @JsonIgnore
    private final Method method;

    @JsonIgnore
    private final Class<?> declaringClass;

    public EventMetadata(Event event, String version, Method method, Class<?> declaringClass) {
        this.event = event;
        this.version = version != null ? version : "v1";
        this.method = method;
        this.declaringClass = declaringClass;
    }

    public Event getEvent() {
        return event;
    }

    public String getVersion() {
        return version;
    }

    @JsonIgnore
    public Method getMethod() {
        return method;
    }

    @JsonIgnore
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public String getMethodName() {
        return method != null ? method.getName() : null;
    }

    public String getDeclaringClassName() {
        return declaringClass != null ? declaringClass.getName() : null;
    }

    public String getReturnTypeName() {
        return method != null ? method.getReturnType().getName() : null;
    }

    public List<String> getParameterTypeNames() {
        if (method == null) {
            return List.of();
        }
        return Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .toList();
    }
}
