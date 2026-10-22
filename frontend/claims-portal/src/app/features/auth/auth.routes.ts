import { Routes } from '@angular/router';

/** Public pages, rendered inside PublicLayout under `/auth`. Guarded by `guestGuard` at the parent route. */
export const AUTH_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    title: 'Sign in',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Create account',
    loadComponent: () => import('./register/register').then((m) => m.Register),
  },
];
