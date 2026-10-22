/** The three roles auth-service puts in the JWT `role` claim. */
export type Role = 'CLAIMANT' | 'OFFICER' | 'ADMIN';

export const ROLES: readonly Role[] = ['CLAIMANT', 'OFFICER', 'ADMIN'];

/** How a role is named on screen. */
export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  CLAIMANT: 'Claimant',
  OFFICER: 'Claims officer',
  ADMIN: 'Administrator',
};

export const isRole = (value: unknown): value is Role => typeof value === 'string' && ROLES.includes(value as Role);
