import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from './api-error';

const failure = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error, url: '/api/x' });

describe('ApiError.from', () => {
  it('uses the message, path and field errors of the backend error JSON', () => {
    const error = ApiError.from(
      failure(400, {
        timestamp: '2026-10-22T06:00:00Z',
        status: 400,
        error: 'Bad Request',
        message: 'Validation failed',
        path: '/api/claims',
        fieldErrors: { policyNumber: 'must match "^[A-Za-z0-9]..."', claimAmount: 'must be greater than 0' },
      }),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toBeInstanceOf(Error);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Validation failed');
    expect(error.path).toBe('/api/claims');
    expect(error.fieldErrors).toEqual({
      policyNumber: 'must match "^[A-Za-z0-9]..."',
      claimAmount: 'must be greater than 0',
    });
    expect(error.hasFieldErrors).toBe(true);
  });

  it('has no field errors when the backend sent none', () => {
    const error = ApiError.from(failure(409, { message: 'Email already registered' }));

    expect(error.fieldErrors).toEqual({});
    expect(error.hasFieldErrors).toBe(false);
  });

  it('keeps only field errors whose message is text', () => {
    const error = ApiError.from(failure(400, { message: 'Validation failed', fieldErrors: { a: 'bad', b: 5, c: null } }));

    expect(error.fieldErrors).toEqual({ a: 'bad' });
  });

  it('explains a missing connection without jargon', () => {
    const error = ApiError.from(failure(0));

    expect(error.isNetwork).toBe(true);
    expect(error.message).toBe('Cannot reach the server. Check your connection and try again.');
  });

  it.each([502, 503, 504])('treats %i as "a service is unavailable", with or without a body', (status) => {
    const empty = ApiError.from(failure(status));
    const html = ApiError.from(failure(status, '<html>Bad gateway</html>'));

    expect(empty.isUnavailable).toBe(true);
    expect(empty.message).toBe('The service is temporarily unavailable. Please try again in a moment.');
    expect(html.message).toBe(empty.message);
  });

  it('prefers the backend message for an unavailable service when there is one', () => {
    expect(ApiError.from(failure(503, { message: 'Claim service is unavailable' })).message).toBe('Claim service is unavailable');
  });

  it('never shows raw server text that is not the documented JSON', () => {
    expect(ApiError.from(failure(500, 'java.lang.NullPointerException at ...')).message).toBe('Something went wrong. Please try again.');
    expect(ApiError.from(failure(500, ['x'])).message).toBe('Something went wrong. Please try again.');
    expect(ApiError.from(failure(500, { message: '   ' })).message).toBe('Something went wrong. Please try again.');
  });

  it('classifies the statuses the UI reacts to', () => {
    expect(ApiError.from(failure(401)).isUnauthorized).toBe(true);
    expect(ApiError.from(failure(403)).isForbidden).toBe(true);
    expect(ApiError.from(failure(404)).isNotFound).toBe(true);
    expect(ApiError.from(failure(409)).isConflict).toBe(true);

    const ordinary = ApiError.from(failure(400));
    expect([ordinary.isNetwork, ordinary.isUnauthorized, ordinary.isForbidden, ordinary.isNotFound, ordinary.isConflict, ordinary.isUnavailable])
      .toEqual([false, false, false, false, false, false]);
  });
});
