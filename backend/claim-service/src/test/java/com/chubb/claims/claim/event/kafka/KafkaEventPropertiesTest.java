package com.chubb.claims.claim.event.kafka;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventPropertiesTest {

    @Test
    void everythingHasADefault() {
        KafkaEventProperties properties = new KafkaEventProperties(null, null, null, null, null, null, null);

        assertThat(properties.topic()).isEqualTo("claim-events");
        assertThat(properties.deadLetterTopic()).isEqualTo("claim-events.DLT");
        assertThat(properties.partitions()).isEqualTo(3);
        assertThat(properties.replicationFactor()).isEqualTo(1);
        assertThat(properties.batchSize()).isEqualTo(100);
        assertThat(properties.sendTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.retryInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.maxRetries()).isEqualTo(3);
    }

    @Test
    void configuredValuesAreKept() {
        KafkaEventProperties properties = new KafkaEventProperties("events", 6, 3, 10, Duration.ofSeconds(2),
                Duration.ofMillis(50), 0);

        assertThat(properties.topic()).isEqualTo("events");
        assertThat(properties.deadLetterTopic()).isEqualTo("events.DLT");
        assertThat(properties.partitions()).isEqualTo(6);
        assertThat(properties.replicationFactor()).isEqualTo(3);
        assertThat(properties.batchSize()).isEqualTo(10);
        assertThat(properties.sendTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.retryInterval()).isEqualTo(Duration.ofMillis(50));
        assertThat(properties.maxRetries()).isZero();
    }

    @Test
    void nonsenseCountsFallBackToTheDefaults() {
        KafkaEventProperties properties = new KafkaEventProperties(" ", 0, -1, 0, null, null, -5);

        assertThat(properties.topic()).isEqualTo("claim-events");
        assertThat(properties.partitions()).isEqualTo(3);
        assertThat(properties.replicationFactor()).isEqualTo(1);
        assertThat(properties.batchSize()).isEqualTo(100);
        assertThat(properties.maxRetries()).isEqualTo(3);
    }
}
