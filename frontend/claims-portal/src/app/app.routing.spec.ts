import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Role } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { routes } from './app.routes';

/** The whole route table, guards and layouts working together, with sessions signed in through the real AuthStore. */
describe('routing through the real router', () => {
  // Angular allows one harness per test; a test that visits several URLs navigates the same harness.
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), ...provideHttpTesting()] });
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    TestBed.inject(AuthStore).discard();
    vi.useRealTimers();
  });

  const root = (): HTMLElement => harness.fixture.nativeElement as HTMLElement;
  const loginUrl = (returnUrl: string) => `/auth/login?returnUrl=${encodeURIComponent(returnUrl)}`;

  async function open(url: string): Promise<{ url: string; text: string; heading: string; inShell: boolean }> {
    await harness.navigateByUrl(url);
    return {
      url: router.url,
      text: root().textContent ?? '',
      // Every real page has exactly one <h1>; checking it proves the right page rendered, not just that its words
      // appear somewhere (the side navigation also says "My claims").
      heading: root().querySelector('h1')?.textContent?.trim() ?? '',
      inShell: root().querySelector('.shell') !== null,
    };
  }

  describe('a visitor who is not signed in', () => {
    it('is sent from / to the login page', async () => {
      const page = await open('/');

      expect(page.url).toBe('/auth/login');
      expect(page.heading).toBe('Sign in');
      expect(page.inShell).toBe(false);
    });

    it('can use the public pages, inside the public layout', async () => {
      expect((await open('/auth/login')).heading).toBe('Sign in');

      const register = await open('/auth/register');
      expect(register.url).toBe('/auth/register');
      expect(register.heading).toBe('Create an account');
      expect(root().querySelector('.public')).not.toBeNull();

      expect((await open('/auth')).url).toBe('/auth/login'); // area index redirect
    });

    it.each([
      '/claimant',
      '/claimant/my-claims',
      '/claimant/claims/new',
      '/claimant/claims/CLM-2026-000001',
      '/claimant/notifications',
      '/officer/work-queue',
      '/officer/claims/CLM-2026-000001',
      '/officer/notifications',
      '/admin/dashboard',
      '/forbidden',
    ])('is sent from %s to login, which will bring them back', async (url) => {
      const page = await open(url);

      expect(page.url).toBe(loginUrl(url));
      expect(page.heading).toBe('Sign in');
      expect(page.inShell).toBe(false);
    });

    it('keeps the query string in the page they will be brought back to', async () => {
      const url = '/claimant/claims/CLM-2026-000001?tab=history';

      expect((await open(url)).url).toBe(loginUrl(url));
    });

    it('sees the not-found page for an unknown URL, without needing to sign in', async () => {
      const page = await open('/definitely/not/a/page');

      expect(page.url).toBe('/definitely/not/a/page');
      expect(page.text).toContain('Page not found');
      expect(page.text).toContain('Go to sign in');
    });
  });

  describe.each([
    ['CLAIMANT', '/claimant/my-claims', 'My claims', ['/officer/work-queue', '/admin/dashboard']],
    ['OFFICER', '/officer/work-queue', 'Work queue', ['/claimant/my-claims', '/admin/dashboard']],
    ['ADMIN', '/admin/dashboard', 'Dashboard', ['/claimant/my-claims', '/officer/work-queue']],
  ] as [Role, string, string, string[]][])('a signed-in %s', (role, home, homeHeading, otherAreas) => {
    beforeEach(() => signInAs(role));

    it('lands on their own home page from /, inside the shell', async () => {
      const page = await open('/');

      expect(page.url).toBe(home);
      expect(page.heading).toBe(homeHeading);
      expect(page.inShell).toBe(true);
    });

    it('is moved on from the login and register pages', async () => {
      expect((await open('/auth/login')).url).toBe(home);
      expect((await open('/auth/register')).url).toBe(home);
    });

    it.each([0, 1])('is told access is denied in the other areas (#%i), without leaving the shell', async (index) => {
      const page = await open(otherAreas[index]);

      expect(page.url).toBe('/forbidden');
      expect(page.text).toContain('Access denied');
      expect(page.inShell).toBe(true);
    });

    it('highlights the current page in the navigation', async () => {
      await open(home);

      // RouterLinkActive updates a microtask after the navigation ends, so give the view a moment to catch up.
      await vi.waitFor(() => {
        harness.detectChanges();
        expect(root().querySelector('a[aria-current="page"]')?.getAttribute('href')).toBe(home);
      });
    });

    it('sees the not-found page, outside the shell, for an unknown URL', async () => {
      const page = await open('/definitely/not/a/page');

      expect(page.text).toContain('Page not found');
      expect(page.text).toContain('Back to my home page');
      expect(page.inShell).toBe(false);
    });

    it('is signed out for good by signing out: the login page, and every area closed again', async () => {
      await open(home);

      TestBed.inject(AuthStore).signOut('user');
      await vi.waitFor(() => expect(router.url).toBe('/auth/login'));

      expect((await open(home)).url).toBe(loginUrl(home));
    });
  });

  describe('signed-in extras', () => {
    it('is taken to the page a login link asked for, when their role may open it', async () => {
      signInAs('CLAIMANT');

      expect((await open('/auth/login?returnUrl=/claimant/notifications')).url).toBe('/claimant/notifications');
    });

    it("ignores a login link that asks for another role's page or another site", async () => {
      signInAs('CLAIMANT');

      expect((await open('/auth/login?returnUrl=/admin/dashboard')).url).toBe('/claimant/my-claims');
      expect((await open('/auth/login?returnUrl=https://evil.example')).url).toBe('/claimant/my-claims');
    });

    it('opens the area root on its first page', async () => {
      signInAs('OFFICER');

      expect((await open('/officer')).url).toBe('/officer/work-queue');
    });

    it('is treated as signed out once the session has expired', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      signInAs('CLAIMANT', {}, 3600);
      vi.setSystemTime(Date.now() + 3601 * 1000);

      expect((await open('/claimant/my-claims')).url).toBe(loginUrl('/claimant/my-claims'));
      expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(false);
    });
  });
});
