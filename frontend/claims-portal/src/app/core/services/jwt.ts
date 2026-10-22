import { isRole, Role } from '@core/models/role';

/** The claims auth-service puts in every access token. */
export interface JwtPayload {
  /** The user id, as a string. */
  sub: string;
  email: string;
  name: string;
  role: Role;
  /** Issued at / expires at, in epoch SECONDS. */
  iat: number;
  exp: number;
}

/**
 * Reads the payload of a JWT. This only DECODES: it does not (and cannot) verify the signature. That is fine because
 * the browser never trusts the token for security, only for display and routing; every backend call re-validates it.
 * Returns null for anything that is not a well-formed token with the claims this app needs.
 */
export function decodeJwt(token: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }

  try {
    const payload: unknown = JSON.parse(decodeBase64Url(parts[1]));
    return isJwtPayload(payload) ? payload : null;
  } catch {
    return null;
  }
}

/** base64url → UTF-8 text (names can contain any script, so `atob` alone would garble them). */
function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const binary = atob(padded);
  return new TextDecoder().decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)));
}

function isJwtPayload(value: unknown): value is JwtPayload {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const claims = value as Record<string, unknown>;
  return (
    typeof claims['sub'] === 'string' &&
    claims['sub'] !== '' &&
    typeof claims['email'] === 'string' &&
    typeof claims['name'] === 'string' &&
    isRole(claims['role']) &&
    typeof claims['iat'] === 'number' &&
    typeof claims['exp'] === 'number'
  );
}
