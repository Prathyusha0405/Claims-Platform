package com.chubb.claims.claim.seed;

import com.chubb.claims.claim.dto.OfficerStatusTotal;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Notification;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.repository.NotificationRepository;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.security.TestJwt;
import com.chubb.claims.claim.service.ClaimNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full migration set (V1 schema + V2 demo data) on a private database. Checks the demo data is internally
 * consistent, matches the dashboard figures documented at the top of V2__seed_data.sql, and is readable by the app.
 */
@SpringBootTest(properties = {
        "spring.flyway.target=latest",
        "spring.datasource.url=jdbc:h2:mem:claimseedcheck;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SeedDataIntegrationTest {

    private static final Instant ANCHOR = Instant.parse("2026-10-22T06:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimHistoryRepository historyRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ClaimNumberGenerator claimNumberGenerator;

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---- volumes and mix --------------------------------------------------------------------------------------

    @Test
    void theDocumentedVolumesAreLoaded() {
        assertThat(count("SELECT COUNT(*) FROM claims")).isEqualTo(250);
        assertThat(count("SELECT COUNT(*) FROM claim_history")).isEqualTo(500);
        assertThat(count("SELECT COUNT(*) FROM notifications")).isEqualTo(200);
    }

    @Test
    void everyLifecycleStatusIsRepresented() {
        assertThat(grouped("SELECT status AS k, COUNT(*) AS n FROM claims GROUP BY status")).containsOnly(
                Map.entry("SUBMITTED", 100L), Map.entry("UNDER_REVIEW", 56L), Map.entry("MORE_INFO_REQUIRED", 12L),
                Map.entry("APPROVED", 28L), Map.entry("REJECTED", 18L), Map.entry("SETTLED", 36L));
    }

    @Test
    void motorAndPropertyClaimsAreSplitAsDocumented() {
        assertThat(grouped("SELECT claim_type AS k, COUNT(*) AS n FROM claims GROUP BY claim_type"))
                .containsOnly(Map.entry("AUTO", 146L), Map.entry("HOME", 104L));
    }

    @Test
    void claimsSpanTheSixCountriesAndPolicyNumbersAgreeWithTheCountry() {
        Map<String, Long> byCountry = new LinkedHashMap<>();
        Map<String, String> codes = Map.of("Australia", "AU", "Singapore", "SG", "Hong Kong", "HK", "Japan", "JP", "Malaysia", "MY", "New Zealand", "NZ");
        codes.forEach((country, code) -> {
            byCountry.put(country, count("SELECT COUNT(*) FROM claims WHERE incident_location LIKE '%, " + country + "'"));
            assertThat(count("SELECT COUNT(*) FROM claims WHERE incident_location LIKE '%, " + country + "' AND policy_number NOT LIKE '%-" + code + "-%'"))
                    .as("policy numbers of %s claims carry %s", country, code).isZero();
        });

        assertThat(byCountry).containsOnly(Map.entry("Australia", 70L), Map.entry("Singapore", 50L), Map.entry("Hong Kong", 30L),
                Map.entry("Japan", 40L), Map.entry("Malaysia", 35L), Map.entry("New Zealand", 25L));
    }

    @Test
    void policyNumbersLookLikeRealPoliciesAndAreSharedAcrossAClaimantsClaims() {
        assertThat(count("SELECT COUNT(*) FROM claims WHERE policy_number NOT REGEXP '^(MTR|PRP)-(AU|SG|HK|JP|MY|NZ)-20(24|25)-[0-9]{6}$'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE (claim_type = 'AUTO' AND policy_number NOT LIKE 'MTR-%') "
                + "OR (claim_type = 'HOME' AND policy_number NOT LIKE 'PRP-%')")).isZero();
        assertThat(count("SELECT COUNT(DISTINCT policy_number) FROM claims")).isEqualTo(40);   // 20 claimants x (motor + property)
    }

    // ---- claim data quality -----------------------------------------------------------------------------------

    @Test
    void claimNumbersAreUniqueAndFollowTheApplicationsOwnScheme() {
        assertThat(count("SELECT COUNT(DISTINCT claim_number) FROM claims")).isEqualTo(250);
        assertThat(count("SELECT COUNT(*) FROM claims WHERE claim_number NOT REGEXP '^CLM-20[0-9]{2}-[0-9]{6}$'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE RIGHT(claim_number, 6) <> LPAD(CAST(id AS VARCHAR), 6, '0')")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE SUBSTRING(claim_number, 5, 4) <> CAST(EXTRACT(YEAR FROM created_at) AS VARCHAR)")).isZero();
    }

    @Test
    void datesAreCoherent() {
        assertThat(count("SELECT COUNT(*) FROM claims WHERE incident_date > CAST(created_at AS DATE)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE updated_at < created_at")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE updated_at > TIMESTAMP WITH TIME ZONE '2026-10-22 06:00:00+00:00'")).isZero();
        assertThat(jdbc.queryForObject("SELECT MIN(created_at) FROM claims", java.time.OffsetDateTime.class).toInstant())
                .isAfter(Instant.parse("2025-10-01T00:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT MAX(created_at) FROM claims", java.time.OffsetDateTime.class).toInstant()).isBefore(ANCHOR);
    }

    @Test
    void amountsAreRealisticAndTwoDecimalPlaces() {
        assertThat(jdbc.queryForObject("SELECT MIN(claim_amount) FROM claims", BigDecimal.class)).isGreaterThanOrEqualTo(new BigDecimal("500"));
        assertThat(jdbc.queryForObject("SELECT MAX(claim_amount) FROM claims", BigDecimal.class)).isLessThan(new BigDecimal("200000"));
        assertThat(count("SELECT COUNT(*) FROM claims WHERE claim_type = 'AUTO' AND claim_amount > 50000")).isZero();
    }

    @Test
    void aClaimIsUnassignedExactlyWhileItIsSubmitted() {
        assertThat(count("SELECT COUNT(*) FROM claims WHERE status = 'SUBMITTED' AND assigned_officer_id IS NOT NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE status <> 'SUBMITTED' AND assigned_officer_id IS NULL")).isZero();
    }

    @Test
    void claimsReferenceTheRealStaffAndClaimantIdsFromAuthServicesSeed() {
        assertThat(count("SELECT COUNT(*) FROM claims WHERE assigned_officer_id IS NOT NULL AND assigned_officer_id NOT BETWEEN 2 AND 6")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE claimant_id NOT BETWEEN 7 AND 26")).isZero();
        assertThat(count("SELECT COUNT(DISTINCT claimant_id) FROM claims")).isEqualTo(20);
        assertThat(count("SELECT COUNT(DISTINCT assigned_officer_id) FROM claims")).isEqualTo(5);
        // claimants file a realistic number of claims, nobody dominates
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM claims GROUP BY claimant_id", Long.class)).allSatisfy(n -> assertThat(n).isBetween(8L, 16L));
    }

    // ---- dashboard figures ------------------------------------------------------------------------------------

    @Test
    void dashboardFiguresMatchTheOnesDocumentedInTheSeedFile() {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        Map<ClaimStatus, BigDecimal> amounts = new EnumMap<>(ClaimStatus.class);
        for (StatusTotal total : claimRepository.totalsByStatus()) {
            counts.put(total.status(), total.claimCount());
            amounts.put(total.status(), total.totalAmount());
        }

        long pending = counts.get(ClaimStatus.SUBMITTED) + counts.get(ClaimStatus.UNDER_REVIEW) + counts.get(ClaimStatus.MORE_INFO_REQUIRED);
        BigDecimal exposure = amounts.entrySet().stream().filter(e -> e.getKey() != ClaimStatus.SETTLED)
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(250);   // total claims
        assertThat(pending).isEqualTo(168);                                                      // pending claims
        assertThat(counts.get(ClaimStatus.APPROVED)).isEqualTo(28);                              // approved claims
        assertThat(counts.get(ClaimStatus.REJECTED)).isEqualTo(18);                              // rejected claims
        assertThat(counts.get(ClaimStatus.SETTLED)).isEqualTo(36);
        assertThat(exposure).isEqualByComparingTo("2674303.50");                                  // outstanding liability exposure
        assertThat(amounts.get(ClaimStatus.SETTLED)).isEqualByComparingTo("480955.50");           // excluded from exposure
        assertThat(amounts.get(ClaimStatus.SUBMITTED)).isEqualByComparingTo("850718.00");
        assertThat(amounts.get(ClaimStatus.UNDER_REVIEW)).isEqualByComparingTo("1033466.00");
    }

    @Test
    void officerWorkloadIsUnevenSoTheDashboardHasSomethingToShow() {
        Map<Long, Long> open = new java.util.TreeMap<>();
        Map<Long, Long> total = new java.util.TreeMap<>();
        for (OfficerStatusTotal t : claimRepository.totalsByOfficerAndStatus()) {
            total.merge(t.officerId(), t.claimCount(), Long::sum);
            if (t.status() == ClaimStatus.UNDER_REVIEW || t.status() == ClaimStatus.MORE_INFO_REQUIRED) {
                open.merge(t.officerId(), t.claimCount(), Long::sum);
            }
        }

        assertThat(open).containsExactly(Map.entry(2L, 20L), Map.entry(3L, 14L), Map.entry(4L, 17L), Map.entry(5L, 10L), Map.entry(6L, 7L));
        assertThat(total).containsExactly(Map.entry(2L, 53L), Map.entry(3L, 23L), Map.entry(4L, 30L), Map.entry(5L, 23L), Map.entry(6L, 21L));
        assertThat(total.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(150);
    }

    // ---- history and notifications ----------------------------------------------------------------------------

    @Test
    void historyHasTheExpectedEventMix() {
        assertThat(grouped("SELECT event_type AS k, COUNT(*) AS n FROM claim_history GROUP BY event_type")).containsOnly(
                Map.entry("CLAIM_CREATED", 250L), Map.entry("CLAIM_ASSIGNED", 150L), Map.entry("CLAIM_APPROVED", 64L),
                Map.entry("CLAIM_REJECTED", 18L), Map.entry("ADDITIONAL_INFO_REQUESTED", 15L), Map.entry("ADDITIONAL_INFO_RECEIVED", 3L));
        assertThat(count("SELECT COUNT(*) FROM (SELECT claim_id FROM claim_history WHERE event_type = 'CLAIM_CREATED' GROUP BY claim_id HAVING COUNT(*) = 1)"))
                .isEqualTo(250);
    }

    @Test
    void everyClaimsTimelineEndsWhereItsStatusSays() {
        // The last event's target status is the claim's status; a SETTLED claim's history stops at its approval
        // (settlement is not an event the platform records yet).
        assertThat(count("SELECT COUNT(*) FROM claims c JOIN claim_history h ON h.id = (SELECT MAX(id) FROM claim_history WHERE claim_id = c.id) "
                + "WHERE h.to_status <> CASE WHEN c.status = 'SETTLED' THEN 'APPROVED' ELSE c.status END")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims WHERE id NOT IN (SELECT claim_id FROM claim_history)")).isZero();
    }

    @Test
    void historyIsChronologicalAndNeverOutlivesTheClaimsUpdatedAt() {
        assertThat(count("SELECT COUNT(*) FROM claim_history h JOIN claims c ON c.id = h.claim_id WHERE h.created_at < c.created_at")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claims c WHERE c.updated_at < (SELECT MAX(h.created_at) FROM claim_history h WHERE h.claim_id = c.id)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_history a JOIN claim_history b ON a.claim_id = b.claim_id AND a.id < b.id AND a.created_at >= b.created_at")).isZero();
    }

    @Test
    void historyActorsAreTheClaimantOrTheAssignedOfficer() {
        assertThat(count("SELECT COUNT(*) FROM claim_history h JOIN claims c ON c.id = h.claim_id WHERE h.performed_by_role = 'CLAIMANT' AND h.performed_by <> c.claimant_id"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_history h JOIN claims c ON c.id = h.claim_id WHERE h.performed_by_role = 'OFFICER' AND h.performed_by <> c.assigned_officer_id"))
                .isZero();
    }

    @Test
    void notificationsGoToTheRightPeopleAndMostRecentOnesAreUnread() {
        assertThat(count("SELECT COUNT(*) FROM notifications n JOIN claims c ON c.id = n.claim_id "
                + "WHERE n.recipient_id <> c.claimant_id AND n.recipient_id <> c.assigned_officer_id")).isZero();
        assertThat(grouped("SELECT CASE WHEN is_read THEN 'read' ELSE 'unread' END AS k, COUNT(*) AS n FROM notifications GROUP BY is_read"))
                .containsOnly(Map.entry("read", 170L), Map.entry("unread", 30L));
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE is_read = FALSE AND created_at < TIMESTAMP WITH TIME ZONE '2026-10-08 00:00:00+00:00'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM notifications n JOIN claim_history h ON h.claim_id = n.claim_id AND h.event_type = n.event_type AND h.created_at = n.created_at"))
                .isGreaterThanOrEqualTo(200);   // every notification corresponds to a recorded event
    }

    // ---- the application can read all of it -------------------------------------------------------------------

    @Test
    void hibernateReadsEverySeededRowIncludingEveryEnumValue() {
        List<Claim> claims = claimRepository.findAll();
        List<ClaimHistory> history = historyRepository.findAll();
        List<Notification> notifications = notificationRepository.findAll();

        assertThat(claims).hasSize(250);
        assertThat(claims).extracting(Claim::getClaimType).containsOnly(ClaimType.AUTO, ClaimType.HOME);
        assertThat(claims).extracting(Claim::getStatus).containsOnly(ClaimStatus.values());
        assertThat(history).hasSize(500);
        assertThat(history).extracting(ClaimHistory::getEventType).containsOnly(ClaimEventType.values());
        assertThat(notifications).hasSize(200);
    }

    @Test
    @Transactional
    void countersWereMovedPastTheSeededRowsSoNewClaimsDoNotCollide() {
        String next = claimNumberGenerator.next();
        Claim fresh = claimRepository.saveAndFlush(new Claim(next, "MTR-AU-2025-123456", 7L, "Liam O'Connor", "liam.oconnor@example.com",
                ClaimType.AUTO, LocalDate.of(2026, 10, 20), null, "New claim after the seed", new BigDecimal("1000.00"), ANCHOR));

        assertThat(next).endsWith("-000251");
        assertThat(fresh.getId()).isEqualTo(251L);
    }

    @Test
    void theApiServesTheSeededClaimsToOfficersAndClaimants() throws Exception {
        String officer = "Bearer " + TestJwt.token(jwtSecret, 2L, Role.OFFICER);
        // user 12 is Wei Ling Tan, a Singapore claimant
        String claimant = "Bearer " + TestJwt.token(jwtSecret, 12L, Role.CLAIMANT);
        long weiLingsClaims = count("SELECT COUNT(*) FROM claims WHERE claimant_id = 12");

        mockMvc.perform(get("/api/claims/search").param("size", "1").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(250));
        mockMvc.perform(get("/api/claims/search").param("status", "SUBMITTED").param("size", "1").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(jsonPath("$.totalElements").value(100));
        mockMvc.perform(get("/api/claims/my-claims").param("size", "1").header(HttpHeaders.AUTHORIZATION, claimant))
                .andExpect(jsonPath("$.totalElements").value(weiLingsClaims));
        mockMvc.perform(get("/api/claims/CLM-2025-000001").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.history[0].eventType").value("CLAIM_CREATED"));
        mockMvc.perform(get("/api/internal/claims/statistics").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.length()").value(6));
    }

    @Test
    void theApiServesTheSeededInboxesAndTheirUnreadBadges() throws Exception {
        // Whoever has the most unread notifications (lowest id on a tie): claimant or officer, both are in the seed.
        Map<String, Object> busiest = jdbc.queryForMap("SELECT recipient_id, COUNT(*) AS unread FROM notifications "
                + "WHERE is_read = FALSE GROUP BY recipient_id ORDER BY unread DESC, recipient_id LIMIT 1");
        long recipient = ((Number) busiest.get("RECIPIENT_ID")).longValue();
        long unread = ((Number) busiest.get("UNREAD")).longValue();
        long total = count("SELECT COUNT(*) FROM notifications WHERE recipient_id = " + recipient);
        // Users 2..6 are the seeded officers; everyone else who is notified is a claimant.
        String bearer = "Bearer " + TestJwt.token(jwtSecret, recipient, recipient <= 6 ? Role.OFFICER : Role.CLAIMANT);

        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(unread));
        mockMvc.perform(get("/api/notifications").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.content[*].claimNumber").value(everyItem(matchesPattern("CLM-20\\d{2}-\\d{6}"))));
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.totalElements").value(unread))
                .andExpect(jsonPath("$.content[*].read").value(everyItem(is(false))));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Map<String, Long> grouped(String sql) {
        return jdbc.queryForList(sql).stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("K")), row -> ((Number) row.get("N")).longValue()));
    }
}
