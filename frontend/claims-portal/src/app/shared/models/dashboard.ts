import { ClaimStatus } from './claim';

/** `GET /api/dashboard/status-summary`. totalClaims = pending + approved + rejected + settled. */
export interface StatusSummary {
  totalClaims: number;
  /** SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED. */
  pendingClaims: number;
  approvedClaims: number;
  rejectedClaims: number;
  settledClaims: number;
  claimsByStatus: Record<ClaimStatus, number>;
  generatedAt: string;
}

export interface OfficerWorkload {
  officerId: number;
  officerName: string | null;
  /** underReview + awaitingInformation: what the officer still has to decide. */
  openClaims: number;
  underReview: number;
  awaitingInformation: number;
  approved: number;
  rejected: number;
  settled: number;
  totalAssigned: number;
  /** Sum of claimAmount over the open claims. */
  openExposure: number;
}

/** `GET /api/dashboard/officer-workload`. Officers are listed busiest first. */
export interface OfficerWorkloadReport {
  /** Claims waiting in the work queue for any officer to take. */
  unassignedClaims: number;
  officers: OfficerWorkload[];
  generatedAt: string;
}

export interface StatusExposure {
  status: ClaimStatus;
  claimCount: number;
  amount: number;
  /** False only for SETTLED, which the exposure formula leaves out. */
  includedInExposure: boolean;
}

/** `GET /api/dashboard/liability-exposure`: SUM(claimAmount) WHERE status != SETTLED. */
export interface LiabilityExposure {
  totalLiabilityExposure: number;
  exposedClaims: number;
  excludedSettledAmount: number;
  formula: string;
  byStatus: StatusExposure[];
  generatedAt: string;
}
