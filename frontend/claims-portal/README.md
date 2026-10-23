# Claims Portal (Angular front end)

The single-page app for the Chubb APAC Claims Management Platform. One app serves three kinds of user: **claimants** file
and track claims, **claims staff** work a queue and decide claims, **managers** read the dashboards.

This file covers the front end only. For the whole system see the [root README](../../README.md) (run everything, demo
logins, endpoints, tests) and [architecture.md](../../architecture.md) (design and decisions, section 12 for this app).

## Stack

Angular 22 (standalone components, no NgModules), Angular Material 3, reactive forms, signals for state, RxJS 7.8,
TypeScript 6, Vitest + jsdom for unit tests. No state library and no charting library.

> `CLAUDE.md` asks for Angular 19; the project was scaffolded with Angular 22 (see
> [architecture.md, section 14](../../architecture.md#14-decisions-and-trade-offs)).

## Run it

The app only calls relative `/api/...` URLs, and the dev server forwards them to the three backend services
(`proxy.conf.json`), so **start the backends first**:

| Prefix | Forwarded to |
|---|---|
| `/api/auth`, `/api/users` | auth-service, `http://localhost:8084` |
| `/api/claims`, `/api/notifications` | claim-service, `http://localhost:8082` |
| `/api/staff`, `/api/dashboard` | assessment-service, `http://localhost:8083` |

Then, from this folder:

```powershell
npm install
npm start          # = ng serve, http://localhost:4200
```

Sign in with the demo accounts listed in the [root README](../../README.md#demo-logins); the login page also has
quick-fill buttons for them in development builds. If a backend is not running, its calls fail and the page shows
"We could not load ..." with a retry button instead of breaking.

Because every URL is relative there are no host names in the code and no CORS configuration anywhere. A real deployment
puts a reverse proxy with the same routing in front of the built files.

## Scripts

| Command | What it does |
|---|---|
| `npm start` | Dev server on port 4200 with the API proxy |
| `npm run build` | Production build into `dist/` (initial bundle 501.15 kB, 126.5 kB transferred, measured 2026-10-23) |
| `npm run watch` | Development build that rebuilds on change |
| `npm test` / `npx ng test --watch=false` | Unit tests, 367 tests in 28 files (Vitest, jsdom) |

On a OneDrive folder `ng build` and `ng test` can take one to three minutes.

## Pages and routes

Every page is a lazy route, so a claimant never downloads claims staff or manager code. `authGuard` protects each area and
`roleGuard(role)` (a `canMatch`) keeps the wrong role from even loading it.

| URL | Page | Who |
|---|---|---|
| `/auth/login`, `/auth/register` | Sign in, create a claimant account | signed-out users (`guestGuard`) |
| `/claimant/my-claims` | Own claims: search, filter (including by market), sort, page | CLAIMANT |
| `/claimant/claims/new` | File a claim (the market is a required field) | CLAIMANT |
| `/claimant/claims/:claimNumber` | Claim details, progress, timeline, reply to an information request | CLAIMANT |
| `/claimant/notifications`, `/staff/notifications` | Notification inbox (one shared page) | CLAIMANT, CLAIMS_STAFF |
| `/staff/work-queue` | Unassigned queue (filterable by market) and "my assigned claims" | CLAIMS_STAFF |
| `/staff/claims/:claimNumber` | Claim review: take, note, request information, approve, reject | CLAIMS_STAFF |
| `/manager/dashboard` | A filter row (market, period, claim type), status totals, liability exposure, and charts of claims over time, by market, by claim type and per claims staff member, with the By market and workload tables (own currency and USD) | MANAGER |
| `/forbidden`, anything else | Wrong area for your role, not found | any |

A signed-in user who opens a login URL, or the site root, is sent to the home page of their role. A `?returnUrl=` is
honoured only for in-app paths inside the user's own area.

## Source layout

```
src/app/
  app.config.ts, app.routes.ts   providers (router, HTTP interceptors, session restore) and the top-level route table
  core/                          singletons, imported by everything
    guards/                      authGuard, guestGuard, roleGuard
    interceptors/                authInterceptor (bearer token), errorInterceptor (ApiError, sign-out on 401)
    layout/                      app shell, role-aware navigation, notification bell, user menu, 403 / 404 pages
    services/                    API clients (auth, claim, staff, dashboard, notification), AuthStore,
                                 NotificationStore, endpoints.ts (every backend URL in one place)
    models/                      ApiError, session, role
  shared/                        reusable pieces with no feature knowledge
    components/                  claims table, filters, status chip, progress, timeline, overview, bar, stacked-bar and column charts,
                                 notifications page, text-prompt dialog, load-error
    models/                      claim, page, dashboard, notification, assessment
    utils/                       claim-rules (what claims staff may do), dates, errors, loader
  features/                      auth, claimant, staff, manager: each a route table plus its pages
src/environments/                development / production flags (demo quick-fill logins are development only)
src/testing/                     shared test helpers
proxy.conf.json                  dev-server routing of /api/* to the three services
```

Path aliases keep imports flat: `@core`, `@shared`, `@features`, `@env`, `@testing`. Features never import each other;
a page two roles need (notifications) lives in `shared`.

## How it works

* **Session.** `AuthStore` keeps the session in a signal and the token in `sessionStorage` (tab-scoped). On start-up,
  `restore()` resumes a stored token only if it is unexpired and auth-service still knows the user (`/api/users/me`), so
  refreshing a protected page does not bounce through login. The session ends 30 seconds before the token's real expiry.
* **HTTP.** `authInterceptor` attaches the bearer token to same-origin `/api/` calls (never to login or register).
  `errorInterceptor` turns every failure into an `ApiError`, so no component handles `HttpErrorResponse`; it signs the
  user out on a 401 from a protected call and shows a toast on a 403.
* **Data loading.** Pages use one helper, `loader(request, fetch)`: it refetches when the request signal changes, cancels a
  superseded request (a slow reply cannot overwrite a newer one), keeps the previous value while the next loads, and
  exposes `value`, `loading` and `error` signals.
* **What the user may do.** `reviewAccess()` maps claim status and assignee to the claims staff actions offered (take, decide,
  held by someone else, awaiting claimant, closed). It only hides buttons; the backend authorizes every call again.
* **Dashboards** use three in-house chart components (`bar-chart`, `stacked-bar-chart`, `column-chart`), each with a legend for
  two or more series, a tooltip on hover and keyboard focus, and a "View as table" twin. One filter row above the page
  (market, period, claim type, and two dates for a custom period; "Clear filters" appears when something is set) re-queries all four
  reports together with `?market=&claimType=&fromDate=&toDate=`, and the previous render stays on screen, faded, while they load. A
  custom range that ends before it starts is not sent: the row says so and the page keeps showing the previous range. The
  "By market" and "Claims by type" breakdowns never narrow by their own dimension. Colours: blue is what needs action, orange the
  second series, gray decided claims; each claim type keeps a fixed colour (see architecture §12 for the palette check). Status
  colours pair with containers that pass WCAG AA contrast.
* **Money.** `formatMoney` / `MoneyPipe` show an amount with its ISO code and the currency's own decimals (`SGD 3,840.00`,
  `JPY 506,850`). A claim's amount uses the claim's `currency`; dashboard amounts use the report's `reportingCurrency`
  (USD). The new-claim form shows the currency of the chosen market and never sends one.
* **Forms.** Forms with a `FormGroup` use `(ngSubmit)`. A form that only holds a standalone `[formControl]` has no
  `NgForm` to emit `ngSubmit`, so it uses `(submit)="$event.preventDefault(); handler()"` instead; otherwise the browser
  does a native submit and reloads the page.

## Testing

`npx ng test --watch=false` runs 367 tests: guards, interceptors, stores, API clients, the route table, the auth and
claimant pages, the notification bell (its ten-second refresh), the work queue's market filter, the manager dashboard with its filter row and its three chart components, the money formatter, and the utilities. Coverage was 69.7% of lines overall before the market, currency and dashboard work (not re-measured). The claim-review and
my-assigned-claims pages have no component specs; they were checked by walking the running app in a browser. The figures,
how they were measured and the exact commands are in the [root README](../../README.md#tests).

## Known gaps

* No component specs for the claim-review and my-assigned-claims pages (above).
* The production bundle is 1.15 kB over the 500 kB *warning* budget (the error budget is 1 MB).
* An assignee shows as "Staff #n": there is no staff directory to look a name up in.
