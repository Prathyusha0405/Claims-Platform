package com.chubb.claims.assessment.repository;

import com.chubb.claims.assessment.entity.AssessmentDecision;
import com.chubb.claims.assessment.entity.ClaimAssessment;
import com.chubb.claims.assessment.entity.ClaimNote;
import com.chubb.claims.assessment.entity.NoteType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The mapping and constraints of the two tables, against real H2 built by the Flyway migrations. */
@DataJpaTest
class AssessmentRepositoriesTest {

    private static final Instant T0 = Instant.parse("2026-10-22T06:00:00Z");

    @Autowired
    private ClaimAssessmentRepository assessmentRepository;

    @Autowired
    private ClaimNoteRepository noteRepository;

    @Test
    void anAssessmentRoundTripsIncludingItsDecision() {
        ClaimAssessment assessment = new ClaimAssessment(10L, "CLM-2026-000010", 7L, "Alice Staff", T0);
        assessment.recordDecision(AssessmentDecision.REJECTED, "Policy lapsed", T0.plusSeconds(60));
        assessmentRepository.saveAndFlush(assessment);

        ClaimAssessment found = assessmentRepository.findByClaimId(10L).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getClaimNumber()).isEqualTo("CLM-2026-000010");
        assertThat(found.getStaffId()).isEqualTo(7L);
        assertThat(found.getStaffName()).isEqualTo("Alice Staff");
        assertThat(found.getDecision()).isEqualTo(AssessmentDecision.REJECTED);
        assertThat(found.getDecisionReason()).isEqualTo("Policy lapsed");
        assertThat(found.getAssignedAt()).isEqualTo(T0);
        assertThat(found.getDecidedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(found.getVersion()).isZero();
        // audit columns: created when the staff member took the claim, last touched when they decided
        assertThat(found.getCreatedAt()).isEqualTo(T0);
        assertThat(found.getUpdatedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void findByClaimIdIsEmptyForAClaimNobodyTook() {
        assertThat(assessmentRepository.findByClaimId(404L)).isEmpty();
    }

    @Test
    void aClaimCanOnlyHaveOneAssessment() {
        assessmentRepository.saveAndFlush(new ClaimAssessment(10L, "CLM-2026-000010", 7L, "Alice", T0));

        assertThatThrownBy(() -> assessmentRepository.saveAndFlush(new ClaimAssessment(10L, "CLM-2026-000010", 8L, "Bob", T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatingAnAssessmentBumpsItsVersionForOptimisticLocking() {
        ClaimAssessment assessment = assessmentRepository.saveAndFlush(new ClaimAssessment(10L, "CLM-2026-000010", 7L, "Alice", T0));

        assessment.recordDecision(AssessmentDecision.APPROVED, null, T0.plusSeconds(5));
        assessmentRepository.saveAndFlush(assessment);

        assertThat(assessment.getVersion()).isEqualTo(1);
        assertThat(assessment.getCreatedAt()).isEqualTo(T0);
        assertThat(assessment.getUpdatedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void staffNamesAreGroupedPerStaffMember() {
        assessmentRepository.save(new ClaimAssessment(10L, "CLM-2026-000010", 7L, "Alice Staff", T0));
        assessmentRepository.save(new ClaimAssessment(11L, "CLM-2026-000011", 7L, "Alice Staff", T0));
        assessmentRepository.save(new ClaimAssessment(12L, "CLM-2026-000012", 8L, null, T0));
        assessmentRepository.flush();

        List<ClaimAssessmentRepository.StaffName> names = assessmentRepository.findStaffNames();

        assertThat(names).hasSize(2);
        assertThat(names).filteredOn(name -> name.getStaffId().equals(7L)).singleElement()
                .satisfies(name -> assertThat(name.getStaffName()).isEqualTo("Alice Staff"));
        assertThat(names).filteredOn(name -> name.getStaffId().equals(8L)).singleElement()
                .satisfies(name -> assertThat(name.getStaffName()).isNull());
    }

    @Test
    void notesComeBackOldestFirstAndOnlyForTheirClaim() {
        noteRepository.save(new ClaimNote(10L, 7L, "Alice", NoteType.GENERAL, "second", T0.plusSeconds(10)));
        noteRepository.save(new ClaimNote(10L, 7L, "Alice", NoteType.INFO_REQUEST, "first", T0));
        noteRepository.save(new ClaimNote(10L, 8L, "Bob", NoteType.APPROVAL, "third", T0.plusSeconds(20)));
        noteRepository.save(new ClaimNote(99L, 7L, "Alice", NoteType.GENERAL, "other claim", T0));
        noteRepository.flush();

        List<ClaimNote> notes = noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(10L);

        assertThat(notes).extracting(ClaimNote::getContent).containsExactly("first", "second", "third");
        assertThat(notes).extracting(ClaimNote::getNoteType)
                .containsExactly(NoteType.INFO_REQUEST, NoteType.GENERAL, NoteType.APPROVAL);
        assertThat(notes.get(0).getStaffName()).isEqualTo("Alice");
        assertThat(notes.get(0).getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void aNoteDoesNotNeedAnAssessment() {
        noteRepository.saveAndFlush(new ClaimNote(555L, 7L, "Alice", NoteType.GENERAL, "triage note", T0));

        assertThat(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(555L)).hasSize(1);
        assertThat(assessmentRepository.findByClaimId(555L)).isEmpty();
    }
}
