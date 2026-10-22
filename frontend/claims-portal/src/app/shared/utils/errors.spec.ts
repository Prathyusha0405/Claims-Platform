import { FormControl, FormGroup, Validators } from '@angular/forms';

import { ApiError } from '@core/models/api-error';
import { applyServerErrors, asApiError, fieldError } from './errors';

describe('fieldError', () => {
  it('is empty for a valid or missing control', () => {
    expect(fieldError(null)).toBe('');
    expect(fieldError(new FormControl('fine', Validators.required))).toBe('');
  });

  it.each([
    ['required', new FormControl('', Validators.required), 'This field is required'],
    ['email', new FormControl('nope', Validators.email), 'Enter a valid email address'],
    ['minlength', new FormControl('abc', Validators.minLength(8)), 'Use at least 8 characters'],
    ['maxlength', new FormControl('abcdef', Validators.maxLength(3)), 'Use at most 3 characters'],
    ['min', new FormControl(0, Validators.min(0.01)), 'Must be at least 0.01'],
    ['max', new FormControl(11, Validators.max(10)), 'Must be at most 10'],
  ])('words the built-in %s error', (_name, control, message) => {
    expect(fieldError(control)).toBe(message);
  });

  it('shows a string error as the message itself, so a server or custom message wins', () => {
    const control = new FormControl('', Validators.required);
    control.setErrors({ required: true, server: 'Already taken' });

    expect(fieldError(control)).toBe('Already taken');
  });
});

describe('applyServerErrors', () => {
  const form = () => new FormGroup({ email: new FormControl(''), password: new FormControl('') });

  it('puts each backend field message on the control of the same name and leaves no form-level message', () => {
    const group = form();
    const error = new ApiError(400, 'Validation failed', '/x', { email: 'must be a well-formed email address' });

    expect(applyServerErrors(group, error)).toBeNull();
    expect(fieldError(group.controls.email)).toBe('must be a well-formed email address');
    expect(group.controls.email.touched).toBe(true);
    expect(group.controls.password.errors).toBeNull();
  });

  it('hands back the message for the page to show when no field could be matched', () => {
    const error = new ApiError(400, 'Something is off', '/x', { unknownField: 'bad' });

    expect(applyServerErrors(form(), error)).toBe('Something is off');
    expect(applyServerErrors(form(), new ApiError(500, 'Boom'))).toBe('Boom');
  });
});

describe('asApiError', () => {
  it('passes an ApiError through and wraps anything else in a safe generic one', () => {
    const real = new ApiError(404, 'No such claim');

    expect(asApiError(real)).toBe(real);
    expect(asApiError(new TypeError('x is undefined')).message).toBe('Something went wrong. Please try again.');
  });
});
