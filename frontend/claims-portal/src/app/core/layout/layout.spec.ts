import { BreakpointObserver } from '@angular/cdk/layout';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Role } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { AppShell } from './app-shell/app-shell';
import { Forbidden } from './forbidden/forbidden';
import { NotFound } from './not-found/not-found';
import { PublicLayout } from './public-layout/public-layout';
import { UserMenu } from './user-menu/user-menu';

function configure(options: { compact?: boolean } = {}): void {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      ...provideHttpTesting(),
      { provide: BreakpointObserver, useValue: { observe: () => of({ matches: options.compact ?? false, breakpoints: {} }) } },
    ],
  });
}

async function render<T>(component: new () => T): Promise<ComponentFixture<T>> {
  const fixture = TestBed.createComponent(component);
  await fixture.whenStable();
  return fixture;
}

const text = (fixture: ComponentFixture<unknown>, selector: string): string[] =>
  Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(selector)).map((el) => (el.textContent ?? '').trim());

afterEach(() => TestBed.inject(AuthStore).discard());

describe('AppShell', () => {
  it.each([
    ['CLAIMANT', ['My claims', 'New claim', 'Notifications'], '/claimant/my-claims'],
    ['CLAIMS_STAFF', ['Work queue', 'Notifications'], '/staff/work-queue'],
    ['MANAGER', ['Dashboard'], '/manager/dashboard'],
  ] as const)('shows a %s only their own navigation', async (role, labels, home) => {
    configure();
    signInAs(role);

    const fixture = await render(AppShell);

    expect(text(fixture, '.mdc-list-item__primary-text')).toEqual(labels);
    expect(fixture.nativeElement.querySelector('.shell__brand').getAttribute('href')).toBe(home);
  });

  it('links each entry to its page', async () => {
    configure();
    signInAs('CLAIMANT');

    const fixture = await render(AppShell);

    const hrefs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a[mat-list-item]')).map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/claimant/my-claims', '/claimant/claims/new', '/claimant/notifications']);
  });

  it('shows who is signed in', async () => {
    configure();
    signInAs('CLAIMS_STAFF', { fullName: 'Rajesh Kumar' });

    const fixture = await render(AppShell);

    expect(text(fixture, '.user-menu__name')).toEqual(['Rajesh Kumar']);
  });

  it('keeps the navigation beside the page on a wide screen, with no menu button', async () => {
    configure({ compact: false });
    signInAs('CLAIMANT');

    const fixture = await render(AppShell);

    expect(fixture.nativeElement.querySelector('button[aria-label="Toggle navigation menu"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('mat-sidenav').classList).toContain('mat-drawer-side');
  });

  it('turns the navigation into a drawer opened from a toolbar button on a narrow screen', async () => {
    configure({ compact: true });
    signInAs('CLAIMANT');

    const fixture = await render(AppShell);
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Toggle navigation menu"]');

    expect(button).not.toBeNull();
    expect(fixture.nativeElement.querySelector('mat-sidenav').classList).toContain('mat-drawer-over');
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(button.getAttribute('aria-expanded')).toBe('true');
  });

  it('moves focus to the page when the skip link is used, without navigating', async () => {
    configure();
    signInAs('CLAIMANT');
    const fixture = await render(AppShell);

    (fixture.nativeElement.querySelector('.skip-link') as HTMLAnchorElement).click();

    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#main-content'));
  });

  it('renders the page inside a <main> landmark', async () => {
    configure();
    signInAs('CLAIMANT');

    const fixture = await render(AppShell);

    expect(fixture.nativeElement.querySelectorAll('main').length).toBe(1);
    expect(fixture.nativeElement.querySelector('main router-outlet')).not.toBeNull();
  });
});

describe('UserMenu', () => {
  it('shows nothing while signed out', async () => {
    configure();

    const fixture = await render(UserMenu);

    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('opens a menu with the name, email and role, and signs out from it', async () => {
    configure();
    signInAs('CLAIMS_STAFF', { fullName: 'Rajesh Kumar', email: 'rajesh.kumar@chubb.com' });
    const signOut = vi.spyOn(TestBed.inject(AuthStore), 'signOut').mockImplementation(() => undefined);
    const fixture = await render(UserMenu);
    const menu = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatMenuHarness);

    await menu.open();

    const summary = document.querySelector('.user-menu__summary')?.textContent ?? '';
    expect(summary).toContain('Rajesh Kumar');
    expect(summary).toContain('rajesh.kumar@chubb.com');
    expect(summary).toContain('Claims staff');

    const items = await menu.getItems();
    expect(items).toHaveLength(1);
    expect(await items[0].getText()).toContain('Sign out');
    await items[0].click();

    expect(signOut).toHaveBeenCalledWith('user');
  });
});

describe('Forbidden', () => {
  it.each([
    ['CLAIMANT', 'Claimant', '/claimant/my-claims'],
    ['MANAGER', 'Manager', '/manager/dashboard'],
  ] as const)('tells a signed-in %s what they are and links to their home page', async (role: Role, label, home) => {
    configure();
    signInAs(role);

    const fixture = await render(Forbidden);

    expect(text(fixture, 'h1')).toEqual(['Access denied']);
    expect(text(fixture, '.notice__text')[0]).toContain(label);
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe(home);
  });
});

describe('NotFound', () => {
  it('offers a visitor the sign-in page', async () => {
    configure();

    const fixture = await render(NotFound);

    expect(text(fixture, 'h1')).toEqual(['Page not found']);
    expect(text(fixture, 'a')).toEqual(['Go to sign in']);
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/auth/login');
  });

  it('offers a signed-in user their home page', async () => {
    configure();
    signInAs('CLAIMS_STAFF');

    const fixture = await render(NotFound);

    expect(text(fixture, 'a')).toEqual(['Back to my home page']);
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/staff/work-queue');
  });
});

describe('PublicLayout', () => {
  it('frames the page with the brand and a <main> landmark', async () => {
    configure();

    const fixture = await render(PublicLayout);

    expect(text(fixture, '.public__name')[0]).toContain('Chubb Claims');
    expect(fixture.nativeElement.querySelector('main router-outlet')).not.toBeNull();
  });
});
