import { ClaimDetail, ClaimHistoryEntry, ClaimSummary } from '@shared/models/claim';
import { UserNotification } from '@shared/models/notification';
import { Page } from '@shared/models/page';

/** Test helpers only (never imported by app code): the JSON the backend sends, with sensible defaults. */

export function claimSummary(overrides: Partial<ClaimSummary> = {}): ClaimSummary {
  return {
    id: 42,
    claimNumber: 'CLM-2026-000042',
    policyNumber: 'POL-2026-12345',
    claimType: 'AUTO',
    status: 'SUBMITTED',
    claimAmount: 2500,
    incidentDate: '2026-10-15',
    assignedOfficerId: null,
    createdAt: '2026-10-16T02:30:00Z',
    updatedAt: '2026-10-16T02:30:00Z',
    ...overrides,
  };
}

export function historyEntry(overrides: Partial<ClaimHistoryEntry> = {}): ClaimHistoryEntry {
  return {
    eventType: 'CLAIM_CREATED',
    fromStatus: null,
    toStatus: 'SUBMITTED',
    performedBy: 7,
    performedByRole: 'CLAIMANT',
    details: null,
    createdAt: '2026-10-16T02:30:00Z',
    ...overrides,
  };
}

export function claimDetail(overrides: Partial<ClaimDetail> = {}): ClaimDetail {
  return {
    ...claimSummary(),
    claimantId: 7,
    claimantName: 'Wei Ling Tan',
    claimantEmail: 'weiling.tan@example.com',
    incidentLocation: 'Orchard Road, Singapore',
    description: 'Rear-ended at a traffic light; bumper and tail light damaged.',
    history: [historyEntry()],
    ...overrides,
  };
}

export function page<T>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    first: true,
    last: true,
    ...overrides,
  };
}

export function notification(overrides: Partial<UserNotification> = {}): UserNotification {
  return {
    id: 1,
    claimId: 42,
    claimNumber: 'CLM-2026-000042',
    eventType: 'CLAIM_ASSIGNED',
    message: 'Your claim CLM-2026-000042 is now under review.',
    read: false,
    createdAt: '2026-10-17T03:00:00Z',
    ...overrides,
  };
}
