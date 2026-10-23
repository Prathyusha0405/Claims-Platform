import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { NotificationBell } from './notification-bell';

const UNREAD_COUNT = '/api/notifications/unread-count';

describe('NotificationBell', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<NotificationBell>;

  /** Signs `role` in and shows the bell; the first count is requested on the next (fake) tick. */
  const showBellFor = async (role: Role): Promise<void> => {
    signInAs(role);
    fixture = TestBed.createComponent(NotificationBell);
    await vi.advanceTimersByTimeAsync(0);
  };
  const link = (): HTMLAnchorElement | null => (fixture.nativeElement as HTMLElement).querySelector('a');
  const badge = (): string | null => (fixture.nativeElement as HTMLElement).querySelector('.mat-badge-content')?.textContent?.trim() ?? null;

  beforeEach(() => {
    sessionStorage.clear();
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AuthStore).discard();
    vi.useRealTimers();
  });

  it.each([
    ['CLAIMANT', '/claimant/notifications'],
    ['CLAIMS_STAFF', '/staff/notifications'],
  ] as const)('links a %s to their own inbox', async (role, href) => {
    await showBellFor(role);
    http.expectOne(UNREAD_COUNT).flush({ unreadCount: 0 });
    fixture.detectChanges();

    expect(link()?.getAttribute('href')).toBe(href);
  });

  it('asks for the unread count straight away and again every ten seconds', async () => {
    await showBellFor('CLAIMS_STAFF');
    http.expectOne(UNREAD_COUNT).flush({ unreadCount: 0 });

    await vi.advanceTimersByTimeAsync(9_000);
    http.expectNone(UNREAD_COUNT);

    await vi.advanceTimersByTimeAsync(1_000);
    http.expectOne(UNREAD_COUNT).flush({ unreadCount: 1 });

    await vi.advanceTimersByTimeAsync(10_000);
    http.expectOne(UNREAD_COUNT).flush({ unreadCount: 2 });
  });

  it('shows the count on the bell, and the badge goes away at zero', async () => {
    await showBellFor('CLAIMS_STAFF');
    http.expectOne(UNREAD_COUNT).flush({ unreadCount: 3 });
    fixture.detectChanges();

    expect(badge()).toBe('3');
    expect(link()?.getAttribute('aria-label')).toBe('Notifications, 3 unread');

    await vi.advanceTimersByTimeAsync(10_000);
    http.expectOne(UNREAD_COUNT).flush({ unreadCount: 0 });
    fixture.detectChanges();

    expect(link()?.getAttribute('aria-label')).toBe('Notifications');
  });

  it('renders nothing for a manager and never asks claim-service for a count', async () => {
    await showBellFor('MANAGER');

    expect(link()).toBeNull();
    await vi.advanceTimersByTimeAsync(30_000);
    http.expectNone(UNREAD_COUNT);
  });
});
