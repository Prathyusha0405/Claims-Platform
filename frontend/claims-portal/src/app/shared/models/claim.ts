/** The claim lifecycle: SUBMITTED → UNDER_REVIEW ⇄ MORE_INFO_REQUIRED → APPROVED | REJECTED → SETTLED. */
export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'MORE_INFO_REQUIRED' | 'APPROVED' | 'REJECTED' | 'SETTLED';

/** In lifecycle order, which is also the order dashboards list them in. */
export const CLAIM_STATUSES: readonly ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'MORE_INFO_REQUIRED',
  'APPROVED',
  'REJECTED',
  'SETTLED',
];

export const CLAIM_STATUS_LABELS: Readonly<Record<ClaimStatus, string>> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  MORE_INFO_REQUIRED: 'More info required',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SETTLED: 'Settled',
};

/** Material Icons ligature that stands for each status. */
export const CLAIM_STATUS_ICONS: Readonly<Record<ClaimStatus, string>> = {
  SUBMITTED: 'upload_file',
  UNDER_REVIEW: 'manage_search',
  MORE_INFO_REQUIRED: 'help_outline',
  APPROVED: 'check_circle',
  REJECTED: 'cancel',
  SETTLED: 'paid',
};

export type ClaimType ='AUTO' | 'HOME' | 'HEALTH' | 'TRAVEL' | 'PERSONAL_ACCIDENT' | 'OTHER';

export const CLAIM_TYPES: readonly ClaimType[] = ['AUTO', 'HOME', 'HEALTH', 'TRAVEL', 'PERSONAL_ACCIDENT', 'OTHER'];

export const CLAIM_TYPE_LABELS: Readonly<Record<ClaimType, string>> = {
  AUTO: 'Auto',
  HOME: 'Home',
  HEALTH: 'Health',
  TRAVEL: 'Travel',
  PERSONAL_ACCIDENT: 'Personal accident',
  OTHER: 'Other',
};

/** The markets Chubb APAC writes motor and property business in, by ISO country code. */
export type Market = 'AU' | 'HK' | 'JP' | 'MY' | 'NZ' | 'SG';

export const MARKETS: readonly Market[] = ['AU', 'HK', 'JP', 'MY', 'NZ', 'SG'];

export const MARKET_LABELS: Readonly<Record<Market, string>> = {
  AU: 'Australia',
  HK: 'Hong Kong',
  JP: 'Japan',
  MY: 'Malaysia',
  NZ: 'New Zealand',
  SG: 'Singapore',
};

/** The currencies the six markets trade in (ISO 4217). A claim's amount is always in the currency of its market. */
export type Currency = 'AUD' | 'HKD' | 'JPY' | 'MYR' | 'NZD' | 'SGD';

export const CURRENCIES: readonly Currency[] = ['AUD', 'HKD', 'JPY', 'MYR', 'NZD', 'SGD'];

/** Which currency each market uses. The server applies the same rule when a claim is filed. */
export const MARKET_CURRENCY: Readonly<Record<Market, Currency>> = {
  AU: 'AUD',
  HK: 'HKD',
  JP: 'JPY',
  MY: 'MYR',
  NZ: 'NZD',
  SG: 'SGD',
};

/** Things that happen to a claim; each is audited in the claim history and may notify someone. */
export type ClaimEventType =
  | 'CLAIM_CREATED'
  | 'CLAIM_ASSIGNED'
  | 'CLAIM_APPROVED'
  | 'CLAIM_REJECTED'
  | 'ADDITIONAL_INFO_REQUESTED'
  | 'ADDITIONAL_INFO_RECEIVED';

export const CLAIM_EVENT_LABELS: Readonly<Record<ClaimEventType, string>> = {
  CLAIM_CREATED: 'Claim submitted',
  CLAIM_ASSIGNED: 'Assigned to a claims staff member',
  CLAIM_APPROVED: 'Claim approved',
  CLAIM_REJECTED: 'Claim rejected',
  ADDITIONAL_INFO_REQUESTED: 'More information requested',
  ADDITIONAL_INFO_RECEIVED: 'Information provided',
};

/** Material Icons ligature that stands for each event. */
export const CLAIM_EVENT_ICONS: Readonly<Record<ClaimEventType, string>> = {
  CLAIM_CREATED: 'upload_file',
  CLAIM_ASSIGNED: 'person_add',
  CLAIM_APPROVED: 'check_circle',
  CLAIM_REJECTED: 'cancel',
  ADDITIONAL_INFO_REQUESTED: 'help_outline',
  ADDITIONAL_INFO_RECEIVED: 'mark_email_read',
};

/** One row of a claim list (my claims, search results, the work queue). */
export interface ClaimSummary {
  id: number;
  claimNumber: string;
  policyNumber: string;
  claimType: ClaimType;
  market: Market;
  status: ClaimStatus;
  /** In `currency`, the currency of the claim's market. */
  claimAmount: number;
  currency: Currency;
  /** `yyyy-MM-dd`. */
  incidentDate: string;
  assignedStaffId: number | null;
  /** ISO-8601 instants. */
  createdAt: string;
  updatedAt: string;
}

/** One step of a claim's audit timeline. */
export interface ClaimHistoryEntry {
  eventType: ClaimEventType;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus | null;
  performedBy: number | null;
  performedByRole: string | null;
  /** Free text: a staff member's comment, a rejection reason, the claimant's reply. */
  details: string | null;
  createdAt: string;
}

export interface ClaimDetail extends ClaimSummary {
  claimantId: number;
  claimantName: string;
  claimantEmail: string;
  incidentLocation: string | null;
  description: string;
  /** Oldest first. */
  history: ClaimHistoryEntry[];
}

/** `POST /api/claims` body. */
export interface CreateClaimRequest {
  policyNumber: string;
  claimType: ClaimType;
  market: Market;
  /** `yyyy-MM-dd`, not in the future. */
  incidentDate: string;
  incidentLocation?: string;
  description: string;
  claimAmount: number;
}

/** `GET /api/claims/search` filters. Every one is optional; the ones given are combined with AND. */
export interface ClaimSearchCriteria {
  claimNumber?: string;
  policyNumber?: string;
  status?: ClaimStatus;
  /** Exact market, by country code. */
  market?: Market;
  /** Submitted on or after this date, `yyyy-MM-dd`. */
  fromDate?: string;
  /** Submitted on or before this date, `yyyy-MM-dd`. */
  toDate?: string;
  assignedStaffId?: number;
}

/** The sortable columns claim-service accepts (`ClaimController`). */
export type ClaimSortField =
  | 'createdAt'
  | 'updatedAt'
  | 'claimNumber'
  | 'policyNumber'
  | 'market'
  | 'status'
  | 'claimAmount'
  | 'incidentDate';
