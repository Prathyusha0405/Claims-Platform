package com.chubb.claims.assessment.seed;

import com.chubb.claims.assessment.entity.AssessmentDecision;
import com.chubb.claims.assessment.entity.ClaimAssessment;
import com.chubb.claims.assessment.entity.ClaimNote;
import com.chubb.claims.assessment.entity.NoteType;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.repository.ClaimNoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full migration set (V1 schema + V2 demo data) on a private database. Checks the assessments and notes are
 * internally consistent and agree with the ids and numbering claim-service's and auth-service's seeds use.
 */
@SpringBootTest(properties = {
        "spring.flyway.target=latest",
        "spring.datasource.url=jdbc:h2:mem:assessmentseedcheck;DB_CLOSE_DELAY=-1"})
class SeedDataIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ClaimAssessmentRepository assessmentRepository;

    @Autowired
    private ClaimNoteRepository noteRepository;

    @Test
    void theDocumentedVolumesAreLoaded() {
        assertThat(count("SELECT COUNT(*) FROM claim_assessments")).isEqualTo(150);
        assertThat(count("SELECT COUNT(*) FROM claim_notes")).isEqualTo(200);
    }

    @Test
    void decisionsMatchTheClaimStatusesInClaimService() {
        // 28 APPROVED + 36 SETTLED (approved first) = 64 approvals; 18 REJECTED; 56 UNDER_REVIEW + 12 MORE_INFO_REQUIRED undecided.
        Map<String, Long> decisions = grouped("SELECT COALESCE(decision, 'PENDING') AS k, COUNT(*) AS n FROM claim_assessments GROUP BY decision");

        assertThat(decisions).containsOnly(Map.entry("APPROVED", 64L), Map.entry("REJECTED", 18L), Map.entry("PENDING", 68L));
    }

    @Test
    void noteTypesAreMixedAsDocumented() {
        assertThat(grouped("SELECT note_type AS k, COUNT(*) AS n FROM claim_notes GROUP BY note_type")).containsOnly(
                Map.entry("GENERAL", 120L), Map.entry("INFO_REQUEST", 15L), Map.entry("APPROVAL", 47L), Map.entry("REJECTION", 18L));
    }

    @Test
    void oneAssessmentPerClaimWithTheNumberingClaimServiceUses() {
        assertThat(count("SELECT COUNT(DISTINCT claim_id) FROM claim_assessments")).isEqualTo(150);
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE claim_id NOT BETWEEN 1 AND 250")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE claim_number NOT REGEXP '^CLM-20[0-9]{2}-[0-9]{6}$'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE RIGHT(claim_number, 6) <> LPAD(CAST(claim_id AS VARCHAR), 6, '0')")).isZero();
    }

    @Test
    void officersAreTheFiveStaffMembersAuthServiceSeeds() {
        Map<Long, String> names = new TreeMap<>();
        jdbc.queryForList("SELECT officer_id, MAX(officer_name) AS officer_name FROM claim_assessments GROUP BY officer_id")
                .forEach(row -> names.put(((Number) row.get("OFFICER_ID")).longValue(), (String) row.get("OFFICER_NAME")));

        assertThat(names).containsExactly(Map.entry(2L, "Olivia Bennett"), Map.entry(3L, "Kenji Sato"), Map.entry(4L, "Mei Lin Chua"),
                Map.entry(5L, "Rajesh Kumar"), Map.entry(6L, "Grace Wong"));
        // each officer is known by exactly one name
        assertThat(count("SELECT COUNT(*) FROM (SELECT officer_id FROM claim_assessments GROUP BY officer_id HAVING COUNT(DISTINCT officer_name) > 1)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_notes WHERE officer_id NOT BETWEEN 2 AND 6")).isZero();
    }

    @Test
    void timestampsAreCoherentAndNothingIsDatedAfterTheAnchor() {
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE decided_at < assigned_at")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE updated_at < created_at")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE (decision IS NULL) <> (decided_at IS NULL)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE updated_at <> COALESCE(decided_at, assigned_at)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE COALESCE(decided_at, assigned_at) > TIMESTAMP WITH TIME ZONE '2026-10-22 06:00:00+00:00'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_notes WHERE created_at > TIMESTAMP WITH TIME ZONE '2026-10-22 06:00:00+00:00'")).isZero();
    }

    @Test
    void rejectionsAlwaysCarryAReasonAndApprovalCommentsAreMirroredAsNotes() {
        assertThat(count("SELECT COUNT(*) FROM claim_assessments WHERE decision = 'REJECTED' AND decision_reason IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_notes WHERE note_type = 'REJECTION'"))
                .isEqualTo(count("SELECT COUNT(*) FROM claim_assessments WHERE decision = 'REJECTED'"));
        assertThat(count("SELECT COUNT(*) FROM claim_notes WHERE note_type = 'APPROVAL'"))
                .isEqualTo(count("SELECT COUNT(*) FROM claim_assessments WHERE decision = 'APPROVED' AND decision_reason IS NOT NULL"));
        // the note says exactly what the decision says
        assertThat(count("SELECT COUNT(*) FROM claim_notes n JOIN claim_assessments a ON a.claim_id = n.claim_id "
                + "WHERE n.note_type IN ('APPROVAL', 'REJECTION') AND (n.content <> a.decision_reason OR n.created_at <> a.decided_at)")).isZero();
    }

    @Test
    void decisionAndInfoRequestNotesBelongToTheOfficerWhoHoldsTheClaim() {
        assertThat(count("SELECT COUNT(*) FROM claim_notes n JOIN claim_assessments a ON a.claim_id = n.claim_id "
                + "WHERE n.note_type <> 'GENERAL' AND n.officer_id <> a.officer_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_notes WHERE note_type <> 'GENERAL' AND claim_id NOT IN (SELECT claim_id FROM claim_assessments)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_notes n JOIN claim_assessments a ON a.claim_id = n.claim_id "
                + "WHERE n.note_type = 'GENERAL' AND n.officer_id <> a.officer_id")).isZero();
    }

    @Test
    void someGeneralNotesAreTriageNotesOnClaimsStillWaitingInTheQueue() {
        long onUnassigned = count("SELECT COUNT(*) FROM claim_notes WHERE claim_id NOT IN (SELECT claim_id FROM claim_assessments)");

        assertThat(onUnassigned).isBetween(5L, 40L);
        assertThat(count("SELECT COUNT(*) FROM claim_notes WHERE claim_id NOT IN (SELECT claim_id FROM claim_assessments) AND note_type <> 'GENERAL'")).isZero();
    }

    @Test
    void everyOfficerHasWorkSoTheWorkloadDashboardShowsAllFive() {
        List<Long> perOfficer = jdbc.queryForList("SELECT COUNT(*) FROM claim_assessments GROUP BY officer_id ORDER BY officer_id", Long.class);

        assertThat(perOfficer).containsExactly(53L, 23L, 30L, 23L, 21L);
    }

    @Test
    void hibernateReadsEverySeededRow() {
        List<ClaimAssessment> assessments = assessmentRepository.findAll();
        List<ClaimNote> notes = noteRepository.findAll();

        assertThat(assessments).hasSize(150);
        assertThat(assessments).extracting(ClaimAssessment::getDecision).containsOnly(AssessmentDecision.APPROVED, AssessmentDecision.REJECTED, null);
        assertThat(assessments).allSatisfy(a -> {
            assertThat(a.getCreatedAt()).isNotNull();
            assertThat(a.getUpdatedAt()).isNotNull();
        });
        assertThat(notes).hasSize(200);
        assertThat(notes).extracting(ClaimNote::getNoteType).containsOnly(NoteType.values());
        assertThat(assessmentRepository.findOfficerNames()).extracting(ClaimAssessmentRepository.OfficerName::getOfficerName)
                .containsExactlyInAnyOrder("Olivia Bennett", "Kenji Sato", "Mei Lin Chua", "Rajesh Kumar", "Grace Wong");
    }

    @Test
    @Transactional
    void identityCountersWereMovedPastTheSeededRows() {
        ClaimAssessment assessment = assessmentRepository.saveAndFlush(
                new ClaimAssessment(251L, "CLM-2026-000251", 2L, "Olivia Bennett", Instant.parse("2026-10-22T06:00:00Z")));
        ClaimNote note = noteRepository.saveAndFlush(
                new ClaimNote(251L, 2L, "Olivia Bennett", NoteType.GENERAL, "after the seed", Instant.parse("2026-10-22T06:00:00Z")));

        assertThat(assessment.getId()).isEqualTo(151L);
        assertThat(note.getId()).isEqualTo(201L);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Map<String, Long> grouped(String sql) {
        return jdbc.queryForList(sql).stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("K")), row -> ((Number) row.get("N")).longValue()));
    }
}
