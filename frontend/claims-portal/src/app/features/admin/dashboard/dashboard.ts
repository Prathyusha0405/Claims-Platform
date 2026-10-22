import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { environment } from '@env/environment';
import { DashboardApi } from '@core/services/dashboard-api';
import { BarChart, BarDatum } from '@shared/components/bar-chart/bar-chart';
import { LoadError } from '@shared/components/load-error/load-error';
import { CLAIM_STATUS_LABELS, CLAIM_STATUSES } from '@shared/models/claim';
import { OfficerWorkload } from '@shared/models/dashboard';
import { loader } from '@shared/utils/loader';

const LOCALE = 'en-US';

/** The administrator's view: liability exposure, claim counts by status, and what each officer is carrying. */
@Component({
  imports: [
    BarChart,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    LoadError,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dashboard.scss',
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly api = inject(DashboardApi);

  /** Bumped by "Refresh"; all three reports are re-read together so the numbers on screen always agree. */
  private readonly refreshes = signal(0);
  private readonly request = computed(() => ({ refresh: this.refreshes() }));

  protected readonly summary = loader(this.request, () => this.api.statusSummary());
  protected readonly exposure = loader(this.request, () => this.api.liabilityExposure());
  protected readonly workload = loader(this.request, () => this.api.officerWorkload());

  protected readonly currency = environment.reportingCurrency;
  protected readonly workloadColumns = [
    'officer',
    'openClaims',
    'underReview',
    'awaitingInformation',
    'approved',
    'rejected',
    'settled',
    'totalAssigned',
    'openExposure',
  ];
  protected readonly refreshing = computed(
    () => this.summary.loading() || this.exposure.loading() || this.workload.loading(),
  );

  private readonly money = new Intl.NumberFormat(LOCALE, {
    style: 'currency',
    currency: environment.reportingCurrency,
    maximumFractionDigits: 0,
  });
  private readonly count = new Intl.NumberFormat(LOCALE);

  protected readonly statusBars = computed<BarDatum[]>(() => {
    const summary = this.summary.value();
    if (summary === null) {
      return [];
    }
    return CLAIM_STATUSES.map((status) => {
      const value = summary.claimsByStatus[status] ?? 0;
      return { label: CLAIM_STATUS_LABELS[status], value, valueText: this.count.format(value) };
    });
  });

  protected readonly exposureBars = computed<BarDatum[]>(() => {
    const exposure = this.exposure.value();
    if (exposure === null) {
      return [];
    }
    return exposure.byStatus.map((row) => ({
      label: CLAIM_STATUS_LABELS[row.status],
      value: row.amount,
      valueText: this.money.format(row.amount),
      detail: `${this.count.format(row.claimCount)} ${row.claimCount === 1 ? 'claim' : 'claims'}`,
      muted: !row.includedInExposure,
    }));
  });

  protected readonly workloadBars = computed<BarDatum[]>(() =>
    (this.workload.value()?.officers ?? []).map((officer) => ({
      label: this.officerName(officer),
      value: officer.openClaims,
      valueText: this.count.format(officer.openClaims),
      detail: `${this.count.format(officer.underReview)} under review, ${this.count.format(officer.awaitingInformation)} awaiting information`,
    })),
  );

  protected officerName(officer: OfficerWorkload): string {
    return officer.officerName ?? `Officer #${officer.officerId}`;
  }

  protected refresh(): void {
    this.refreshes.update((count) => count + 1);
  }
}
