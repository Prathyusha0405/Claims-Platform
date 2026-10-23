package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.Notification;

import java.util.ArrayList;
import java.util.List;

/** Decides who is told what when a claim event happens. */
final class ClaimNotificationFactory {

    private ClaimNotificationFactory() {
    }

    static List<Notification> notificationsFor(ClaimEvent event) {
        List<Notification> notifications = new ArrayList<>();
        notifications.add(toClaimant(event, claimantMessage(event)));

        // The staff member working the claim needs to know the claimant has answered their request.
        if (event.type() == ClaimEventType.ADDITIONAL_INFO_RECEIVED && event.assignedStaffId() != null) {
            notifications.add(new Notification(
                    event.assignedStaffId(),
                    event.claimId(),
                    event.type(),
                    "The claimant has provided additional information for claim " + event.claimNumber() + ".",
                    event.occurredAt()));
        }
        return notifications;
    }

    private static Notification toClaimant(ClaimEvent event, String message) {
        return new Notification(event.claimantId(), event.claimId(), event.type(), message, event.occurredAt());
    }

    private static String claimantMessage(ClaimEvent event) {
        String claim = event.claimNumber();
        return switch (event.type()) {
            case CLAIM_CREATED -> "Your claim " + claim + " has been submitted successfully.";
            case CLAIM_ASSIGNED -> "Your claim " + claim + " has been assigned to a claims staff member and is under review.";
            case CLAIM_APPROVED -> "Your claim " + claim + " has been approved.";
            case CLAIM_REJECTED -> "Your claim " + claim + " has been rejected.";
            case ADDITIONAL_INFO_REQUESTED ->
                    "Additional information is required for your claim " + claim + ". Please respond with the requested details.";
            case ADDITIONAL_INFO_RECEIVED ->
                    "We have received the additional information for your claim " + claim + ". It is back under review.";
        };
    }
}
