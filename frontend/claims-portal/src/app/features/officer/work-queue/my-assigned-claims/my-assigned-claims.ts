import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { AuthStore } from '@core/services/auth-store';
import { ClaimApi } from '@core/services/claim-api';
import { ClaimFilters } from '@shared/components/claim-filters/claim-filters';
import { ClaimsTable } from '@shared/components/claims-table/claims-table';
import { LoadError } from '@shared/components/load-error/load-error';
import { ClaimSearchCriteria } from '@shared/models/claim';
import { DEFAULT_PAGE_SIZE, PageRequest } from '@shared/models/page';
import { loader } from '@shared/utils/loader';

/** The claims this officer holds, most recently touched first, searchable by the usual filters. */
@Component({
  imports: [ClaimFilters, ClaimsTable, LoadError],
  selector: 'app-my-assigned-claims',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './my-assigned-claims.scss',
  templateUrl: './my-assigned-claims.html',
})
export class MyAssignedClaims {
  private readonly api = inject(ClaimApi);
  private readonly auth = inject(AuthStore);

  protected readonly request = signal<PageRequest>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sortField: 'updatedAt',
    sortDirection: 'desc',
  });
  private readonly criteria = signal<ClaimSearchCriteria>({});
  private readonly reloads = signal(0);

  protected readonly emptyMessage = computed(() =>
    Object.keys(this.criteria()).length > 0 ? 'None of your claims match these filters.' : 'You do not hold any claims yet.',
  );
  protected readonly claims = loader(
    computed(() => ({ request: this.request(), criteria: this.criteria(), reload: this.reloads() })),
    ({ request, criteria }) => this.api.search({ ...criteria, assignedOfficerId: this.auth.user()?.id }, request),
  );

  protected search(criteria: ClaimSearchCriteria): void {
    this.criteria.set(criteria);
    this.request.update((current) => ({ ...current, page: 0 }));
  }

  protected reload(): void {
    this.reloads.update((count) => count + 1);
  }
}
