import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { EMPTY, filter, Observable } from 'rxjs';

import { AuthStore } from '@core/services/auth-store';
import { ClaimApi } from '@core/services/claim-api';
import { Notify } from '@core/services/notify';
import { StaffApi } from '@core/services/staff-api';
import { ClaimOverview } from '@shared/components/claim-overview/claim-overview';
import { ClaimTimeline } from '@shared/components/claim-timeline/claim-timeline';
import { LoadError } from '@shared/components/load-error/load-error';
import { StatusChip } from '@shared/components/status-chip/status-chip';
import { promptForText, TextPromptData } from '@shared/components/text-prompt-dialog/text-prompt-dialog';
import { NOTE_TYPE_LABELS } from '@shared/models/assessment';
import { CLAIM_STATUS_LABELS } from '@shared/models/claim';
import { latestInfoRequest, reviewAccess } from '@shared/utils/claim-rules';
import { asApiError, fieldError } from '@shared/utils/errors';
import { loader } from '@shared/utils/loader';

const COMMENT_LIMIT = 2000;

/**
 * A claims staff member's view of one claim: the claim and its history, what this staff member may do with it (take it, approve,
 * reject, ask for information, settle it once decided), and the internal notes. Which buttons appear follows the lifecycle rules in
 * `reviewAccess`; the backend enforces the same rules, so a stale page can at worst get a polite 409.
 */
@Component({
  imports: [
    ClaimOverview,
    ClaimTimeline,
    DatePipe,
    LoadError,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    ReactiveFormsModule,
    RouterLink,
    StatusChip,
  ],
  selector: 'app-claim-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-review.scss',
  templateUrl: './claim-review.html',
})
export class ClaimReview {
  private readonly claimApi = inject(ClaimApi);
  private readonly staffApi = inject(StaffApi);
  private readonly auth = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(Notify);

  /** From the URL: `/staff/claims/:claimNumber`. */
  readonly claimNumber = input.required<string>();

  private readonly claimReloads = signal(0);
  private readonly noteReloads = signal(0);

  protected readonly claim = loader(
    computed(() => ({ claimNumber: this.claimNumber(), reload: this.claimReloads() })),
    ({ claimNumber }) => this.claimApi.get(claimNumber),
  );
  /** Notes are keyed by the claim's numeric id, which is only known once the claim has loaded. */
  protected readonly notes = loader(
    computed(() => ({ id: this.claim.value()?.id ?? null, reload: this.noteReloads() })),
    ({ id }) => (id === null ? EMPTY : this.staffApi.notes(id)),
  );

  protected readonly access = computed(() => {
    const claim = this.claim.value();
    const staffMember = this.auth.user();
    return claim === null || staffMember === null ? null : reviewAccess(claim, staffMember.id);
  });
  protected readonly question = computed(() => {
    const claim = this.claim.value();
    return claim === null ? null : latestInfoRequest(claim.history);
  });
  /** True while a decision is being sent, so no button can fire a second one. */
  protected readonly acting = signal(false);

  protected readonly note = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(COMMENT_LIMIT)],
  });
  protected readonly addingNote = signal(false);
  protected readonly noteError = () => fieldError(this.note);

  protected readonly statusLabels = CLAIM_STATUS_LABELS;
  protected readonly noteTypeLabels = NOTE_TYPE_LABELS;

  protected reload(): void {
    this.claimReloads.update((count) => count + 1);
  }

  protected take(id: number, claimNumber: string): void {
    this.perform(this.staffApi.assign(id), `You now hold ${claimNumber}.`);
  }

  protected approve(id: number, claimNumber: string): void {
    this.decide(
      {
        title: `Approve ${claimNumber}`,
        description: 'The claimant is notified. Add a comment for the record if you wish.',
        label: 'Comment (optional)',
        confirmLabel: 'Approve claim',
        required: false,
        maxLength: COMMENT_LIMIT,
      },
      (comment) => this.staffApi.approve(id, comment),
      `${claimNumber} approved.`,
    );
  }

  protected reject(id: number, claimNumber: string): void {
    this.decide(
      {
        title: `Reject ${claimNumber}`,
        description: 'The claimant can read the reason in the claim history.',
        label: 'Reason for rejection',
        confirmLabel: 'Reject claim',
        required: true,
        maxLength: COMMENT_LIMIT,
        destructive: true,
      },
      (reason) => this.staffApi.reject(id, reason),
      `${claimNumber} rejected.`,
    );
  }

  protected settle(id: number, claimNumber: string): void {
    this.decide(
      {
        title: `Settle ${claimNumber}`,
        description:
          'Settling closes the claim: the claimant is notified and it stops counting towards liability exposure. Add a comment for the record if you wish.',
        label: 'Comment (optional)',
        confirmLabel: 'Settle claim',
        required: false,
        maxLength: COMMENT_LIMIT,
      },
      (comment) => this.staffApi.settle(id, comment),
      `${claimNumber} settled.`,
    );
  }

  protected requestInfo(id: number, claimNumber: string): void {
    this.decide(
      {
        title: `Request information for ${claimNumber}`,
        description: 'The claim moves to "More info required" and the claimant is asked to reply.',
        label: 'What does the claimant need to provide?',
        confirmLabel: 'Send request',
        required: true,
        maxLength: COMMENT_LIMIT,
      },
      (message) => this.staffApi.requestInfo(id, message),
      `Information requested from the claimant of ${claimNumber}.`,
    );
  }

  protected addNote(id: number): void {
    if (this.addingNote()) {
      return;
    }
    const content = this.note.value.trim();
    if (content === '' || this.note.invalid) {
      this.note.setErrors(content === '' ? { required: true } : this.note.errors);
      this.note.markAsTouched();
      return;
    }

    this.addingNote.set(true);
    this.staffApi.addNote(id, content).subscribe({
      next: () => {
        this.note.reset();
        this.addingNote.set(false);
        this.noteReloads.update((count) => count + 1);
        this.notify.success('Note added.');
      },
      error: (failure: unknown) => {
        this.notify.error(asApiError(failure).message);
        this.addingNote.set(false);
      },
    });
  }

  /** Asks for the text a decision needs, and only if the staff member confirms does it call the backend. */
  private decide(prompt: TextPromptData, call: (text: string) => Observable<unknown>, success: string): void {
    promptForText(this.dialog, prompt)
      .pipe(filter((text): text is string => text !== undefined))
      .subscribe((text) => this.perform(call(text), success));
  }

  private perform(action: Observable<unknown>, success: string): void {
    if (this.acting()) {
      return;
    }
    this.acting.set(true);
    action.subscribe({
      next: () => {
        this.notify.success(success);
        this.acting.set(false);
        // Reloading the claim also reloads the notes (they are keyed on the loaded claim), and a decision adds one.
        this.reload();
      },
      error: (failure: unknown) => {
        const error = asApiError(failure);
        // A 403 has already been announced by the error interceptor.
        if (!error.isForbidden) {
          this.notify.error(error.message);
        }
        this.acting.set(false);
        // The claim has moved on since this page loaded (someone else took it, the claimant replied): show it as it is now.
        if (error.isConflict || error.isForbidden) {
          this.reload();
        }
      },
    });
  }
}
