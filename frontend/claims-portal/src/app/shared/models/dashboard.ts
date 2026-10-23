import { ClaimStatus, Currency, Market } from './claim';

/** One market's claim counts: a row of the "by market" table. The list always covers every market that has claims. */
export interface MarketSummary {
  market: Market;
  totalClaims: number;
  pendingClaims: number;
  approvedClaims: number;
  rejectedClaims: number;
  settledClaims: number;
}

/** `GET /api/dashboard/status-summary`. totalClaims = pending + approved + rejected + settled. */
export interface StatusSummary {
  totalClaims: number;
  /** SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED. */
  pendingClaims: number;
  approvedClaims: number;
  rejectedClaims: number;
  settledClaims: number;
  claimsByStatus: Record<ClaimStatus, number>;
  /** The market the figures above are limited to; null for all markets. */
  market: Market | null;
  /** Every market, whatever `market` is: the comparison table does not shrink when you filter. */
  byMarket: MarketSummary[];
  generatedAt: string;
}

export interface StaffWorkload {
  staffId: number;
  staffName: string | null;
  /** underReview + awaitingInformation: what the staff member still has to decide. */
  openClaims: number;
  underReview: number;
  awaitingInformation: number;
  approved: number;
  rejected: number;
  settled: number;
  totalAssigned: number;
  /** Sum of the open claims, converted to the report's `reportingCurrency`. */
  openExposure: number;
}

/** `GET /api/dashboard/staff-workload`. Staff are listed busiest first. */
export interface StaffWorkloadReport {
  /** The currency `openExposure` is shown in (indicative fixed rates, not live ones). */
  reportingCurrency: string;
  /** The market the report is limited to; null for all markets. */
  market: Market | null;
  /** Claims waiting in the work queue for any staff member to take (in `market`, when one is given). */
  unassignedClaims: number;
  staff: StaffWorkload[];
  generatedAt: string;
}

export interface StatusExposure {
  status: ClaimStatus;
  claimCount: number;
  /** In the report's `reportingCurrency`. */
  amount: number;
  /** False only for SETTLED, which the exposure formula leaves out. */
  includedInExposure: boolean;
}

/** One market's exposure, in its own currency and in the reporting currency. */
export interface MarketExposure {
  market: Market;
  currency: Currency;
  /** Units of `currency` per one unit of the reporting currency. */
  rate: number;
  /** Claims that are not settled. */
  claimCount: number;
  /** In `currency`. */
  exposure: number;
  exposureInReportingCurrency: number;
}

/** `GET /api/dashboard/liability-exposure`: SUM(claimAmount) WHERE status != SETTLED, converted to one currency. */
export interface LiabilityExposure {
  reportingCurrency: string;
  /** The market the totals and `byStatus` are limited to; null for all markets. `byMarket` is never limited. */
  market: Market | null;
  totalLiabilityExposure: number;
  exposedClaims: number;
  excludedSettledAmount: number;
  formula: string;
  byStatus: StatusExposure[];
  byMarket: MarketExposure[];
  generatedAt: string;
}
