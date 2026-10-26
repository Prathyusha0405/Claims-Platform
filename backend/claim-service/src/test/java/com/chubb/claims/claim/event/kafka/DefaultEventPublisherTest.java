package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.event.LocalClaimEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With no configuration at all the service behaves as it did before Kafka was added: events are handled in-process and
 * nothing Kafka-related runs, so no broker is needed to start or test it.
 */
@SpringBootTest
class DefaultEventPublisherTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theLocalPublisherIsTheDefault() {
        assertThat(context.getBeansOfType(ClaimEventPublisher.class)).hasSize(1).containsKey("localClaimEventPublisher");
        assertThat(context.getBean(ClaimEventPublisher.class)).isInstanceOf(LocalClaimEventPublisher.class);
    }

    @Test
    void nothingThatTalksToKafkaIsCreated() {
        assertThat(context.getBeansOfType(KafkaClaimEventPublisher.class)).isEmpty();
        assertThat(context.getBeansOfType(ClaimEventOutboxRelay.class)).isEmpty();
        assertThat(context.getBeansOfType(ClaimEventKafkaListener.class)).isEmpty();
        assertThat(context.getBeansOfType(KafkaEventConfig.class)).isEmpty();
        assertThat(context.getBeanProvider(KafkaListenerEndpointRegistry.class).getIfAvailable())
                .satisfiesAnyOf(registry -> assertThat(registry).isNull(),
                        registry -> assertThat(registry.getListenerContainers()).isEmpty());
    }
}
