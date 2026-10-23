import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { DashboardApi } from '@core/services/dashboard-api';
import { BarChart, BarDatum } from '@shared/components/bar-chart/bar-chart';
import { ChartSeries } from '@shared/components/chart-series';
import { ColumnChart, ColumnDatum } from '@shared/components/column-chart/column-chart';
import { LoadError } from '@shared/components/load-error/load-error';
import { StackedBarChart, StackedRow } from '@shared/components/stacked-bar-chart/stacked-bar-chart';
import {
  CLAIM_STATUS_LABELS,
  CLAIM_STATUSES,
  CLAIM_TYPE_LABELS,
  CLAIM_TYPES,
  ClaimType,
  Currency,
  Market,
  MARKET_CURRENCY,
  MARKET_LABELS,
} from '@shared/models/claim';
import { DashboardFilters, StaffWorkload } from '@shared/models/dashboard';
import { MoneyPipe } from '@shared/pipes/money-pipe';
import { loader } from '@shared/utils/loader';
import { formatMoney } from '@shared/utils/money';
import { DashboardFilterBar } from './dashboard-filters';
import { dayAndMonth, describeRange, formatIsoDate, monthAndYear } from './period';

const LOCALE = 'en-US';

// Chart colours (the dataviz default palette). On the status charts a colour means what the manager does with the
// claims: blue is the work to act on (pending, under review), orange the second kind of open work (awaiting
// information), gray is decided claims, shown for context.
const BLUE = '#2a78d6';
const ORANGE = '#eb6834';
const GRAY = '#898781';
const ON_GRAY = '#0b0b0b';

/** Pending against decided: used for claims over time and for claims by market. */
const PENDING_DECIDED: readonly ChartSeries[] = [
  { key: 'pending', label: 'Pending', color: BLUE },
  { key: 'decided', label: 'Decided', color: GRAY, textColor: ON_GRAY },
];

const WORKLOAD_SERIES: readonly ChartSeries[] = [
  { key: 'underReview', label: 'Under review', color: BLUE },
  { key: 'awaitingInformation', label: 'Awaiting information', color: ORANGE },
  { key: 'decided', label: 'Decided', color: GRAY, textColor: ON_GRAY },
];

/**
 * The claim type chart is a different card with its own legend, and a colour there means a type. It is fixed per type,
 * so a filter that removes one type never repaints the others; the gray is the fold-into-"Other" slot.
 */
const CLAIM_TYPE_COLORS: Readonly<Record<ClaimType, string>> = {
  AUTO: BLUE,
  HOME: ORANGE,
  HEALTH: '#1baf7a',
  TRAVEL: '#eda100',
  PERSONAL_ACCIDENT: '#e87ba4',
  OTHER: GRAY,
};

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
 * The manager's view: liability exposure, claim counts by status, claims over time, how the markets and the claim
 * types compare, and what each staff member is carrying. A single row of filters above (market, period, claim type)
 * scopes every figure and chart on the page, which are all read together so they always agree. Money is shown in the
 * reporting currency the server converted it to (indicative fixed rates), except the by-market table, which also shows
 * each market's own currency.
 */
@Component({
  imports: [
    BarChart,
    ColumnChart,
    DashboardFilterBar,
    DatePipe,
    DecimalPipe,
    LoadError,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MoneyPipe,
    StackedBarChart,
  ],
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dashboard.scss',
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly api = inject(DashboardApi);

  /** Bumped by "Refresh"; every report is re-read together so the numbers on screen always agree. */
  private readonly refreshes = signal(0);
  /** What every report is limited to; an empty object means everything. */
  protected readonly filters = signal<DashboardFilters>({});
  private readonly request = computed(() => ({ refresh: this.refreshes(), filters: this.filters() }));

  protected readonly summary = loader(this.request, ({ filters }) => this.api.statusSummary(filters));
  protected readonly exposure = loader(this.request, ({ filters }) => this.api.liabilityExposure(filters));
  protected readonly workload = loader(this.request, ({ filters }) => this.api.staffWorkload(filters));
  protected readonly trend = loader(this.request, ({ filters }) => this.api.claimsOverTime(filters));

  /** The market the by-market table marks as selected; empty when none is. */
  protected readonly market = computed(() => this.filters().market ?? '');
  protected readonly scope = computed(() => {
    const { market, claimType, fromDate, toDate } = this.filters();
    const parts = [market ? MARKET_LABELS[market] : 'All markets'];
    if (claimType) {
      parts.push(CLAIM_TYPE_LABELS[claimType]);
    }
    const dates = describeRange({ fromDate, toDate });
    if (dates !== '') {
      parts.push(dates);
    }
    return parts.join(' · ');
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
    () => this.summary.loading() || this.exposure.loading() || this.workload.loading() || this.trend.loading(),
  );

  protected readonly pendingDecided = PENDING_DECIDED;
  protected readonly workloadSeries = WORKLOAD_SERIES;

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

  /** "per week" or "per month", as the server chose for the length of the window. */
  protected readonly trendUnit = computed(() => (this.trend.value()?.granularity === 'MONTH' ? 'month' : 'week'));

  protected readonly trendColumns = computed<ColumnDatum[]>(() => {
    const trend = this.trend.value();
    if (trend === null) {
      return [];
    }
    const monthly = trend.granularity === 'MONTH';
    return trend.periods.map((period) => ({
      label: monthly ? monthAndYear(period.start) : dayAndMonth(period.start),
      title: monthly ? monthAndYear(period.start) : `Week of ${formatIsoDate(period.start)}`,
      values: { pending: period.pendingClaims, decided: period.decidedClaims },
    }));
  });

  /** Claims per market, pending against decided, biggest market first. */
  protected readonly marketClaimRows = computed<StackedRow[]>(() =>
    [...(this.summary.value()?.byMarket ?? [])]
      .sort((a, b) => b.totalClaims - a.totalClaims)
      .map((row) => ({
        label: MARKET_LABELS[row.market] ?? row.market,
        values: { pending: row.pendingClaims, decided: row.approvedClaims + row.rejectedClaims + row.settledClaims },
      })),
  );

  /** Exposure per market in the reporting currency, one series, biggest first. */
  protected readonly marketExposureBars = computed<BarDatum[]>(() => {
    const exposure = this.exposure.value();
    if (exposure === null) {
      return [];
    }
    return [...exposure.byMarket]
      .sort((a, b) => b.exposureInReportingCurrency - a.exposureInReportingCurrency)
      .map((row) => ({
        label: MARKET_LABELS[row.market] ?? row.market,
        value: row.exposureInReportingCurrency,
        valueText: formatMoney(row.exposureInReportingCurrency, exposure.reportingCurrency, true),
        detail: `${this.count.format(row.claimCount)} ${row.claimCount === 1 ? 'claim' : 'claims'}, ${formatMoney(row.exposure, row.currency, true)}`,
      }));
  });

  /** Only the claim types that have claims in scope, each in its own fixed colour. */
  protected readonly typeSeries = computed<ChartSeries[]>(() => {
    const present = new Set<ClaimType>([
      ...(this.summary.value()?.byClaimType ?? []).map((row) => row.claimType),
      ...(this.exposure.value()?.byClaimType ?? []).map((row) => row.claimType),
    ]);
    return CLAIM_TYPES.filter((type) => present.has(type)).map((type) => ({
      key: type,
      label: CLAIM_TYPE_LABELS[type],
      color: CLAIM_TYPE_COLORS[type],
    }));
  });

  /** Two rows to compare by mix: how many claims, and how much exposure, each type accounts for. */
  protected readonly typeRows = computed<StackedRow[]>(() => {
    const summary = this.summary.value();
    const exposure = this.exposure.value();
    if (summary === null || exposure === null || this.typeSeries().length === 0) {
      return [];
    }
    return [
      {
        label: 'Claims',
        values: Object.fromEntries(summary.byClaimType.map((row) => [row.claimType, row.totalClaims])),
      },
      {
        label: 'Liability exposure',
        values: Object.fromEntries(exposure.byClaimType.map((row) => [row.claimType, row.exposureInReportingCurrency])),
        format: (value: number) => formatMoney(value, exposure.reportingCurrency, true),
      },
    ];
  });

  /** Every staff member as the API lists them (busiest first): open claims in colour, decided ones in gray. */
  protected readonly workloadRows = computed<StackedRow[]>(() =>
    (this.workload.value()?.staff ?? []).map((staffMember) => ({
      label: this.staffName(staffMember),
      values: {
        underReview: staffMember.underReview,
        awaitingInformation: staffMember.awaitingInformation,
        decided: staffMember.approved + staffMember.rejected + staffMember.settled,
      },
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

  protected applyFilters(filters: DashboardFilters): void {
    this.filters.set(filters);
  }

  protected refresh(): void {
    this.refreshes.update((count) => count + 1);
  }
}
