import { Route, Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth-guard';
import { guestGuard } from '@core/guards/guest-guard';
import { NAV_ITEMS } from '@core/layout/nav-config';
import { routes } from './app.routes';

interface FlatRoute {
  /** Full path from the root, e.g. `claimant/claims/:claimNumber`. */
  path: string;
  route: Route;
}

type LazyRoutes = () => Promise<Routes>;
type LazyComponent = () => Promise<unknown>;

/** Walks the whole route tree, loading every lazy area, so the tests see exactly what the router will. */
async function flatten(list: Routes, parent = ''): Promise<FlatRoute[]> {
  const result: FlatRoute[] = [];
  for (const route of list) {
    const path = [parent, route.path ?? ''].filter(Boolean).join('/');
    result.push({ path, route });
    if (route.children) {
      result.push(...(await flatten(route.children, path)));
    }
    if (route.loadChildren) {
      result.push(...(await flatten(await (route.loadChildren as LazyRoutes)(), path)));
    }
  }
  return result;
}

/** A page is a route that renders a component and has nothing nested below it. */
const isPage = (route: Route): boolean => !!route.loadComponent && !route.children && !route.loadChildren;

describe('route table', () => {
  let flat: FlatRoute[];
  const find = (path: string): Route => flat.find((f) => f.path === path)!.route;

  beforeAll(async () => {
    flat = await flatten(routes);
  });

  it('exposes exactly the pages CLAUDE.md asks for, and nothing else', () => {
    const pages = flat.filter((f) => isPage(f.route)).map((f) => f.path).sort();

    expect(pages).toEqual(
      [
        'auth/login',
        'auth/register',
        'claimant/my-claims',
        'claimant/claims/new',
        'claimant/claims/:claimNumber',
        'claimant/notifications',
        'officer/work-queue',
        'officer/claims/:claimNumber',
        'officer/notifications',
        'admin/dashboard',
        'forbidden',
        '**',
      ].sort(),
    );
  });

  it('lazy-loads every page and every feature area; nothing is bundled eagerly', () => {
    expect(flat.filter((f) => f.route.component)).toEqual([]);
    for (const area of ['auth', 'claimant', 'officer', 'admin']) {
      expect(find(area).loadChildren, `${area} must be a lazy area`).toBeTypeOf('function');
    }
  });

  it('gives every page a title', () => {
    for (const { path, route } of flat.filter((f) => isPage(f.route))) {
      expect(route.title, `title of ${path}`).toBeTypeOf('string');
      expect(String(route.title).length, `title of ${path}`).toBeGreaterThan(0);
    }
  });

  it('resolves every lazy page to a component class', async () => {
    for (const { path, route } of flat.filter((f) => isPage(f.route))) {
      expect(await (route.loadComponent as LazyComponent)(), `component of ${path}`).toBeTypeOf('function');
    }
  });

  it('guards each area: guests-only for auth, sign-in for the shell, one role gate per role area', () => {
    expect(find('auth').canActivate).toEqual([guestGuard]);

    const shell = routes.find((r) => r.path === '' && r.children)!;
    expect(shell.canActivate).toEqual([authGuard]);

    for (const area of ['claimant', 'officer', 'admin']) {
      expect(find(area).canMatch, `role gate of ${area}`).toHaveLength(1);
    }
    expect(find('forbidden').canMatch).toBeUndefined(); // reachable by any signed-in user
  });

  it('starts at the login page and keeps the wildcard last', () => {
    expect(routes[0]).toMatchObject({ path: '', pathMatch: 'full', redirectTo: 'auth/login' });
    expect(routes[routes.length - 1].path).toBe('**');
  });

  it('gives every area an index redirect that points at a page that exists', async () => {
    const landing: Record<string, string> = {
      auth: 'login',
      claimant: 'my-claims',
      officer: 'work-queue',
      admin: 'dashboard',
    };
    for (const [area, target] of Object.entries(landing)) {
      const children = await (find(area).loadChildren as LazyRoutes)();
      expect(children[0], `index of ${area}`).toMatchObject({ path: '', pathMatch: 'full', redirectTo: target });
      expect(children.some((c) => c.path === target && isPage(c)), `${area} → ${target}`).toBe(true);
    }
  });

  it('points every side-navigation entry at a page that exists', () => {
    const pages = flat.filter((f) => isPage(f.route)).map((f) => `/${f.path}`);

    for (const item of NAV_ITEMS) {
      expect(pages, `${item.label} → ${item.route}`).toContain(item.route);
    }
  });

  it('declares claims/new before claims/:claimNumber so "new" is never read as a claim number', async () => {
    const children = await (find('claimant').loadChildren as LazyRoutes)();
    const order = children.map((c) => c.path);

    expect(order.indexOf('claims/new')).toBeGreaterThan(-1);
    expect(order.indexOf('claims/new')).toBeLessThan(order.indexOf('claims/:claimNumber'));
  });
});

