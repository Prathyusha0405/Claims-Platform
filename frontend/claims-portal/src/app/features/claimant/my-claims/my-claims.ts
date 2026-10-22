import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { ClaimApi } from '@core/services/claim-api';
import { ClaimFilters } from '@shared/components/claim-filters/claim-filters';
import { ClaimsTable } from '@shared/components/claims-table/claims-table';
import { LoadError } from '@shared/components/load-error/load-error';
import { ClaimSearchCriteria } from '@shared/models/claim';
import { DEFAULT_PAGE_SIZE, PageRequest } from '@shared/models/page';
import { loader } from '@shared/utils/loader';

/** The claimant's own claims, newest first. With no filters it lists them; with any, it searches (still only theirs). */
@Component({
  imports: [ClaimFilters, ClaimsTable, LoadError, MatButtonModule, MatIconModule, RouterLink],
  selector: 'app-my-claims',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './my-claims.scss',
  templateUrl: './my-claims.html',
})
export class MyClaims {
  private readonly api = inject(ClaimApi);

  protected readonly request = signal<PageRequest>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sortField: 'createdAt',
    sortDirection: 'desc',
  });
  private readonly criteria = signal<ClaimSearchCriteria>({});
  private readonly reloads = signal(0);

  protected readonly filtered = computed(() => Object.keys(this.criteria()).length > 0);
  protected readonly emptyMessage = computed(() =>
    this.filtered() ? 'No claims match these filters.' : 'You have not submitted any claims yet.',
  );
  protected readonly claims = loader(
    computed(() => ({ request: this.request(), criteria: this.criteria(), reload: this.reloads() })),
    ({ request, criteria }) =>
      Object.keys(criteria).length > 0 ? this.api.search(criteria, request) : this.api.myClaims(request),
  );

  protected search(criteria: ClaimSearchCriteria): void {
    this.criteria.set(criteria);
    this.request.update((current) => ({ ...current, page: 0 }));
  }

  protected reload(): void {
    this.reloads.update((count) => count + 1);
  }
}
