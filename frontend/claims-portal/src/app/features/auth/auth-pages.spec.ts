import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { environment } from '@env/environment';
import { Notify } from '@core/services/notify';
import { authResponseFor, fixtureUser, provideHttpTesting } from '@testing/auth-testing';
import { click, root, settle, texts, type } from '@testing/dom-testing';
import { Login } from './login/login';
import { Register } from './register/register';

describe('auth pages', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Notify), 'success').mockImplementation(() => undefined);
    vi.spyOn(TestBed.inject(Notify), 'info').mockImplementation(() => undefined);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  describe('Login', () => {
    async function render(returnUrl?: string): Promise<ComponentFixture<Login>> {
      const fixture = TestBed.createComponent(Login);
      if (returnUrl !== undefined) {
        fixture.componentRef.setInput('returnUrl', returnUrl);
      }
      await settle(fixture);
      return fixture;
    }

    function fill(fixture: ComponentFixture<Login>, email: string, password: string): void {
      type(fixture, 'input[type="email"]', email);
      type(fixture, 'input[formControlName="password"]', password);
    }

    it('has one h1 and links to registration', async () => {
      const fixture = await render();

      expect(texts(fixture, 'h1')).toEqual(['Sign in']);
      expect(root(fixture).querySelector('a[href="/auth/register"]')).not.toBeNull();
    });

    it('does not call the server, and says what is missing, when the form is empty', async () => {
      const fixture = await render();

      click(fixture, 'button[type="submit"]');
      await settle(fixture);

      http.expectNone('/api/auth/login');
      expect(texts(fixture, 'mat-error')).toEqual(['This field is required', 'This field is required']);
    });

    it('signs in and goes to the role home page', async () => {
      const fixture = await render();
      fill(fixture, '  officer@chubb.com ', 'Officer@123');

      click(fixture, 'button[type="submit"]');
      const request = http.expectOne('/api/auth/login');
      expect(request.request.body).toEqual({ email: 'officer@chubb.com', password: 'Officer@123' });
      request.flush(authResponseFor(fixtureUser('OFFICER', { id: 2 })));

      expect(navigate).toHaveBeenCalledWith('/officer/work-queue');
    });

    it('goes back to the page the visitor asked for when their role may open it', async () => {
      const fixture = await render('/claimant/claims/CLM-2026-000042');
      fill(fixture, 'weiling.tan@example.com', 'Claimant@123');

      click(fixture, 'button[type="submit"]');
      http.expectOne('/api/auth/login').flush(authResponseFor(fixtureUser('CLAIMANT')));

      expect(navigate).toHaveBeenCalledWith('/claimant/claims/CLM-2026-000042');
    });

    it("ignores a returnUrl into another role's area", async () => {
      const fixture = await render('/admin/dashboard');
      fill(fixture, 'weiling.tan@example.com', 'Claimant@123');

      click(fixture, 'button[type="submit"]');
      http.expectOne('/api/auth/login').flush(authResponseFor(fixtureUser('CLAIMANT')));

      expect(navigate).toHaveBeenCalledWith('/claimant/my-claims');
    });

    it('says the credentials are wrong, without saying which one, and lets the user try again', async () => {
      const fixture = await render();
      fill(fixture, 'weiling.tan@example.com', 'wrong-password1');

      click(fixture, 'button[type="submit"]');
      http
        .expectOne('/api/auth/login')
        .flush({ message: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
      await settle(fixture);

      expect(texts(fixture, '[role="alert"]')).toEqual(['Incorrect email or password.']);
      expect(root(fixture).querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('offers quick-fill demo accounts in development builds only', async () => {
      const fixture = await render();

      const demo = texts(fixture, '.demo__buttons button');
      expect(demo).toEqual(environment.showDemoLogins ? ['Claimant', 'Officer', 'Admin'] : []);

      if (environment.showDemoLogins) {
        click(fixture, '.demo__buttons button:nth-child(2)');
        await settle(fixture);
        expect(root(fixture).querySelector<HTMLInputElement>('input[type="email"]')!.value).toBe('officer@chubb.com');
      }
    });
  });

  describe('Register', () => {
    async function render(): Promise<ComponentFixture<Register>> {
      const fixture = TestBed.createComponent(Register);
      await settle(fixture);
      return fixture;
    }

    function fill(fixture: ComponentFixture<Register>, password: string, confirm: string): void {
      type(fixture, 'input[formControlName="fullName"]', 'Wei Ling Tan');
      type(fixture, 'input[type="email"]', 'weiling.tan@example.com');
      type(fixture, 'input[formControlName="password"]', password);
      type(fixture, 'input[formControlName="confirmPassword"]', confirm);
    }

    it('rejects a password with no digit and one that does not match, before asking the server', async () => {
      const fixture = await render();

      fill(fixture, 'lettersonly', 'something-else');
      click(fixture, 'button[type="submit"]');
      await settle(fixture);

      http.expectNone('/api/auth/register');
      expect(texts(fixture, 'mat-error')).toEqual([
        'Use at least one letter and one digit',
        'The passwords do not match',
      ]);
    });

    it('registers, signs in straight away and lands on the claimant home page', async () => {
      const fixture = await render();
      fill(fixture, 'Secret#123', 'Secret#123');

      click(fixture, 'button[type="submit"]');
      const register = http.expectOne('/api/auth/register');
      expect(register.request.body).toEqual({
        fullName: 'Wei Ling Tan',
        email: 'weiling.tan@example.com',
        password: 'Secret#123',
      });
      register.flush(fixtureUser('CLAIMANT'), { status: 201, statusText: 'Created' });

      http.expectOne('/api/auth/login').flush(authResponseFor(fixtureUser('CLAIMANT')));

      expect(navigate).toHaveBeenCalledWith('/claimant/my-claims');
    });

    it('shows "email already registered" under the email field', async () => {
      const fixture = await render();
      fill(fixture, 'Secret#123', 'Secret#123');

      click(fixture, 'button[type="submit"]');
      http
        .expectOne('/api/auth/register')
        .flush({ message: 'An account with this email already exists' }, { status: 409, statusText: 'Conflict' });
      await settle(fixture);

      expect(texts(fixture, 'mat-error')).toEqual(['An account with this email already exists']);
      http.expectNone('/api/auth/login');
    });

    it('puts the backend field errors under the right fields', async () => {
      const fixture = await render();
      fill(fixture, 'Secret#123', 'Secret#123');

      click(fixture, 'button[type="submit"]');
      http
        .expectOne('/api/auth/register')
        .flush(
          { message: 'Validation failed', fieldErrors: { fullName: 'must not be blank' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await settle(fixture);

      expect(texts(fixture, 'mat-error')).toEqual(['must not be blank']);
      expect(root(fixture).querySelector('[role="alert"]')).toBeNull();
    });
  });
});
