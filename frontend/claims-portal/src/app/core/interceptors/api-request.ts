import { PUBLIC_API_PATHS } from '@core/services/endpoints';

/** True for our own backend (same origin, under `/api/`). The bearer token is never sent anywhere else. */
export function isApiRequest(url: string): boolean {
  const target = resolve(url);
  return target !== null && target.origin === window.location.origin && target.pathname.startsWith('/api/');
}

/** True for the sign-in and registration calls: no token goes out, and a 401 there just means "wrong credentials". */
export function isPublicApiRequest(url: string): boolean {
  const target = resolve(url);
  return target !== null && PUBLIC_API_PATHS.includes(target.pathname);
}

function resolve(url: string): URL | null {
  try {
    return new URL(url, window.location.origin);
  } catch {
    return null;
  }
}
