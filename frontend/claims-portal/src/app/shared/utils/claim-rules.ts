import { ClaimHistoryEntry, ClaimSummary } from '@shared/models/claim';

/** The claims staff's most recent question to the claimant, which is what the claimant is being asked to answer. */
export function latestInfoRequest(history: readonly ClaimHistoryEntry[]): ClaimHistoryEntry | null {
  for (let index = history.length - 1; index >= 0; index--) {
    if (history[index].eventType === 'ADDITIONAL_INFO_REQUESTED') {
      return history[index];
    }
  }
  return null;
}

/**
 * What a particular staff member can do with a claim, from the lifecycle rules the backend enforces:
 *  - `take`               nobody holds it yet (SUBMITTED): any staff member may take it
 *  - `decide`             it is under review and this staff member holds it: approve, reject or ask for information
 *  - `held-by-other`      it is under review but another staff member holds it: read and add notes only
 *  - `awaiting-claimant`  the claimant has been asked for information: nothing to do until they answer
 *  - `settle`             decided (approved or rejected) and this staff member holds it: settle it, which closes it
 *  - `awaiting-settlement` decided but another staff member holds it: read and add notes only
 *  - `closed`             settled: read and add notes only
 * The UI only offers what the backend would accept; the backend still checks every action itself.
 */
export type ReviewAccess =
  | 'take'
  | 'decide'
  | 'held-by-other'
  | 'awaiting-claimant'
  | 'settle'
  | 'awaiting-settlement'
  | 'closed';

export function reviewAccess(claim: Pick<ClaimSummary, 'status' | 'assignedStaffId'>, staffId: number): ReviewAccess {
  switch (claim.status) {
    case 'SUBMITTED':
      return 'take';
    case 'UNDER_REVIEW':
      return claim.assignedStaffId === staffId ? 'decide' : 'held-by-other';
    case 'MORE_INFO_REQUIRED':
      return 'awaiting-claimant';
    case 'APPROVED':
    case 'REJECTED':
      return claim.assignedStaffId === staffId ? 'settle' : 'awaiting-settlement';
    default:
      return 'closed';
  }
}
