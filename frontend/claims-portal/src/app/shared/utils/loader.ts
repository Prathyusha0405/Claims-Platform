import { Signal, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, Observable, switchMap, tap } from 'rxjs';

import { ApiError } from '@core/models/api-error';
import { asApiError } from './errors';

/** What a page shows for data it fetches: the latest value, whether a fetch is running, and why the last one failed. */
export interface Loader<T> {
  /** The last successful result; kept while the next one loads, so a table does not blank out on every page turn. */
  readonly value: Signal<T | null>;
  readonly loading: Signal<boolean>;
  /** Set when the latest fetch failed; cleared as soon as the next one starts. */
  readonly error: Signal<ApiError | null>;
}

/**
 * Fetches whenever `request` changes. Changing the request while a fetch is running cancels the old one (so a slow
 * reply can never overwrite a newer one). To refetch the same thing, put a counter inside the request.
 *
 * Call it in an injection context, i.e. from a field initialiser or constructor.
 */
export function loader<R, T>(request: Signal<R>, fetch: (request: R) => Observable<T>): Loader<T> {
  const value = signal<T | null>(null);
  const loading = signal(true);
  const error = signal<ApiError | null>(null);

  toObservable(request)
    .pipe(
      tap(() => {
        loading.set(true);
        error.set(null);
      }),
      switchMap((current) =>
        fetch(current).pipe(
          tap((result) => {
            value.set(result);
            loading.set(false);
          }),
          catchError((failure: unknown) => {
            error.set(asApiError(failure));
            loading.set(false);
            return EMPTY;
          }),
        ),
      ),
      takeUntilDestroyed(),
    )
    .subscribe();

  return { value: value.asReadonly(), loading: loading.asReadonly(), error: error.asReadonly() };
}
