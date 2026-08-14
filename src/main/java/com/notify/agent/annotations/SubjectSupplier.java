package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation for a method that returns the list of subjects
 * (recipients) for a particular event. The method must return a type
 * assignable to java.util.List.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubjectSupplier {
    /** Event key/name this supplier provides subjects for. */
    String event();
    String description() default "";
}
