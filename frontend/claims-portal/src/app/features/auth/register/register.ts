import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { catchError, EMPTY, switchMap } from 'rxjs';

import { AuthApi } from '@core/services/auth-api';
import { AuthStore } from '@core/services/auth-store';
import { Notify } from '@core/services/notify';
import { resolveReturnUrl } from '@core/services/role-home';
import { applyServerErrors, asApiError, fieldError } from '@shared/utils/errors';

/** auth-service's password rule: 8–72 characters with at least one letter and one digit. */
const letterAndDigit: ValidatorFn = (control) =>
  !control.value || (/\p{L}/u.test(control.value) && /\p{Nd}/u.test(control.value))
    ? null
    : { weak: 'Use at least one letter and one digit' };

const matchesPassword: ValidatorFn = (control) =>
  control.value === control.parent?.get('password')?.value ? null : { mismatch: 'The passwords do not match' };

/** Registration creates a claimant account and signs the new user straight in. */
@Component({
  imports: [MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, ReactiveFormsModule, RouterLink],
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register.scss',
  templateUrl: './register.html',
})
export class Register {
  private readonly authApi = inject(AuthApi);
  private readonly auth = inject(AuthStore);
  private readonly notify = inject(Notify);
  private readonly router = inject(Router);

  /** Bound from the `returnUrl` query parameter, carried over from the sign-in page. */
  readonly returnUrl = input<string>();

  protected readonly form = new FormGroup({
    fullName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(8), Validators.maxLength(72), letterAndDigit],
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, matchesPassword] }),
  });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly passwordVisible = signal(false);
  protected readonly error = (control: AbstractControl | null): string => fieldError(control);

  constructor() {
    // Changing the password after typing the confirmation must re-check the confirmation.
    this.form.controls.password.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity());
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.failure.set(null);
    const { fullName, email, password } = this.form.getRawValue();
    const request = { fullName: fullName.trim(), email: email.trim(), password };

    this.authApi
      .register(request)
      .pipe(
        switchMap(() =>
          this.auth.login({ email: request.email, password }).pipe(
            // The account exists; only the automatic sign-in failed, so send them to do it by hand.
            catchError(() => {
              this.notify.info('Your account is ready. Please sign in.');
              void this.router.navigate(['/auth/login']);
              return EMPTY;
            }),
          ),
        ),
      )
      .subscribe({
        next: (user) => {
          this.notify.success(`Welcome, ${user.fullName}.`);
          void this.router.navigateByUrl(resolveReturnUrl(this.returnUrl(), user.role));
        },
        error: (failure: unknown) => this.fail(asApiError(failure)),
      });
  }

  private fail(error: ReturnType<typeof asApiError>): void {
    if (error.isConflict && !error.hasFieldErrors) {
      this.form.controls.email.setErrors({ server: error.message });
      this.form.controls.email.markAsTouched();
    } else {
      this.failure.set(applyServerErrors(this.form, error));
    }
    this.submitting.set(false);
  }
}
