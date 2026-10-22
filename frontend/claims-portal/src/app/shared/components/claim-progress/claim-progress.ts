import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { ClaimStatus } from '@shared/models/claim';

type StepState = 'done' | 'current' | 'upcoming';

interface Step {
  label: string;
  state: StepState;
  hint: string | null;
}

/** How far along the four visible stages a status is. MORE_INFO_REQUIRED is still "under review". */
const STAGE_REACHED: Readonly<Record<ClaimStatus, number>> = {
  SUBMITTED: 0,
  UNDER_REVIEW: 1,
  MORE_INFO_REQUIRED: 1,
  APPROVED: 2,
  REJECTED: 2,
  SETTLED: 3,
};

/** The claim lifecycle as four stages, so a claimant sees at a glance where their claim is. */
@Component({
  imports: [MatIconModule],
  selector: 'app-claim-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-progress.scss',
  templateUrl: './claim-progress.html',
})
export class ClaimProgress {
  readonly status = input.required<ClaimStatus>();

  protected readonly steps = computed<Step[]>(() => {
    const status = this.status();
    const reached = STAGE_REACHED[status];
    const labels = ['Submitted', 'Under review', this.decisionLabel(status), 'Settled'];

    return labels.map((label, index) => ({
      label,
      state: this.stateOf(index, reached, status === 'SETTLED'),
      hint: index === 1 && status === 'MORE_INFO_REQUIRED' ? 'More information needed' : null,
    }));
  });

  private decisionLabel(status: ClaimStatus): string {
    switch (status) {
      case 'APPROVED':
        return 'Approved';
      case 'REJECTED':
        return 'Rejected';
      case 'SETTLED':
        return 'Decision made';
      default:
        return 'Decision';
    }
  }

  private stateOf(index: number, reached: number, finished: boolean): StepState {
    if (index < reached || (index === reached && finished)) {
      return 'done';
    }
    return index === reached ? 'current' : 'upcoming';
  }
}
