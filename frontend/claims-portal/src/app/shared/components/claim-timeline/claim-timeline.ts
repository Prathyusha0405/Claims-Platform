import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { isRole, ROLE_LABELS } from '@core/models/role';
import { CLAIM_EVENT_ICONS, CLAIM_EVENT_LABELS, ClaimHistoryEntry } from '@shared/models/claim';
import { StatusChip } from '../status-chip/status-chip';

/** A claim's audit history, oldest first, as the "what has happened so far" list on a claim page. */
@Component({
  imports: [DatePipe, MatIconModule, StatusChip],
  selector: 'app-claim-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-timeline.scss',
  templateUrl: './claim-timeline.html',
})
export class ClaimTimeline {
  readonly history = input.required<readonly ClaimHistoryEntry[]>();

  protected readonly labels = CLAIM_EVENT_LABELS;
  protected readonly icons = CLAIM_EVENT_ICONS;

  /** Who did it, in words; null for events the system recorded itself. */
  protected actor(entry: ClaimHistoryEntry): string | null {
    const role = entry.performedByRole;
    return role !== null && isRole(role) ? ROLE_LABELS[role] : null;
  }
}
