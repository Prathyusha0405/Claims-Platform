import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { ApiError } from '@core/models/api-error';
import { loader } from './loader';

describe('loader', () => {
  /** Every fetch gets its own Subject, so a test decides when (and whether) each reply arrives. */
  function setup() {
    const request = signal('a');
    const replies = new Map<string, Subject<string>>();
    const fetch = vi.fn((key: string) => {
      const reply = new Subject<string>();
      replies.set(key, reply);
      return reply;
    });
    const result = TestBed.runInInjectionContext(() => loader(request, fetch));
    TestBed.tick();
    return { request, replies, fetch, result };
  }

  it('starts out loading, fetches the first request and shows its result', () => {
    const { replies, fetch, result } = setup();

    expect(fetch).toHaveBeenCalledWith('a');
    expect(result.loading()).toBe(true);
    expect(result.value()).toBeNull();

    replies.get('a')!.next('first');

    expect(result.value()).toBe('first');
    expect(result.loading()).toBe(false);
    expect(result.error()).toBeNull();
  });

  it('keeps the old value on screen while the next request loads', () => {
    const { request, replies, result } = setup();
    replies.get('a')!.next('first');

    request.set('b');
    TestBed.tick();

    expect(result.loading()).toBe(true);
    expect(result.value()).toBe('first');

    replies.get('b')!.next('second');
    expect(result.value()).toBe('second');
    expect(result.loading()).toBe(false);
  });

  it('ignores a slow reply to a request that has been replaced', () => {
    const { request, replies, result } = setup();

    request.set('b');
    TestBed.tick();
    replies.get('b')!.next('second');
    replies.get('a')!.next('late reply to the old request');

    expect(result.value()).toBe('second');
  });

  it('reports a failure, keeps the last good value, and clears the error when the next fetch starts', () => {
    const { request, replies, result } = setup();
    replies.get('a')!.next('first');

    request.set('b');
    TestBed.tick();
    replies.get('b')!.error(new ApiError(503, 'Service unavailable'));

    expect(result.error()?.status).toBe(503);
    expect(result.loading()).toBe(false);
    expect(result.value()).toBe('first');

    request.set('c');
    TestBed.tick();

    expect(result.error()).toBeNull();
    expect(result.loading()).toBe(true);
  });

  it('keeps working after a failure', () => {
    const { request, replies, result } = setup();
    replies.get('a')!.error(new ApiError(500, 'Boom'));

    request.set('b');
    TestBed.tick();
    replies.get('b')!.next('recovered');

    expect(result.value()).toBe('recovered');
  });
});
