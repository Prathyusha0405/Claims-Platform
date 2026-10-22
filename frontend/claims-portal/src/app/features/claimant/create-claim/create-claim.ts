import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';

import { environment } from '@env/environment';
import { ClaimApi } from '@core/services/claim-api';
import { Notify } from '@core/services/notify';
import { CLAIM_TYPE_LABELS, CLAIM_TYPES, ClaimType } from '@shared/models/claim';
import { startOfToday, toIsoDate } from '@shared/utils/dates';
import { applyServerErrors, asApiError, fieldError } from '@shared/utils/errors';

/** claim-service's rule for a policy number: 4–30 characters, letters, digits, '-' or '/', starting with a letter or digit. */
const policyNumberFormat: ValidatorFn = (control) =>
  !control.value || /^[A-Za-z0-9][A-Za-z0-9/-]{3,29}$/.test(control.value.trim())
    ? null
    : { format: "Use 4 to 30 characters: letters, digits, '-' or '/'" };

/** Money has cents and no more. Compared in whole cents so 0.1 + 0.2 style float noise cannot fail a valid amount. */
const atMostTwoDecimals: ValidatorFn = (control) =>
  control.value === null || Math.abs(control.value * 100 - Math.round(control.value * 100)) < 1e-6
    ? null
    : { decimals: 'Use at most 2 decimal places' };

const notBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.length > 0 && control.value.trim() === '' ? { blank: 'This field is required' } : null;

/** The "file a claim" form. A new claim starts as SUBMITTED, and the user lands on its tracking page. */
@Component({
  imports: [
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  providers: [provideNativeDateAdapter()],
  selector: 'app-create-claim',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './create-claim.scss',
  templateUrl: './create-claim.html',
})
export class CreateClaim {
  private readonly api = inject(ClaimApi);
  private readonly notify = inject(Notify);
  private readonly router = inject(Router);

  protected readonly form = new FormGroup({
    policyNumber: new FormControl('', { nonNullable: true, validators: [Validators.required, policyNumberFormat] }),
    claimType: new FormControl<ClaimType | null>(null, { validators: [Validators.required] }),
    incidentDate: new FormControl<Date | null>(null, { validators: [Validators.required] }),
    incidentLocation: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank, Validators.maxLength(2000)],
    }),
    claimAmount: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.01), Validators.max(9_999_999_999_999), atMostTwoDecimals],
    }),
  });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly claimTypes = CLAIM_TYPES;
  protected readonly claimTypeLabels = CLAIM_TYPE_LABELS;
  protected readonly currency = environment.reportingCurrency;
  /** An incident cannot be in the future; the backend rejects it too. */
  protected readonly today = startOfToday();
  protected readonly error = (control: AbstractControl | null): string => fieldError(control);

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const location = value.incidentLocation.trim();
    this.submitting.set(true);
    this.failure.set(null);
    this.api
      .create({
        policyNumber: value.policyNumber.trim(),
        // The validators above guarantee these three are set.
        claimType: value.claimType!,
        incidentDate: toIsoDate(value.incidentDate!),
        ...(location ? { incidentLocation: location } : {}),
        description: value.description.trim(),
        claimAmount: value.claimAmount!,
      })
      .subscribe({
        next: (claim) => {
          this.notify.success(`Claim ${claim.claimNumber} submitted.`);
          void this.router.navigate(['/claimant/claims', claim.claimNumber]);
        },
        error: (failure: unknown) => {
          this.failure.set(applyServerErrors(this.form, asApiError(failure)));
          this.submitting.set(false);
        },
      });
  }
}
