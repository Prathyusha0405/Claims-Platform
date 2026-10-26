/**
 * The Kafka implementation of ClaimEventPublisher, switched on with app.events.publisher=kafka. The publisher saves each event
 * to an outbox table in the claim's own transaction; the relay sends it to Kafka; the listener records it (audit row and
 * notifications) from the topic.
 */
package com.chubb.claims.claim.event.kafka;
