import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { CLAIM_TYPE_LABELS, ClaimDetail, MARKET_LABELS } from '@shared/models/claim';
import { MoneyPipe } from '@shared/pipes/money-pipe';

/** The facts of one claim as a labelled list. Claims staff also see who the claimant is and who holds the claim. */
@Component({
  imports: [DatePipe, MoneyPipe],
  selector: 'app-claim-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-overview.scss',
  templateUrl: './claim-overview.html',
})
export class ClaimOverview {
  readonly claim = input.required<ClaimDetail>();
  /** Show the claimant and the assigned staff member: for claims staff, not for the claimant looking at their own claim. */
  readonly staff = input(false);

  protected readonly typeLabels = CLAIM_TYPE_LABELS;
  protected readonly marketLabels = MARKET_LABELS;
}
