package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation for a callback invoked before or after an event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Callback {
    /** Event key/name this callback is bound to. */
    String event();
    /** When to invoke: before or after the event is triggered. */
    When when();

    enum When { BEFORE, AFTER }
}
