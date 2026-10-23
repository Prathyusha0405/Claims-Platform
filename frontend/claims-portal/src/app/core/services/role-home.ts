import { Role } from '@core/models/role';

/** Where each role lands after signing in, and the first page of its area. */
const ROLE_HOME: Readonly<Record<Role, string>> = {
  CLAIMANT: '/claimant/my-claims',
  CLAIMS_STAFF: '/staff/work-queue',
  MANAGER: '/manager/dashboard',
};

/** The URL prefix each role is allowed to open. */
const ROLE_AREA: Readonly<Record<Role, string>> = {
  CLAIMANT: '/claimant',
  CLAIMS_STAFF: '/staff',
  MANAGER: '/manager',
};

export const roleHome = (role: Role): string => ROLE_HOME[role];

/**
 * Where to send someone who has just signed in. The requested page (`returnUrl`) is honoured only when it is an
 * in-app path inside the area their role may open; anything else (another origin, protocol-relative `//host`, a
 * different role's area, garbage) falls back to the role's home page. This is what makes `?returnUrl=` safe.
 */
export function resolveReturnUrl(returnUrl: string | null | undefined, role: Role): string {
  if (typeof returnUrl !== 'string' || !isInAppPath(returnUrl)) {
    return roleHome(role);
  }
  const path = returnUrl.split(/[?#]/, 1)[0];
  const area = ROLE_AREA[role];
  return path === area || path.startsWith(`${area}/`) ? returnUrl : roleHome(role);
}

function isInAppPath(url: string): boolean {
  // Must start with a single slash; no backslashes (browsers read `/\` as `//`); no control characters (browsers
  // strip tabs and newlines, so `/<tab>/evil.example` would otherwise become `//evil.example`).
  return url.startsWith('/') && !url.startsWith('//') && !url.includes('\\') && !/[\u0000-\u001f\u007f]/.test(url);
}
