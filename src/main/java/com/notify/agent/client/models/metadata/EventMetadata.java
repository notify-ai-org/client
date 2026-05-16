package com.notify.agent.client.models.metadata;

import java.lang.reflect.Method;

import com.notify.agent.client.models.Event;

public class EventMetadata {
    private final Event event;
    private final String version;
    private final Method method;
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

    public Method getMethod() {
        return method;
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }
}
