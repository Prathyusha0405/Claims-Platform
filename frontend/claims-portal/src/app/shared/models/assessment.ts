import { ClaimStatus } from './claim';

/** The claims staff member's assessment of a claim plus the claim's status right after the action (assessment-service). */
export interface Assessment {
  claimId: number;
  claimNumber: string;
  claimStatus: ClaimStatus;
  staffId: number;
  staffName: string | null;
  assignedAt: string;
  /** Set once the staff member approves or rejects; null while the claim is being reviewed. */
  decision: 'APPROVED' | 'REJECTED' | null;
  decisionReason: string | null;
  decidedAt: string | null;
}

export type NoteType = 'GENERAL' | 'INFO_REQUEST' | 'APPROVAL' | 'REJECTION';

export const NOTE_TYPE_LABELS: Readonly<Record<NoteType, string>> = {
  GENERAL: 'Note',
  INFO_REQUEST: 'Information request',
  APPROVAL: 'Approval',
  REJECTION: 'Rejection',
};

/** An internal review note. Never shown to claimants. */
export interface ClaimNote {
  id: number;
  claimId: number;
  staffId: number;
  staffName: string | null;
  noteType: NoteType;
  content: string;
  createdAt: string;
}
