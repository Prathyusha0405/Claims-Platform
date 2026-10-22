import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { environment } from '@env/environment';
import { CLAIM_TYPE_LABELS, ClaimDetail } from '@shared/models/claim';

/** The facts of one claim as a labelled list. Officers also see who the claimant is and who holds the claim. */
@Component({
  imports: [CurrencyPipe, DatePipe],
  selector: 'app-claim-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-overview.scss',
  templateUrl: './claim-overview.html',
})
export class ClaimOverview {
  readonly claim = input.required<ClaimDetail>();
  /** Show the claimant and the assigned officer: for claims staff, not for the claimant looking at their own claim. */
  readonly staff = input(false);

  protected readonly typeLabels = CLAIM_TYPE_LABELS;
  protected readonly currency = environment.reportingCurrency;
}
