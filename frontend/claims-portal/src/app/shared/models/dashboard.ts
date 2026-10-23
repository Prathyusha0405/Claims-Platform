import { ClaimStatus, ClaimType, Currency, Market } from './claim';

/**
 * What a manager can narrow every dashboard to. Leave a field out to leave that dimension open. The dates are
 * `yyyy-MM-dd`, both inclusive, and apply to the day a claim was submitted.
 */
export interface DashboardFilters {
  market?: Market;
  claimType?: ClaimType;
  fromDate?: string;
  toDate?: string;
}

/** The filters a report was computed for, echoed back so the page can say what it is showing. */
export interface FilterEcho {
  market: Market | null;
  claimType: ClaimType | null;
  fromDate: string | null;
  toDate: string | null;
}

/** One claim type's claim counts. Never narrowed by the claim type filter, so the types can be read against each other. */
export interface ClaimTypeSummary {
  claimType: ClaimType;
  totalClaims: number;
  pendingClaims: number;
  approvedClaims: number;
  rejectedClaims: number;
  settledClaims: number;
}

/** One claim type's liability exposure, in the report's reporting currency. */
export interface ClaimTypeExposure {
  claimType: ClaimType;
  /** Claims that are not settled. */
  claimCount: number;
  exposureInReportingCurrency: number;
}

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
export interface StatusSummary extends FilterEcho {
  totalClaims: number;
  /** SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED. */
  pendingClaims: number;
  approvedClaims: number;
  rejectedClaims: number;
  settledClaims: number;
  claimsByStatus: Record<ClaimStatus, number>;
  /** Every market, whatever `market` is: the comparison table does not shrink when you filter. */
  byMarket: MarketSummary[];
  /** Every claim type that has claims in scope, whatever `claimType` is. Follows the market and the dates. */
  byClaimType: ClaimTypeSummary[];
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
export interface StaffWorkloadReport extends FilterEcho {
  /** The currency `openExposure` is shown in (indicative fixed rates, not live ones). */
  reportingCurrency: string;
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
export interface LiabilityExposure extends FilterEcho {
  reportingCurrency: string;
  totalLiabilityExposure: number;
  exposedClaims: number;
  excludedSettledAmount: number;
  formula: string;
  byStatus: StatusExposure[];
  /** Never limited by `market`. */
  byMarket: MarketExposure[];
  /** Never limited by `claimType`; follows the market and the dates. */
  byClaimType: ClaimTypeExposure[];
  generatedAt: string;
}

/** The size of one bucket on the claims-over-time chart; the server picks it from the length of the window. */
export type Granularity = 'WEEK' | 'MONTH';

/** One week or month of claims, by the day they were submitted. */
export interface TimePeriod {
  /** First day of the period, `yyyy-MM-dd`: a Monday for WEEK, the 1st for MONTH. */
  start: string;
  totalClaims: number;
  /** SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED. */
  pendingClaims: number;
  /** APPROVED + REJECTED + SETTLED. */
  decidedClaims: number;
  claimsByStatus: Record<ClaimStatus, number>;
}

/** `GET /api/dashboard/claims-over-time`. Periods are ascending with no gaps, and empty when nothing matches. */
export interface ClaimsOverTime extends FilterEcho {
  granularity: Granularity;
  periods: TimePeriod[];
  generatedAt: string;
}
