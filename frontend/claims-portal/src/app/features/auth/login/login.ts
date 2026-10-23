import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { environment } from '@env/environment';
import { AuthStore } from '@core/services/auth-store';
import { resolveReturnUrl } from '@core/services/role-home';
import { asApiError, fieldError } from '@shared/utils/errors';

/** The accounts the backend seeds for demos. Shown as quick-fill buttons in development builds only. */
const DEMO_ACCOUNTS = [
  { label: 'Claimant', email: 'weiling.tan@example.com', password: 'Claimant@123' },
  { label: 'Claims staff', email: 'staff@chubb.com', password: 'Staff@123' },
  { label: 'Manager', email: 'manager@chubb.com', password: 'Manager@123' },
] as const;

type DemoAccount = (typeof DEMO_ACCOUNTS)[number];

/** Sign-in. On success the user goes to the page they asked for (`?returnUrl=`) if their role may open it, else home. */
@Component({
  imports: [MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './login.scss',
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Bound from the `returnUrl` query parameter (the router has component input binding switched on). */
  readonly returnUrl = input<string>();

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly passwordVisible = signal(false);
  protected readonly demoAccounts: readonly DemoAccount[] = environment.showDemoLogins ? DEMO_ACCOUNTS : [];
  protected readonly error = fieldError;

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
    const { email, password } = this.form.getRawValue();
    this.auth.login({ email: email.trim(), password }).subscribe({
      next: (user) => void this.router.navigateByUrl(resolveReturnUrl(this.returnUrl(), user.role)),
      error: (failure: unknown) => {
        const error = asApiError(failure);
        this.failure.set(error.isUnauthorized ? 'Incorrect email or password.' : error.message);
        this.submitting.set(false);
      },
    });
  }

  protected useDemo(account: DemoAccount): void {
    this.form.setValue({ email: account.email, password: account.password });
    this.failure.set(null);
  }
}
