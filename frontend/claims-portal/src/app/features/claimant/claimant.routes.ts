import { Routes } from '@angular/router';

/** Claimant pages, rendered inside AppShell under `/claimant`. Gated by `roleGuard('CLAIMANT')` at the parent. */
export const CLAIMANT_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'my-claims' },
  {
    path: 'my-claims',
    title: 'My claims',
    loadComponent: () => import('./my-claims/my-claims').then((m) => m.MyClaims),
  },
  // `claims/new` must stay above `claims/:claimNumber`, otherwise "new" would be read as a claim number.
  {
    path: 'claims/new',
    title: 'New claim',
    loadComponent: () => import('./create-claim/create-claim').then((m) => m.CreateClaim),
  },
  {
    path: 'claims/:claimNumber',
    title: 'Claim details',
    loadComponent: () => import('./claim-details/claim-details').then((m) => m.ClaimDetails),
  },
  // One notifications page serves claimants and officers, so it lives in `shared` (features never import each other).
  {
    path: 'notifications',
    title: 'Notifications',
    loadComponent: () =>
      import('@shared/components/notifications-page/notifications-page').then((m) => m.NotificationsPage),
  },
];
