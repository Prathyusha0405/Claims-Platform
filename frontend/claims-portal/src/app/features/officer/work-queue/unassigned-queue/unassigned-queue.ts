import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Notify } from '@core/services/notify';
import { OfficerApi } from '@core/services/officer-api';
import { ClaimsTable } from '@shared/components/claims-table/claims-table';
import { LoadError } from '@shared/components/load-error/load-error';
import { ClaimSummary } from '@shared/models/claim';
import { DEFAULT_PAGE_SIZE, PageRequest } from '@shared/models/page';
import { asApiError } from '@shared/utils/errors';
import { loader } from '@shared/utils/loader';

/** SUBMITTED claims nobody holds yet, oldest first so the longest-waiting claimant is served first. */
@Component({
  imports: [ClaimsTable, LoadError],
  selector: 'app-unassigned-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unassigned-queue.html',
})
export class UnassignedQueue {
  private readonly api = inject(OfficerApi);
  private readonly notify = inject(Notify);
  private readonly router = inject(Router);

  protected readonly request = signal<PageRequest>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sortField: 'createdAt',
    sortDirection: 'asc',
  });
  private readonly reloads = signal(0);
  protected readonly queue = loader(
    computed(() => ({ request: this.request(), reload: this.reloads() })),
    ({ request }) => this.api.unassigned(request),
  );
  /** The claim being taken right now; every "Take claim" button is disabled meanwhile. */
  protected readonly busyId = signal<number | null>(null);

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
        void this.router.navigate(['/officer/claims', claim.claimNumber]);
      },
      error: (failure: unknown) => {
        const error = asApiError(failure);
        // A 403 has already been announced by the error interceptor.
        if (!error.isForbidden) {
          this.notify.error(error.isConflict ? `${claim.claimNumber} has already been taken by another officer.` : error.message);
        }
        this.busyId.set(null);
        this.reload();
      },
    });
  }
}
