import { ClaimHistoryEntry, ClaimStatus } from '@shared/models/claim';
import { latestInfoRequest, reviewAccess } from './claim-rules';

const entry = (eventType: ClaimHistoryEntry['eventType'], details: string | null = null): ClaimHistoryEntry => ({
  eventType,
  fromStatus: null,
  toStatus: null,
  performedBy: 2,
  performedByRole: 'CLAIMS_STAFF',
  details,
  createdAt: '2026-10-01T00:00:00Z',
});

describe('reviewAccess', () => {
  const ME = 2;

  it.each<[ClaimStatus, number | null, string]>([
    ['SUBMITTED', null, 'take'],
    ['UNDER_REVIEW', ME, 'decide'],
    ['UNDER_REVIEW', 9, 'held-by-other'],
    ['MORE_INFO_REQUIRED', ME, 'awaiting-claimant'],
    ['MORE_INFO_REQUIRED', 9, 'awaiting-claimant'],
    ['APPROVED', ME, 'settle'],
    ['REJECTED', ME, 'settle'],
    ['APPROVED', 9, 'awaiting-settlement'],
    ['REJECTED', 9, 'awaiting-settlement'],
    ['SETTLED', ME, 'closed'],
    ['SETTLED', 9, 'closed'],
  ])('%s held by %s → %s', (status, assignedStaffId, expected) => {
    expect(reviewAccess({ status, assignedStaffId }, ME)).toBe(expected);
  });
});

describe('latestInfoRequest', () => {
  it('is the most recent request, not the first', () => {
    const history = [
      entry('CLAIM_CREATED'),
      entry('ADDITIONAL_INFO_REQUESTED', 'Send the police report'),
      entry('ADDITIONAL_INFO_RECEIVED', 'Attached'),
      entry('ADDITIONAL_INFO_REQUESTED', 'Now send the repair estimate'),
    ];

    expect(latestInfoRequest(history)?.details).toBe('Now send the repair estimate');
  });

  it('is null when nothing was ever asked', () => {
    expect(latestInfoRequest([entry('CLAIM_CREATED')])).toBeNull();
    expect(latestInfoRequest([])).toBeNull();
  });
});
