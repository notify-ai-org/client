package com.notify.agent.config;

import com.notify.agent.*;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.notify.agent.annotations.EnableNotify;
import com.notify.agent.service.JwtService;

/**
 * Spring Boot auto-configuration for the Notification Engine Client SDK.
 * Enable with @EnableNotify on a @Configuration class and set
 * notify.base-package
 * (or @EnableNotify(basePackage="...") when found on a config bean).
 */
@Configuration
@ConditionalOnClass(EnableNotify.class)
@EnableConfigurationProperties(NotifyProperties.class)
@org.springframework.context.annotation.Import(com.notify.agent.config.KafkaConfig.class)
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AnnotationProcessor annotationProcessor(NotifyProperties props, ApplicationContext ctx) {
        String pkg = props.getBasePackage();
        try {
            var beans = ctx.getBeansWithAnnotation(EnableNotify.class);
            for (Object b : beans.values()) {
                EnableNotify en = b.getClass().getAnnotation(EnableNotify.class);
                if (en != null && en.basePackage() != null && !en.basePackage().isEmpty()) {
                    pkg = en.basePackage();
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return new AnnotationProcessor(pkg);
    }

    @Bean
    @ConditionalOnMissingBean
    public VocabularyManager vocabularyManager(AnnotationProcessor annotationProcessor) {
        return new VocabularyManager(annotationProcessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public Buffer buffer(NotifyProperties props) {
        return new Buffer(props.getBufferBatchSize(), props.getBufferFlushTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public AcpServerClient acpServerClient(NotifyProperties props) {
        return new AcpServerClient(props.getAcpServerUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenHolder tokenHolder() {
        return new TokenHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsManager metricsManager() {
        return new MetricsManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public InvokeManager invokeManager(ApplicationContext applicationContext, MetricsManager metricsManager) {
        return new InvokeManager(applicationContext, metricsManager);
    }

    @Bean(initMethod = "bootstrap")
    @ConditionalOnMissingBean
    public Bootstrapper bootstrapper(NotifyProperties props,
            AnnotationProcessor annotationProcessor,
            VocabularyManager vocabularyManager,
            Buffer buffer,
            JwtService jwtService,
            AcpServerClient acpServerClient,
            TokenHolder tokenHolder,
            InvokeManager invokeManager,
            com.notify.agent.config.KafkaConfig kafkaConfig,
            MetricsManager metricsManager,
            EventListener eventListener) {
        return new Bootstrapper(props, annotationProcessor, vocabularyManager, buffer,
                acpServerClient, tokenHolder, invokeManager, kafkaConfig, jwtService, metricsManager, eventListener);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventListener eventListener(Buffer buffer, InvokeManager invokeManager, MetricsManager metricsManager,
            VocabularyManager vocabularyManager) {
        return new EventListener(buffer, invokeManager, metricsManager, vocabularyManager);
    }
}
