package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation indicating that all fields in the class are vocabulary
 * attributes. Each field should be annotated with @Vocabulary for name and description.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Model {
    /**
     * Optional description of the model.
     */
    String description() default "";
}
