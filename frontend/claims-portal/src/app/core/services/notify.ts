import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

const SHORT_MS = 4000;
const LONG_MS = 8000;

/** The one place the app shows a transient message (a Material snack bar), so wording and timing stay consistent. */
@Injectable({ providedIn: 'root' })
export class Notify {
  private readonly snackBar = inject(MatSnackBar);

  /** Something worked or changed; fades away by itself. */
  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SHORT_MS, politeness: 'polite' });
  }

  /** Neutral news, e.g. "You have been signed out". */
  info(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SHORT_MS, politeness: 'polite' });
  }

  /** Something failed; stays longer and is announced to screen readers immediately. */
  error(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: LONG_MS, politeness: 'assertive' });
  }
}
