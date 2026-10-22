import { Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth-guard';
import { guestGuard } from '@core/guards/guest-guard';
import { roleGuard } from '@core/guards/role-guard';

/**
 * Top-level route table. Every page is lazy-loaded; each feature area ships as its own chunk, so for example a
 * claimant never downloads the officer or admin code.
 *
 *   /                                  → /auth/login, where guestGuard sends anyone already signed in on to their
 *                                        role's home page (the redirect itself cannot carry a guard)
 *   /auth/*          PublicLayout      guestGuard      login, register
 *   /claimant/*      AppShell          authGuard + roleGuard('CLAIMANT')
 *   /officer/*       AppShell          authGuard + roleGuard('OFFICER')
 *   /admin/*         AppShell          authGuard + roleGuard('ADMIN')
 *   /forbidden       AppShell          authGuard       "you are signed in but not allowed here"
 *   anything else    (no layout)                       not found
 *
 * Guards: `authGuard` (canActivate) answers "are you signed in?" once for the whole shell; `roleGuard` (canMatch)
 * answers "is this your area?" before the area's code is even downloaded.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'auth/login' },

  // ── Public area: anonymous visitors only ───────────────────────────────────────────────────────────────
  {
    path: 'auth',
    canActivate: [guestGuard],
    loadComponent: () => import('@core/layout/public-layout/public-layout').then((m) => m.PublicLayout),
    loadChildren: () => import('@features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },

  // ── Authenticated area: one pathless AppShell (toolbar + navigation) around every role area ────────────
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('@core/layout/app-shell/app-shell').then((m) => m.AppShell),
    children: [
      {
        path: 'claimant',
        canMatch: [roleGuard('CLAIMANT')],
        loadChildren: () => import('@features/claimant/claimant.routes').then((m) => m.CLAIMANT_ROUTES),
      },
      {
        path: 'officer',
        canMatch: [roleGuard('OFFICER')],
        loadChildren: () => import('@features/officer/officer.routes').then((m) => m.OFFICER_ROUTES),
      },
      {
        path: 'admin',
        canMatch: [roleGuard('ADMIN')],
        loadChildren: () => import('@features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        path: 'forbidden',
        title: 'Access denied',
        loadComponent: () => import('@core/layout/forbidden/forbidden').then((m) => m.Forbidden),
      },
    ],
  },

  // ── Everything else ─────────────────────────────────────────────────────────────────────────────────────
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('@core/layout/not-found/not-found').then((m) => m.NotFound),
  },
];
