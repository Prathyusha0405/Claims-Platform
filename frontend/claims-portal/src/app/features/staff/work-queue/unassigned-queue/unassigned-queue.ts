import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';

import { Notify } from '@core/services/notify';
import { StaffApi } from '@core/services/staff-api';
import { ClaimsTable } from '@shared/components/claims-table/claims-table';
import { LoadError } from '@shared/components/load-error/load-error';
import { ClaimSummary, Market, MARKET_LABELS, MARKETS } from '@shared/models/claim';
import { DEFAULT_PAGE_SIZE, PageRequest } from '@shared/models/page';
import { asApiError } from '@shared/utils/errors';
import { loader } from '@shared/utils/loader';

/**
 * SUBMITTED claims nobody holds yet, oldest first so the longest-waiting claimant is served first. Claims staff can narrow the
 * queue to one market.
 */
@Component({
  imports: [ClaimsTable, LoadError, MatFormFieldModule, MatSelectModule],
  selector: 'app-unassigned-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './unassigned-queue.scss',
  templateUrl: './unassigned-queue.html',
})
export class UnassignedQueue {
  private readonly api = inject(StaffApi);
  private readonly notify = inject(Notify);
  private readonly router = inject(Router);

  protected readonly request = signal<PageRequest>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sortField: 'createdAt',
    sortDirection: 'asc',
  });
  private readonly reloads = signal(0);
  protected readonly markets = MARKETS;
  protected readonly marketLabels = MARKET_LABELS;
  /** The market the queue is narrowed to; empty means every market. */
  protected readonly market = signal<Market | ''>('');
  protected readonly emptyMessage = computed(() => {
    const market = this.market();
    return market === ''
      ? 'The queue is empty. Every submitted claim has a staff member.'
      : `No submitted claim is waiting in ${MARKET_LABELS[market]}.`;
  });
  protected readonly queue = loader(
    computed(() => ({ request: this.request(), market: this.market(), reload: this.reloads() })),
    ({ request, market }) => this.api.unassigned(request, market || undefined),
  );
  /** The claim being taken right now; every "Take claim" button is disabled meanwhile. */
  protected readonly busyId = signal<number | null>(null);

  protected filterByMarket(market: Market | ''): void {
    this.market.set(market);
    this.request.update((current) => ({ ...current, page: 0 }));
  }

  protected reload(): void {
    this.reloads.update((count) => count + 1);
  }

  protected take(claim: ClaimSummary): void {
    if (this.busyId() !== null) {
      return;
    }
    this.busyId.set(claim.id);
    this.api.assign(claim.id).subscribe({
      next: () => {
        this.notify.success(`You now hold ${claim.claimNumber}.`);
        void this.router.navigate(['/staff/claims', claim.claimNumber]);
      },
      error: (failure: unknown) => {
        const error = asApiError(failure);
        // A 403 has already been announced by the error interceptor.
        if (!error.isForbidden) {
          this.notify.error(error.isConflict ? `${claim.claimNumber} has already been taken by another staff member.` : error.message);
        }
        this.busyId.set(null);
        this.reload();
      },
    });
  }
}
