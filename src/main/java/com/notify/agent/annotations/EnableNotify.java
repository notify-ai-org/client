package com.notify.agent.annotations;

import com.notify.agent.config.NotifyAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation to enable the Notification Engine SDK.
 * Place on a {@code @Configuration} class. The base package is scanned for
 * {@code @Event}, {@code @Rule}, {@code @Callback}, {@code @Vocabulary},
 * {@code @Model}, {@code @VocabularySupplier}, and {@code @SubjectSupplier}.
 *
 * Imports the SDK configuration explicitly so startup does not depend on
 * auto-configuration discovery metadata being copied by the build or IDE.
 * Set notify.ai.properties.base-package in application.yml or basePackage here.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ImportAutoConfiguration(NotifyAutoConfiguration.class)
public @interface EnableNotify {
    /**
     * Base package to scan for notification-related annotations.
     */
    String basePackage() default "";
}
