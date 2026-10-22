import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { ClaimApi } from '@core/services/claim-api';
import { Notify } from '@core/services/notify';
import { ClaimOverview } from '@shared/components/claim-overview/claim-overview';
import { ClaimProgress } from '@shared/components/claim-progress/claim-progress';
import { ClaimTimeline } from '@shared/components/claim-timeline/claim-timeline';
import { LoadError } from '@shared/components/load-error/load-error';
import { StatusChip } from '@shared/components/status-chip/status-chip';
import { latestInfoRequest } from '@shared/utils/claim-rules';
import { asApiError, fieldError } from '@shared/utils/errors';
import { loader } from '@shared/utils/loader';

/** Tracks one claim: where it is in its lifecycle, its details and history, and, when asked, a way to answer the officer. */
@Component({
  imports: [
    ClaimOverview,
    ClaimProgress,
    ClaimTimeline,
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
  selector: 'app-claim-details',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-details.scss',
  templateUrl: './claim-details.html',
})
export class ClaimDetails {
  private readonly api = inject(ClaimApi);
  private readonly notify = inject(Notify);

  /** From the URL: `/claimant/claims/:claimNumber`. */
  readonly claimNumber = input.required<string>();

  private readonly reloads = signal(0);
  protected readonly claim = loader(
    computed(() => ({ claimNumber: this.claimNumber(), reload: this.reloads() })),
    ({ claimNumber }) => this.api.get(claimNumber),
  );

  /** What the officer asked for, shown above the reply box. */
  protected readonly question = computed(() => {
    const claim = this.claim.value();
    return claim === null ? null : latestInfoRequest(claim.history);
  });
  protected readonly reply = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });
  protected readonly replying = signal(false);
  protected readonly replyFailure = signal<string | null>(null);
  protected readonly replyError = () => fieldError(this.reply);

  protected reload(): void {
    this.reloads.update((count) => count + 1);
  }

  protected sendReply(claimId: number): void {
    if (this.replying()) {
      return;
    }
    const message = this.reply.value.trim();
    if (message === '') {
      this.reply.setErrors({ required: true });
      this.reply.markAsTouched();
      return;
    }
    if (this.reply.invalid) {
      this.reply.markAsTouched();
      return;
    }

    this.replying.set(true);
    this.replyFailure.set(null);
    this.api.submitAdditionalInfo(claimId, message).subscribe({
      next: () => {
        this.notify.success('Thank you. Your reply has been sent and the claim is back under review.');
        this.reply.reset();
        this.replying.set(false);
        this.reload();
      },
      error: (failure: unknown) => {
        const error = asApiError(failure);
        this.replyFailure.set(error.message);
        this.replying.set(false);
        // A 409 means the claim already moved on (for instance answered from another tab): show how it stands now.
        if (error.isConflict) {
          this.reload();
        }
      },
    });
  }
}
