package com.notify.agent;

import com.notify.agent.annotations.EnableNotify;
import com.notify.agent.config.NotifyAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnableNotifyTest {

    @Test
    void annotationBootstrapsSdkWithoutAutoConfigurationDiscovery() throws Exception {
        assertBootstrapsOnce(false);
    }

    @Test
    void annotationAndAutoConfigurationDiscoveryBootstrapOnlyOnce() throws Exception {
        assertBootstrapsOnce(true);
    }

    private void assertBootstrapsOnce(boolean discoverAutoConfiguration) throws Exception {
        AnnotationProcessor processor = mock(AnnotationProcessor.class);
        AcpServerClient client = mock(AcpServerClient.class);
        // Exercise the real bootstrap lifecycle without contacting a server or starting workers.
        when(client.register(any(), isNull())).thenThrow(new IllegalStateException("Test server unavailable"));

        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(NotifyEnabledConfiguration.class)
                .withBean(AnnotationProcessor.class, () -> processor)
                .withBean(AcpServerClient.class, () -> client);
        if (discoverAutoConfiguration) {
            runner = runner.withConfiguration(AutoConfigurations.of(NotifyAutoConfiguration.class));
        }

        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Bootstrapper.class);
            verify(processor, times(1)).process();
            verify(client, times(1)).register(any(), isNull());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableNotify(basePackage = "com.notify.agent.fixtures.valid")
    static class NotifyEnabledConfiguration {
    }
}
