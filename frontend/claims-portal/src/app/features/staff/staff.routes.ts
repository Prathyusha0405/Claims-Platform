import { Routes } from '@angular/router';

/** Claims-staff pages, rendered inside AppShell under `/staff`. Gated by `roleGuard('CLAIMS_STAFF')` at the parent. */
export const STAFF_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'work-queue' },
  {
    path: 'work-queue',
    title: 'Work queue',
    loadComponent: () => import('./work-queue/work-queue').then((m) => m.WorkQueue),
  },
  {
    path: 'claims/:claimNumber',
    title: 'Claim review',
    loadComponent: () => import('./claim-review/claim-review').then((m) => m.ClaimReview),
  },
  // The same shared page the claimant area uses (claims staff are notified when a claimant answers an information request).
  {
    path: 'notifications',
    title: 'Notifications',
    loadComponent: () =>
      import('@shared/components/notifications-page/notifications-page').then((m) => m.NotificationsPage),
  },
];
