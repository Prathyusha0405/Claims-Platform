package com.chubb.claims.claim.event;

import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.security.Role;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Takes the claims staff to be everyone who has acted on a claim as {@link Role#CLAIMS_STAFF}, read from the audit
 * trail claim-service already keeps. That is a stand-in for a real directory, with one known gap: a staff member who
 * has never taken a claim is not known yet, so is not told about new claims until they do. Staff accounts are created
 * outside the application (demo seeding), so for now that only affects an account added after the seed.
 */
@Component
public class HistoryClaimsStaffDirectory implements ClaimsStaffDirectory {

    private final ClaimHistoryRepository claimHistoryRepository;

    public HistoryClaimsStaffDirectory(ClaimHistoryRepository claimHistoryRepository) {
        this.claimHistoryRepository = claimHistoryRepository;
    }

    @Override
    public List<Long> claimsStaffIds() {
        return claimHistoryRepository.findActorIdsByRole(Role.CLAIMS_STAFF.name());
    }
}
