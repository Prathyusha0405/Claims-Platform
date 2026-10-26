package com.chubb.claims.claim.event.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Wiring that exists only when {@code app.events.publisher=kafka}: the topics, the scheduler that runs the outbox relay, and
 * what the consumer does with an event it cannot record. With the default (local) publisher none of this is created and
 * the service never contacts a broker.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.events", name = "publisher", havingValue = "kafka")
@EnableScheduling
public class KafkaEventConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConfig.class);

    @Bean
    public NewTopic claimEventsTopic(KafkaEventProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    /** One partition is plenty for the few events that end up here. */
    @Bean
    public NewTopic claimEventsDeadLetterTopic(KafkaEventProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(1)
                .replicas(properties.replicationFactor())
                .build();
    }

    /**
     * An event that cannot be recorded (a database error, say) is retried a few times and then parked on the dead-letter topic
     * so the consumer moves on and nothing is silently dropped. An unreadable message skips the retries.
     */
    @Bean
    public DefaultErrorHandler claimEventErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                      KafkaEventProperties properties) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), -1));
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            log.error("Claim event {}-{}@{} could not be recorded; moving it to {}",
                    record.topic(), record.partition(), record.offset(), properties.deadLetterTopic(), exception);
            deadLetters.accept(record, exception);
        };
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.maxRetries()));
        handler.addNotRetryableExceptions(ClaimEventKafkaListener.UnreadableClaimEventException.class);
        return handler;
    }
}
