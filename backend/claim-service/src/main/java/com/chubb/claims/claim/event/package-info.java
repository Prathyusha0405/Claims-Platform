/**
 * Claim events: the ClaimEventPublisher abstraction, the handler that records an event (audit row and notifications), and
 * its implementations: LocalClaimEventPublisher (in-process, the default) and, in the kafka subpackage, the Kafka publisher.
 */
package com.chubb.claims.claim.event;
