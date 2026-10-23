import { NAV_ITEMS, navItemsFor } from './nav-config';

describe('side navigation', () => {
  it('gives a claimant their claims and notifications, and nothing of the staff areas', () => {
    expect(navItemsFor('CLAIMANT').map((item) => item.route)).toEqual([
      '/claimant/my-claims',
      '/claimant/claims/new',
      '/claimant/notifications',
    ]);
  });

  it('gives claims staff the work queue and notifications', () => {
    expect(navItemsFor('CLAIMS_STAFF').map((item) => item.route)).toEqual(['/staff/work-queue', '/staff/notifications']);
  });

  it('gives a manager the dashboard only', () => {
    expect(navItemsFor('MANAGER').map((item) => item.route)).toEqual(['/manager/dashboard']);
  });

  it('shows nothing to somebody who is not signed in', () => {
    expect(navItemsFor(null)).toEqual([]);
  });

  it('only ever lists a route inside the area of the role it is shown to', () => {
    const area = { CLAIMANT: '/claimant/', CLAIMS_STAFF: '/staff/', MANAGER: '/manager/' } as const;

    for (const item of NAV_ITEMS) {
      for (const role of item.roles) {
        expect(item.route.startsWith(area[role]), `${item.label} for ${role}`).toBe(true);
      }
    }
  });

  it('has a label, an icon and a unique route for every entry', () => {
    expect(NAV_ITEMS.every((item) => item.label && item.icon && item.route)).toBe(true);
    expect(new Set(NAV_ITEMS.map((item) => item.route)).size).toBe(NAV_ITEMS.length);
  });
});
