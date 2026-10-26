package com.chubb.claims.claim.event.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the Kafka publisher ({@code app.events.kafka.*}); only used when {@code app.events.publisher=kafka}.
 *
 * @param topic             the topic claim events are sent to (default claim-events)
 * @param partitions        partitions of that topic when this service creates it (default 3); events are keyed by claim id,
 *                          so one claim's events stay in order whatever the count
 * @param replicationFactor replicas of the topics this service creates (default 1, one broker)
 * @param batchSize         most outbox rows the relay sends per run (default 100)
 * @param sendTimeout       how long the relay waits for Kafka to acknowledge one event (default 5s)
 * @param retryInterval     pause before the consumer retries an event that failed to be recorded (default 1s)
 * @param maxRetries        retries before the event is parked on the dead-letter topic (default 3)
 */
@ConfigurationProperties(prefix = "app.events.kafka")
public record KafkaEventProperties(String topic, Integer partitions, Integer replicationFactor, Integer batchSize,
                                   Duration sendTimeout, Duration retryInterval, Integer maxRetries) {

    public KafkaEventProperties {
        topic = topic == null || topic.isBlank() ? "claim-events" : topic;
        partitions = positiveOrDefault(partitions, 3);
        replicationFactor = positiveOrDefault(replicationFactor, 1);
        batchSize = positiveOrDefault(batchSize, 100);
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(5) : sendTimeout;
        retryInterval = retryInterval == null ? Duration.ofSeconds(1) : retryInterval;
        maxRetries = maxRetries == null || maxRetries < 0 ? 3 : maxRetries;
    }

    /** Events that could not be recorded end up here instead of being lost. */
    public String deadLetterTopic() {
        return topic + ".DLT";
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
