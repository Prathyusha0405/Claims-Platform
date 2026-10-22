import { NAV_ITEMS, navItemsFor } from './nav-config';

describe('side navigation', () => {
  it('gives a claimant their claims and notifications, and nothing of the staff areas', () => {
    expect(navItemsFor('CLAIMANT').map((item) => item.route)).toEqual([
      '/claimant/my-claims',
      '/claimant/claims/new',
      '/claimant/notifications',
    ]);
  });

  it('gives an officer the work queue and notifications', () => {
    expect(navItemsFor('OFFICER').map((item) => item.route)).toEqual(['/officer/work-queue', '/officer/notifications']);
  });

  it('gives an admin the dashboard only', () => {
    expect(navItemsFor('ADMIN').map((item) => item.route)).toEqual(['/admin/dashboard']);
  });

  it('shows nothing to somebody who is not signed in', () => {
    expect(navItemsFor(null)).toEqual([]);
  });

  it('only ever lists a route inside the area of the role it is shown to', () => {
    const area = { CLAIMANT: '/claimant/', OFFICER: '/officer/', ADMIN: '/admin/' } as const;

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
