import { Routes } from '@angular/router';

/** Manager pages, rendered inside AppShell under `/manager`. Gated by `roleGuard('MANAGER')` at the parent. */
export const MANAGER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard',
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
];
