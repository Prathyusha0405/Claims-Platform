import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { CLAIM_TYPE_LABELS, ClaimSummary, ClaimType, Market, MARKET_LABELS } from '@shared/models/claim';
import { Page, PAGE_SIZE_OPTIONS, PageRequest } from '@shared/models/page';
import { MoneyPipe } from '@shared/pipes/money-pipe';
import { StatusChip } from '../status-chip/status-chip';

const BASE_COLUMNS = ['claimNumber', 'policyNumber', 'market', 'claimType', 'status', 'claimAmount', 'incidentDate', 'createdAt'];

/**
 * A page of claims with server-side sorting and paging. It owns no data: the page that uses it holds the
 * `PageRequest`, fetches, and hands the result back in, so my claims, search results and the work queue all behave
 * the same. Give it an `actionLabel` to add a button column (the work queue's "Take claim").
 */
@Component({
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSortModule,
    MatTableModule,
    MoneyPipe,
    RouterLink,
    StatusChip,
  ],
  selector: 'app-claims-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claims-table.scss',
  templateUrl: './claims-table.html',
})
export class ClaimsTable {
  /** The latest result; null until the first one arrives. It stays in place while the next one loads. */
  readonly page = input<Page<ClaimSummary> | null>(null);
  /** What `page` was asked for: drives the sort arrows and the paginator. */
  readonly request = input.required<PageRequest>();
  readonly loading = input(false);
  /** Where a claim number links to; the claim number is appended, e.g. `/claimant/claims`. */
  readonly linkBase = input.required<string>();
  readonly label = input('Claims');
  readonly emptyMessage = input('No claims found.');
  /** When set, every row gets a button with this text and `action` fires with the row's claim. */
  readonly actionLabel = input<string | null>(null);
  /** The claim an action is running for: its button is disabled so a double click cannot repeat it. */
  readonly busyId = input<number | null>(null);

  readonly requestChange = output<PageRequest>();
  readonly action = output<ClaimSummary>();

  protected readonly columns = computed(() => (this.actionLabel() === null ? BASE_COLUMNS : [...BASE_COLUMNS, 'action']));
  protected readonly pageSizes = PAGE_SIZE_OPTIONS;

  /** The work queue is served by assessment-service, which sends the type as a plain string; show it even if unknown. */
  protected typeLabel(type: string): string {
    return CLAIM_TYPE_LABELS[type as ClaimType] ?? type;
  }

  /** Likewise for the market: the work queue's code is shown as a name when known. */
  protected marketLabel(market: string): string {
    return MARKET_LABELS[market as Market] ?? market;
  }

  protected sortBy(sort: Sort): void {
    this.requestChange.emit({
      ...this.request(),
      page: 0,
      sortField: sort.active,
      sortDirection: sort.direction === 'asc' ? 'asc' : 'desc',
    });
  }

  protected turnPage(event: PageEvent): void {
    this.requestChange.emit({ ...this.request(), page: event.pageIndex, size: event.pageSize });
  }
}
