package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface Event {
    /** Event key/name, e.g. "order.placed". Alias for name. */
    String key();

    /** Human-readable description. */
    String description() default "";

    String preferredTimeWindow();

    String eventType();

    String scheduleIntent();

    int priority();

    String version() default "v1";

    /** Optional payload type override. */
    Class<?> payload() default Void.class;
}
