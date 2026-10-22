import { HttpContextToken } from '@angular/common/http';

/**
 * Whether a 401 on this request means "the session is no longer valid" and should sign the user out. On by default.
 * Switch it off for a call whose 401 is handled by its caller (confirming a stored token at start-up).
 */
export const HANDLE_UNAUTHORIZED = new HttpContextToken<boolean>(() => true);
