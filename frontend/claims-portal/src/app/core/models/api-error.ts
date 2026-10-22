import { HttpErrorResponse } from '@angular/common/http';

/** The JSON every backend service returns for a failed request. */
interface ErrorBody {
  message?: unknown;
  path?: unknown;
  fieldErrors?: unknown;
}

const NETWORK_MESSAGE = 'Cannot reach the server. Check your connection and try again.';
const UNAVAILABLE_MESSAGE = 'The service is temporarily unavailable. Please try again in a moment.';
const GENERIC_MESSAGE = 'Something went wrong. Please try again.';

/**
 * What the app throws for any failed HTTP call, so components never touch `HttpErrorResponse`. `message` is safe to
 * show to the user; `fieldErrors` maps backend validation failures to form control names (the DTO field names).
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly path: string = '',
    readonly fieldErrors: Readonly<Record<string, string>> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** The request never got an answer: offline, DNS, CORS, or the dev-server proxy has no backend behind it. */
  get isNetwork(): boolean {
    return this.status === 0;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }

  /** A service we depend on is down (assessment-service answers 503 when claim-service is unreachable). */
  get isUnavailable(): boolean {
    return this.status === 502 || this.status === 503 || this.status === 504;
  }

  get hasFieldErrors(): boolean {
    return Object.keys(this.fieldErrors).length > 0;
  }

  static from(response: HttpErrorResponse): ApiError {
    const body: ErrorBody = isRecord(response.error) ? response.error : {};
    const message = typeof body.message === 'string' && body.message.trim() ? body.message : defaultMessage(response.status);
    const path = typeof body.path === 'string' ? body.path : '';
    return new ApiError(response.status, message, path, toFieldErrors(body.fieldErrors));
  }
}

function defaultMessage(status: number): string {
  if (status === 0) {
    return NETWORK_MESSAGE;
  }
  if (status === 502 || status === 503 || status === 504) {
    return UNAVAILABLE_MESSAGE;
  }
  return GENERIC_MESSAGE;
}

function toFieldErrors(value: unknown): Record<string, string> {
  if (!isRecord(value)) {
    return {};
  }
  return Object.fromEntries(Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
