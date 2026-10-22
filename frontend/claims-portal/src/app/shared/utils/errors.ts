import { AbstractControl, FormGroup } from '@angular/forms';

import { ApiError } from '@core/models/api-error';

/**
 * Every failed HTTP call arrives as an ApiError (the error interceptor converts it); this keeps the types honest for
 * the rare error that is not one, such as a bug in a `tap` callback.
 */
export function asApiError(error: unknown): ApiError {
  return error instanceof ApiError ? error : new ApiError(500, 'Something went wrong. Please try again.');
}

/**
 * The message a form field should show, or '' when it is valid. Any error whose value is a string is taken to be
 * the message itself (server-side field errors, custom validators); the built-in Angular validators get their
 * wording here so every form speaks the same way.
 */
export function fieldError(control: AbstractControl | null): string {
  const errors = control?.errors;
  if (!errors) {
    return '';
  }
  for (const value of Object.values(errors)) {
    if (typeof value === 'string') {
      return value;
    }
  }
  if (errors['required']) {
    return 'This field is required';
  }
  if (errors['email']) {
    return 'Enter a valid email address';
  }
  if (errors['minlength']) {
    return `Use at least ${errors['minlength'].requiredLength} characters`;
  }
  if (errors['maxlength']) {
    return `Use at most ${errors['maxlength'].requiredLength} characters`;
  }
  if (errors['min']) {
    return `Must be at least ${errors['min'].min}`;
  }
  if (errors['max']) {
    return `Must be at most ${errors['max'].max}`;
  }
  if (errors['matDatepickerParse'] || errors['matDatepickerMax'] || errors['matDatepickerMin']) {
    return 'Enter a valid date';
  }
  return 'This value is not valid';
}

/**
 * Puts the backend's validation messages (`fieldErrors`, keyed by DTO field) onto the form controls of the same
 * name, so they show under the right field. Returns the message to show above the form instead when none of the
 * fields could be matched (a failure that is not about a single field).
 */
export function applyServerErrors(form: FormGroup, error: ApiError): string | null {
  let matched = false;
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ server: message });
      control.markAsTouched();
      matched = true;
    }
  }
  return matched ? null : error.message;
}
