import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { DashboardApi } from '@core/services/dashboard-api';
import { BarChart, BarDatum } from '@shared/components/bar-chart/bar-chart';
import { LoadError } from '@shared/components/load-error/load-error';
import {
  CLAIM_STATUS_LABELS,
  CLAIM_STATUSES,
  Currency,
  Market,
  MARKET_CURRENCY,
  MARKET_LABELS,
  MARKETS,
} from '@shared/models/claim';
import { StaffWorkload } from '@shared/models/dashboard';
import { MoneyPipe } from '@shared/pipes/money-pipe';
import { loader } from '@shared/utils/loader';
import { formatMoney } from '@shared/utils/money';

const LOCALE = 'en-US';

/** One row of the "by market" table: a market's claim counts joined with its exposure. */
interface MarketRow {
  market: Market;
  name: string;
  currency: Currency;
  totalClaims: number;
  pendingClaims: number;
  /** In the market's own currency. */
  exposure: number;
  exposureInReportingCurrency: number;
}

/**
 * The manager's view: liability exposure, claim counts by status, how the markets compare, and what each staff member
 * is carrying. Every figure can be limited to one market; money is shown in the reporting currency the server
 * converted it to (indicative fixed rates), except the by-market table, which also shows each market's own currency.
 */
@Component({
  imports: [
    BarChart,
    DatePipe,
    DecimalPipe,
    LoadError,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MoneyPipe,
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
  /** The market every report is limited to; empty means all markets. */
  protected readonly market = signal<Market | ''>('');
  private readonly request = computed(() => ({ refresh: this.refreshes(), market: this.market() }));

  protected readonly summary = loader(this.request, ({ market }) => this.api.statusSummary(market || undefined));
  protected readonly exposure = loader(this.request, ({ market }) => this.api.liabilityExposure(market || undefined));
  protected readonly workload = loader(this.request, ({ market }) => this.api.staffWorkload(market || undefined));

  protected readonly markets = MARKETS;
  protected readonly marketLabels = MARKET_LABELS;
  protected readonly scope = computed(() => {
    const market = this.market();
    return market === '' ? 'All markets' : MARKET_LABELS[market];
  });
  protected readonly workloadColumns = [
    'staff',
    'openClaims',
    'underReview',
    'awaitingInformation',
    'approved',
    'rejected',
    'settled',
    'totalAssigned',
    'openExposure',
  ];
  protected readonly marketColumns = ['market', 'currency', 'totalClaims', 'pendingClaims', 'exposure', 'exposureInReportingCurrency'];
  protected readonly refreshing = computed(
    () => this.summary.loading() || this.exposure.loading() || this.workload.loading(),
  );

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
      valueText: formatMoney(row.amount, exposure.reportingCurrency, true),
      detail: `${this.count.format(row.claimCount)} ${row.claimCount === 1 ? 'claim' : 'claims'}`,
      muted: !row.includedInExposure,
    }));
  });

  protected readonly workloadBars = computed<BarDatum[]>(() =>
    (this.workload.value()?.staff ?? []).map((staffMember) => ({
      label: this.staffName(staffMember),
      value: staffMember.openClaims,
      valueText: this.count.format(staffMember.openClaims),
      detail: `${this.count.format(staffMember.underReview)} under review, ${this.count.format(staffMember.awaitingInformation)} awaiting information`,
    })),
  );

  /** Every market that has claims, whichever one is selected: the table is the comparison, so it never shrinks. */
  protected readonly marketRows = computed<MarketRow[]>(() => {
    const summary = this.summary.value();
    const exposure = this.exposure.value();
    if (summary === null || exposure === null) {
      return [];
    }
    const exposures = new Map(exposure.byMarket.map((row) => [row.market, row]));
    return summary.byMarket.map((row) => {
      const money = exposures.get(row.market);
      return {
        market: row.market,
        name: MARKET_LABELS[row.market] ?? row.market,
        currency: money?.currency ?? MARKET_CURRENCY[row.market],
        totalClaims: row.totalClaims,
        pendingClaims: row.pendingClaims,
        exposure: money?.exposure ?? 0,
        exposureInReportingCurrency: money?.exposureInReportingCurrency ?? 0,
      };
    });
  });

  /** Only what can be added up across currencies: counts, and the exposure already converted to one currency. */
  protected readonly marketTotals = computed(() => {
    const rows = this.marketRows();
    return {
      totalClaims: rows.reduce((sum, row) => sum + row.totalClaims, 0),
      pendingClaims: rows.reduce((sum, row) => sum + row.pendingClaims, 0),
      exposureInReportingCurrency: rows.reduce((sum, row) => sum + row.exposureInReportingCurrency, 0),
    };
  });

  protected staffName(staffMember: StaffWorkload): string {
    return staffMember.staffName ?? `Staff #${staffMember.staffId}`;
  }

  protected filterByMarket(market: Market | ''): void {
    this.market.set(market);
  }

  protected refresh(): void {
    this.refreshes.update((count) => count + 1);
  }
}
