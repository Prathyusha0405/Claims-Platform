package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.repository.ClaimEventOutboxRepository;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.NotificationRepository;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.security.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Kafka publisher end to end, on a real (in-JVM) Kafka broker: the outbox, the relay, the topic, the consumer, the audit
 * rows and notifications they produce, and what happens to a message that cannot be recorded. Its own database, so it
 * cannot disturb the other integration tests.
 */
@SpringBootTest(properties = {
        "app.events.publisher=kafka",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:kafka-events;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.events.kafka.relay-delay-ms=100",
        "app.events.kafka.retry-interval=50ms",
        "app.events.kafka.max-retries=1"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
class KafkaEventPublishingIntegrationTest {

    private static final String TOPIC = "claim-events";
    private static final String DEAD_LETTER_TOPIC = "claim-events.DLT";
    private static final Duration PATIENCE = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ClaimEventPublisher publisher;

    @Autowired
    private ClaimHistoryRepository historyRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ClaimEventOutboxRepository outboxRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void theKafkaPublisherReplacesTheLocalOne() {
        assertThat(context.getBeansOfType(ClaimEventPublisher.class)).hasSize(1).containsKey("kafkaClaimEventPublisher");
        assertThat(context.getBeansOfType(ClaimEventOutboxRelay.class)).hasSize(1);
        assertThat(context.getBeansOfType(ClaimEventKafkaListener.class)).hasSize(1);
    }

    @Test
    void theWholeLifecycleIsRecordedFromTheTopic() throws Exception {
        long jane = randomUserId();
        long staffMember = randomUserId();
        String claimantToken = token(jane, Role.CLAIMANT);
        JsonNode created = createClaim(claimantToken);
        long claimId = created.get("id").asLong();
        String claimNumber = created.get("claimNumber").asText();

        action(staffMember, claimId, "assign", null).andExpect(status().isOk());
        action(staffMember, claimId, "request-info", "{\"message\":\"Please send the invoice\"}").andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/" + claimId + "/additional-info").header(HttpHeaders.AUTHORIZATION, claimantToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Invoice INV-9 attached\"}"))
                .andExpect(status().isOk());
        action(staffMember, claimId, "approve", "{\"comment\":\"Invoice matches\"}").andExpect(status().isOk());
        action(staffMember, claimId, "settle", "{\"comment\":\"Paid by bank transfer\"}").andExpect(status().isOk());

        // The claim itself changed in the request's own transaction, so it is current at once...
        mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, claimantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // ...while the audit trail and the notifications arrive from the topic, in order.
        await().atMost(PATIENCE).untilAsserted(() ->
                mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, claimantToken))
                        .andExpect(jsonPath("$.history[*].eventType").value(contains("CLAIM_CREATED", "CLAIM_ASSIGNED",
                                "ADDITIONAL_INFO_REQUESTED", "ADDITIONAL_INFO_RECEIVED", "CLAIM_APPROVED", "CLAIM_SETTLED")))
                        .andExpect(jsonPath("$.history[2].details").value("Please send the invoice"))
                        .andExpect(jsonPath("$.history[5].details").value("Paid by bank transfer"))
                        .andExpect(jsonPath("$.history[5].performedBy").value(staffMember)));

        await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(notificationRepository.findByRecipientId(jane, PageRequest.of(0, 20)).getContent())
                    .extracting(notification -> notification.getEventType())
                    .containsExactlyInAnyOrder(ClaimEventType.CLAIM_CREATED, ClaimEventType.CLAIM_ASSIGNED,
                            ClaimEventType.ADDITIONAL_INFO_REQUESTED, ClaimEventType.ADDITIONAL_INFO_RECEIVED,
                            ClaimEventType.CLAIM_APPROVED, ClaimEventType.CLAIM_SETTLED);
            assertThat(notificationRepository.findByRecipientId(staffMember, PageRequest.of(0, 20)).getContent())
                    .extracting(notification -> notification.getEventType())
                    .containsExactly(ClaimEventType.ADDITIONAL_INFO_RECEIVED);
        });

        // Every row remembers which event produced it, and the relay has emptied the outbox.
        assertThat(historyRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claimId))
                .extracting(ClaimHistory::getEventId).doesNotContainNull().doesNotHaveDuplicates();
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(outboxRepository.count()).isZero());
    }

    @Test
    void anEventDeliveredTwiceIsRecordedOnce() throws Exception {
        long jane = randomUserId();
        long staffMember = randomUserId();
        JsonNode created = createClaim(token(jane, Role.CLAIMANT));
        long claimId = created.get("id").asLong();
        String claimNumber = created.get("claimNumber").asText();

        ClaimEvent assigned = new ClaimEvent(ClaimEventType.CLAIM_ASSIGNED, claimId, claimNumber, jane, staffMember,
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, staffMember, "CLAIMS_STAFF", "Assigned twice", Instant.now());
        ClaimEvent marker = new ClaimEvent(ClaimEventType.CLAIM_APPROVED, claimId, claimNumber, jane, staffMember,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, staffMember, "CLAIMS_STAFF", "Marker", Instant.now().plusSeconds(1));

        // Kafka may deliver an event twice. Same key, so same partition and in order: the marker is handled after both copies.
        String key = String.valueOf(claimId);
        kafkaTemplate.send(TOPIC, key, objectMapper.writeValueAsString(assigned)).get();
        kafkaTemplate.send(TOPIC, key, objectMapper.writeValueAsString(assigned)).get();
        kafkaTemplate.send(TOPIC, key, objectMapper.writeValueAsString(marker)).get();

        await().atMost(PATIENCE).until(() -> historyRepository.existsByEventId(marker.id()));
        assertThat(historyRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claimId))
                .extracting(ClaimHistory::getEventId)
                .filteredOn(assigned.id()::equals)
                .hasSize(1);
        assertThat(notificationRepository.findByRecipientId(jane, PageRequest.of(0, 20)).getContent())
                .filteredOn(notification -> notification.getEventType() == ClaimEventType.CLAIM_ASSIGNED)
                .hasSize(1);
    }

    @Test
    void aMessageThatIsNotAClaimEventIsParkedOnTheDeadLetterTopic() throws Exception {
        String garbage = "this is not json " + UUID.randomUUID();

        kafkaTemplate.send(TOPIC, "bad", garbage).get();

        awaitRecordOn(DEAD_LETTER_TOPIC, record -> garbage.equals(record.value()));
    }

    @Test
    void anEventThatCannotBeRecordedIsRetriedAndThenParkedOnTheDeadLetterTopic() throws Exception {
        long missingClaim = 987_654_321L;
        ClaimEvent orphan = new ClaimEvent(ClaimEventType.CLAIM_APPROVED, missingClaim, "CLM-9999-000000", 1L, 2L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 2L, "CLAIMS_STAFF", "For a claim that does not exist", Instant.now());

        kafkaTemplate.send(TOPIC, String.valueOf(missingClaim), objectMapper.writeValueAsString(orphan)).get();

        awaitRecordOn(DEAD_LETTER_TOPIC, record -> record.value().contains("\"claimId\":" + missingClaim));
        assertThat(historyRepository.existsByEventId(orphan.id())).isFalse();
    }

    @Test
    void aClaimChangeThatRollsBackLeavesNothingInTheOutbox() throws Exception {
        JsonNode created = createClaim(token(randomUserId(), Role.CLAIMANT));
        ClaimEvent event = new ClaimEvent(ClaimEventType.CLAIM_ASSIGNED, created.get("id").asLong(),
                created.get("claimNumber").asText(), 1L, 2L, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, 2L,
                "CLAIMS_STAFF", "Rolled back", Instant.now());

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            publisher.publish(event);
            transaction.setRollbackOnly();
        });

        assertThat(outboxRepository.findAll()).noneMatch(row -> row.getEventId().equals(event.id()));
    }

    @Test
    void anEventCannotBeStagedOutsideTheClaimsTransaction() {
        ClaimEvent event = new ClaimEvent(ClaimEventType.CLAIM_ASSIGNED, 1L, "CLM-2026-000001", 1L, 2L,
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, 2L, "CLAIMS_STAFF", "No transaction", Instant.now());

        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Waits for a record on the topic that matches; each call reads the topic from the start with its own group. */
    private void awaitRecordOn(String topic, Predicate<ConsumerRecord<String, String>> matches) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", embeddedKafka);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(properties,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            await().atMost(PATIENCE).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (matches.test(record)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private JsonNode createClaim(String claimantToken) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, claimantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"policyNumber":"POL-KAFKA-%s","claimType":"AUTO","market":"SG","incidentDate":"2026-10-15",
                         "description":"Rear-ended at a junction","claimAmount":2100.00}"""
                        .formatted(UUID.randomUUID().toString().substring(0, 8))));
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions action(long staffId, long claimId, String action, String body) throws Exception {
        var request = post("/api/internal/claims/" + claimId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, token(staffId, Role.CLAIMS_STAFF));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private String token(long userId, Role role) {
        return "Bearer " + TestJwt.token(jwtSecret, userId, role);
    }

    private static long randomUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }
}
