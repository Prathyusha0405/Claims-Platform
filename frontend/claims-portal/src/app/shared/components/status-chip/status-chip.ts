import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { CLAIM_STATUS_ICONS, CLAIM_STATUS_LABELS, ClaimStatus } from '@shared/models/claim';

/** A claim's status as a coloured pill. The words and the icon carry the meaning; the colour only reinforces it. */
@Component({
  imports: [MatIconModule],
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './status-chip.scss',
  templateUrl: './status-chip.html',
})
export class StatusChip {
  readonly status = input.required<ClaimStatus>();

  protected readonly label = computed(() => CLAIM_STATUS_LABELS[this.status()]);
  protected readonly icon = computed(() => CLAIM_STATUS_ICONS[this.status()]);
}
