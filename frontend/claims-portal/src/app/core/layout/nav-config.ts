import { Role } from '@core/models/role';

export interface NavItem {
  label: string;
  /** A Material Icons ligature name. */
  icon: string;
  route: string;
  /** Roles that see this entry in the side navigation. */
  roles: readonly Role[];
}

/**
 * The side navigation, as data. Showing an entry only to the roles that can open it is a convenience, not security:
 * the route guards and the backend enforce access either way.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'My claims', icon: 'folder_open', route: '/claimant/my-claims', roles: ['CLAIMANT'] },
  { label: 'New claim', icon: 'add_circle_outline', route: '/claimant/claims/new', roles: ['CLAIMANT'] },
  { label: 'Notifications', icon: 'notifications_none', route: '/claimant/notifications', roles: ['CLAIMANT'] },

  { label: 'Work queue', icon: 'assignment', route: '/staff/work-queue', roles: ['CLAIMS_STAFF'] },
  { label: 'Notifications', icon: 'notifications_none', route: '/staff/notifications', roles: ['CLAIMS_STAFF'] },

  { label: 'Dashboard', icon: 'dashboard', route: '/manager/dashboard', roles: ['MANAGER'] },
];

export const navItemsFor = (role: Role | null): readonly NavItem[] =>
  role === null ? [] : NAV_ITEMS.filter((item) => item.roles.includes(role));
