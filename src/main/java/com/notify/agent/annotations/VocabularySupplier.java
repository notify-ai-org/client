package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation for a method that returns the vocabulary/payload
 * for a particular event. The method is invoked when the event is processed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface VocabularySupplier {
    /** Event key/name this supplier provides payload for. */
    String event();
    String description() default "";
}
