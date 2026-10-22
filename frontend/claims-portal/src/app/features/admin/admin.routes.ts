import { Routes } from '@angular/router';

/** Administrator pages, rendered inside AppShell under `/admin`. Gated by `roleGuard('ADMIN')` at the parent. */
export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard',
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
];
