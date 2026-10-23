/** The three roles auth-service puts in the JWT `role` claim. */
export type Role = 'CLAIMANT' | 'CLAIMS_STAFF' | 'MANAGER';

export const ROLES: readonly Role[] = ['CLAIMANT', 'CLAIMS_STAFF', 'MANAGER'];

/** How a role is named on screen. */
export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  CLAIMANT: 'Claimant',
  CLAIMS_STAFF: 'Claims staff',
  MANAGER: 'Manager',
};

export const isRole = (value: unknown): value is Role => typeof value === 'string' && ROLES.includes(value as Role);
