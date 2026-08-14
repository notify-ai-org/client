package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation for a rule executor. The method is invoked to evaluate
 * a rule for a given event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Rule {
    String name();
    String description() default "";
    /** Event key/name this rule applies to. */
    String event() default "";
}
