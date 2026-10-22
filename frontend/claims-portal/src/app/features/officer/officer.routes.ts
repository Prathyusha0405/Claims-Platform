import { Routes } from '@angular/router';

/** Claims-officer pages, rendered inside AppShell under `/officer`. Gated by `roleGuard('OFFICER')` at the parent. */
export const OFFICER_ROUTES: Routes = [
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
  // The same shared page the claimant area uses (officers are notified when a claimant answers an information request).
  {
    path: 'notifications',
    title: 'Notifications',
    loadComponent: () =>
      import('@shared/components/notifications-page/notifications-page').then((m) => m.NotificationsPage),
  },
];
