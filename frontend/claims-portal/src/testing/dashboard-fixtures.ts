import { ClaimStatus } from '@shared/models/claim';
import {
  ClaimsOverTime,
  ClaimTypeExposure,
  ClaimTypeSummary,
  FilterEcho,
  LiabilityExposure,
  MarketExposure,
  MarketSummary,
  StaffWorkload,
  StaffWorkloadReport,
  StatusSummary,
  TimePeriod,
} from '@shared/models/dashboard';

/** Test helpers only (never imported by app code): the dashboard JSON the backend sends, with sensible defaults. */

/** The echo every report carries: nothing filtered. */
const NO_FILTERS: FilterEcho = { market: null, claimType: null, fromDate: null, toDate: null };

export function claimTypeSummary(overrides: Partial<ClaimTypeSummary> = {}): ClaimTypeSummary {
  return {
    claimType: 'AUTO',
    totalClaims: 146,
    pendingClaims: 98,
    approvedClaims: 16,
    rejectedClaims: 10,
    settledClaims: 22,
    ...overrides,
  };
}

export function claimTypeExposure(overrides: Partial<ClaimTypeExposure> = {}): ClaimTypeExposure {
  return { claimType: 'AUTO', claimCount: 124, exposureInReportingCurrency: 1_500_123.4, ...overrides };
}

export function marketSummary(overrides: Partial<MarketSummary> = {}): MarketSummary {
  return {
    market: 'SG',
    totalClaims: 50,
    pendingClaims: 34,
    approvedClaims: 6,
    rejectedClaims: 4,
    settledClaims: 6,
    ...overrides,
  };
}

export function statusSummary(overrides: Partial<StatusSummary> = {}): StatusSummary {
  return {
    totalClaims: 250,
    pendingClaims: 168,
    approvedClaims: 28,
    rejectedClaims: 18,
    settledClaims: 36,
    claimsByStatus: {
      SUBMITTED: 100,
      UNDER_REVIEW: 56,
      MORE_INFO_REQUIRED: 12,
      APPROVED: 28,
      REJECTED: 18,
      SETTLED: 36,
    },
    ...NO_FILTERS,
    byMarket: [
      marketSummary({ market: 'AU', totalClaims: 70, pendingClaims: 50, approvedClaims: 10, rejectedClaims: 4, settledClaims: 6 }),
      marketSummary({ market: 'JP', totalClaims: 40, pendingClaims: 30, approvedClaims: 5, rejectedClaims: 2, settledClaims: 3 }),
      marketSummary({ market: 'SG', totalClaims: 50, pendingClaims: 34 }),
    ],
    byClaimType: [
      claimTypeSummary(),
      claimTypeSummary({ claimType: 'HOME', totalClaims: 104, pendingClaims: 70, approvedClaims: 12, rejectedClaims: 8, settledClaims: 14 }),
    ],
    generatedAt: '2026-10-23T08:00:00Z',
    ...overrides,
  };
}

export function marketExposure(overrides: Partial<MarketExposure> = {}): MarketExposure {
  return {
    market: 'SG',
    currency: 'SGD',
    rate: 1.35,
    claimCount: 40,
    exposure: 1_100_000,
    exposureInReportingCurrency: 814_814.81,
    ...overrides,
  };
}

export function liabilityExposure(overrides: Partial<LiabilityExposure> = {}): LiabilityExposure {
  return {
    reportingCurrency: 'USD',
    ...NO_FILTERS,
    totalLiabilityExposure: 2_674_303.5,
    exposedClaims: 214,
    excludedSettledAmount: 480_955.5,
    formula: 'SUM(claimAmount) WHERE status != SETTLED',
    byStatus: [
      { status: 'SUBMITTED', claimCount: 100, amount: 850_718, includedInExposure: true },
      { status: 'UNDER_REVIEW', claimCount: 56, amount: 1_033_466, includedInExposure: true },
      { status: 'MORE_INFO_REQUIRED', claimCount: 12, amount: 321_109, includedInExposure: true },
      { status: 'APPROVED', claimCount: 28, amount: 273_959, includedInExposure: true },
      { status: 'REJECTED', claimCount: 18, amount: 195_052, includedInExposure: true },
      { status: 'SETTLED', claimCount: 36, amount: 480_955.5, includedInExposure: false },
    ],
    byMarket: [
      marketExposure({ market: 'AU', currency: 'AUD', rate: 1.5, claimCount: 60, exposure: 812_345.67, exposureInReportingCurrency: 541_563.78 }),
      marketExposure({ market: 'JP', currency: 'JPY', rate: 150, claimCount: 30, exposure: 91_000_000, exposureInReportingCurrency: 606_666.67 }),
      marketExposure(),
    ],
    byClaimType: [claimTypeExposure(), claimTypeExposure({ claimType: 'HOME', claimCount: 90, exposureInReportingCurrency: 1_174_180.1 })],
    generatedAt: '2026-10-23T08:00:00Z',
    ...overrides,
  };
}

export function staffWorkload(overrides: Partial<StaffWorkload> = {}): StaffWorkload {
  return {
    staffId: 2,
    staffName: 'Olivia Bennett',
    openClaims: 20,
    underReview: 18,
    awaitingInformation: 2,
    approved: 12,
    rejected: 5,
    settled: 16,
    totalAssigned: 53,
    openExposure: 206_317,
    ...overrides,
  };
}

export function staffWorkloadReport(overrides: Partial<StaffWorkloadReport> = {}): StaffWorkloadReport {
  return {
    reportingCurrency: 'USD',
    ...NO_FILTERS,
    unassignedClaims: 100,
    staff: [staffWorkload(), staffWorkload({ staffId: 3, staffName: null, openClaims: 14, openExposure: 563_285 })],
    generatedAt: '2026-10-23T08:00:00Z',
    ...overrides,
  };
}

/** One week (or month) of claims; the status split follows the pending and decided totals given. */
export function timePeriod(start: string, pendingClaims: number, decidedClaims: number, overrides: Partial<TimePeriod> = {}): TimePeriod {
  const claimsByStatus: Record<ClaimStatus, number> = {
    SUBMITTED: pendingClaims,
    UNDER_REVIEW: 0,
    MORE_INFO_REQUIRED: 0,
    APPROVED: decidedClaims,
    REJECTED: 0,
    SETTLED: 0,
  };
  return { start, totalClaims: pendingClaims + decidedClaims, pendingClaims, decidedClaims, claimsByStatus, ...overrides };
}

export function claimsOverTime(overrides: Partial<ClaimsOverTime> = {}): ClaimsOverTime {
  return {
    ...NO_FILTERS,
    granularity: 'WEEK',
    periods: [timePeriod('2026-09-28', 6, 14), timePeriod('2026-10-05', 9, 8), timePeriod('2026-10-12', 0, 0), timePeriod('2026-10-19', 12, 2)],
    generatedAt: '2026-10-23T08:00:00Z',
    ...overrides,
  };
}
