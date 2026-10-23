# API guide

A hands-on tour of the REST API: one claim followed from filing to approval with `curl`, then the dashboards and the
errors you will meet. Every request and response below was captured from the running services on 2026-10-23 against the
seed data (arrays are shortened with `...`; ids and timestamps will differ on your machine).

The endpoint reference tables are in the [README](../README.md#api); for the interactive version open Swagger UI on each
service (links in the same section). Design background is in [architecture.md](../architecture.md).

## Conventions

| | |
|---|---|
| Base URLs | auth `http://localhost:8084`, claim `http://localhost:8082`, assessment `http://localhost:8083` |
| Auth | `Authorization: Bearer <accessToken>` from `POST /api/auth/login`. Tokens last one hour (`expiresIn` is in seconds). |
| Bodies | JSON, `Content-Type: application/json` |
| Paging | zero-based `page`, `size` (default 20; out-of-range values are clamped, so `size=1000` returns 100 per page). Responses carry `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`. |
| Sorting | `sort=field,asc` or `sort=field,desc` over a fixed list of fields; any other field is a 400 that lists the allowed ones |
| Money | A claim's amount is in its own `currency`, the currency of its market (AU AUD, HK HKD, JP JPY, MY MYR, NZ NZD, SG SGD); the dashboards report in USD |
| Errors | one JSON shape for every failure, see [Errors](#errors) |

Set these once. The examples are for bash or zsh (macOS, Linux, Git Bash on Windows):

```bash
AUTH=http://localhost:8084; CLAIM=http://localhost:8082; ASSESS=http://localhost:8083
```

> Windows PowerShell 5.1 mangles JSON quotes passed to `curl.exe`. Use Git Bash, or put the body in a file and send it with
> `--data-binary @body.json`.

## 1. Sign in

```bash
curl -s -X POST $AUTH/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"weiling.tan@example.com","password":"Claimant@123"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": 12, "fullName": "Wei Ling Tan", "email": "weiling.tan@example.com",
            "role": "CLAIMANT", "createdAt": "2026-01-09T22:31:59Z" }
}
```

Do the same for `staff@chubb.com` / `Staff@123` and `manager@chubb.com` / `Manager@123`, and keep the three tokens:

```bash
CLAIMANT=<accessToken from the claimant login>
STAFF=<accessToken from the claims staff login>
MANAGER=<accessToken from the manager login>
```

`GET $AUTH/api/users/me` with a token returns the signed-in user. A new account is created with
`POST /api/auth/register` (`fullName`, `email`, `password` of 8 to 72 characters with a letter and a digit) and is always a
claimant; it answers `201` with the user.

## 2. The claimant files a claim

```bash
curl -s -i -X POST $CLAIM/api/claims -H "Authorization: Bearer $CLAIMANT" -H 'Content-Type: application/json' -d '{
  "policyNumber": "POL-2026-12345", "claimType": "AUTO", "market": "SG", "incidentDate": "2026-10-15",
  "incidentLocation": "Orchard Road, Singapore",
  "description": "Rear-ended at a traffic light; bumper and tail light damaged.", "claimAmount": 2500.00 }'
```

`201 Created`, with `Location: http://localhost:8082/api/claims/CLM-2026-000251`:

```json
{
  "id": 251, "claimNumber": "CLM-2026-000251", "policyNumber": "POL-2026-12345",
  "claimantId": 12, "claimantName": "Wei Ling Tan", "claimantEmail": "weiling.tan@example.com",
  "claimType": "AUTO", "market": "SG", "status": "SUBMITTED", "claimAmount": 2500, "currency": "SGD",
  "incidentDate": "2026-10-15", "incidentLocation": "Orchard Road, Singapore",
  "description": "Rear-ended at a traffic light; bumper and tail light damaged.",
  "assignedStaffId": null,
  "createdAt": "2026-10-23T05:43:43.075400600Z", "updatedAt": "2026-10-23T05:43:43.075400600Z",
  "history": [
    { "eventType": "CLAIM_CREATED", "fromStatus": null, "toStatus": "SUBMITTED",
      "performedBy": 12, "performedByRole": "CLAIMANT", "details": "Claim submitted",
      "createdAt": "2026-10-23T05:43:43.075400600Z" }
  ]
}
```

`claimType` is one of `AUTO`, `HOME`, `HEALTH`, `TRAVEL`, `PERSONAL_ACCIDENT`, `OTHER`. `market` is required and is one of
`AU`, `HK`, `JP`, `MY`, `NZ`, `SG`: the claimant chooses it, and it is not checked against the policy number. The claimant's name and email are
copied from the token, and the claim number comes from a database sequence. The first history row is the audit trail
starting.

The amount is in the currency of the market (here 2,500.00 Singapore dollars). The server sets `currency`; a `currency` sent in the
request is ignored, so a Singapore claim cannot be filed in another currency.

## 3. Finding claims

The claimant's own claims, newest first, two per page:

```bash
curl -s "$CLAIM/api/claims/my-claims?page=0&size=2&sort=createdAt,desc" -H "Authorization: Bearer $CLAIMANT"
```

```json
{ "content": [ { "id": 251, "claimNumber": "CLM-2026-000251", "policyNumber": "POL-2026-12345",
                 "claimType": "AUTO", "market": "SG", "status": "SUBMITTED", "claimAmount": 2500, "currency": "SGD",
                 "incidentDate": "2026-10-15", "assignedStaffId": null,
                 "createdAt": "2026-10-23T05:43:43.075401Z", "updatedAt": "2026-10-23T05:43:43.075401Z" },
               ... 1 more ],
  "page": 0, "size": 2, "totalElements": 14, "totalPages": 7, "first": true, "last": false }
```

Search works for every role (a claimant only ever sees their own claims). Filters combine with AND:

| Parameter | Meaning |
|---|---|
| `claimNumber`, `policyNumber` | partial match, case-insensitive |
| `status` | exact: `SUBMITTED`, `UNDER_REVIEW`, `MORE_INFO_REQUIRED`, `APPROVED`, `REJECTED`, `SETTLED` |
| `market` | exact ISO country code: `AU`, `HK`, `JP`, `MY`, `NZ`, `SG`; any other value is a 400 that lists the allowed ones |
| `fromDate`, `toDate` | submission date, `yyyy-MM-dd`, inclusive |
| `assignedStaffId` | the claims staff member holding the claim |
| `page`, `size`, `sort` | `sort` over `createdAt` (default, newest first), `updatedAt`, `claimNumber`, `policyNumber`, `market`, `status`, `claimAmount`, `incidentDate` |

```bash
# the largest claims currently under review, submitted this year (56 match in the seed data)
curl -s "$CLAIM/api/claims/search?status=UNDER_REVIEW&fromDate=2026-01-01&sort=claimAmount,desc&size=2" \
  -H "Authorization: Bearer $STAFF"
```

A single market works the same way (19 submitted claims are waiting in Japan in the seed data):

```bash
curl -s "$CLAIM/api/claims/search?market=JP&status=SUBMITTED&size=2" -H "Authorization: Bearer $STAFF"
```

```json
{ "content": [ { "id": 245, "claimNumber": "CLM-2026-000245", "policyNumber": "MTR-JP-2025-301037",
                 "claimType": "AUTO", "market": "JP", "status": "SUBMITTED", "claimAmount": 1796175,
                 "currency": "JPY",
                 "incidentDate": "2026-10-16", "assignedStaffId": null,
                 "createdAt": "2026-10-20T19:26:05Z", "updatedAt": "2026-10-20T19:26:05Z" }, ... 1 more ],
  "page": 0, "size": 2, "totalElements": 19, "totalPages": 10, "first": true, "last": false }
```

One claim with its full history: `GET $CLAIM/api/claims/CLM-2026-000251` (a claimant asking for someone else's claim gets
`404`, not `403`).

## 4. The claims staff member takes the claim

The work queue is the submitted claims nobody holds, oldest first (100 in the seed data, plus the new one):

```bash
curl -s "$ASSESS/api/staff/claims/unassigned?size=2" -H "Authorization: Bearer $STAFF"
```

Narrow the queue to one market with `market` (same codes as above; 19 of the 100 are Japanese). The response has the same shape,
every row carrying its `market`, and an unknown code is a 400:

```bash
curl -s "$ASSESS/api/staff/claims/unassigned?market=JP&size=2" -H "Authorization: Bearer $STAFF"
```

Take the claim (the claim id, not the number). It becomes `UNDER_REVIEW` with this claims staff member as the assignee:

```bash
curl -s -X POST $ASSESS/api/staff/claims/251/assign -H "Authorization: Bearer $STAFF"
```

```json
{ "claimId": 251, "claimNumber": "CLM-2026-000251", "claimStatus": "UNDER_REVIEW",
  "staffId": 2, "staffName": "Olivia Bennett", "assignedAt": "2026-10-23T05:43:45.307812Z",
  "decision": null, "decisionReason": null, "decidedAt": null }
```

An internal note, which the claimant never sees (`201`):

```bash
curl -s -X POST $ASSESS/api/staff/claims/251/notes -H "Authorization: Bearer $STAFF" \
  -H 'Content-Type: application/json' -d '{"content":"Called the workshop; estimate looks reasonable."}'
```

```json
{ "id": 201, "claimId": 251, "staffId": 2, "staffName": "Olivia Bennett", "noteType": "GENERAL",
  "content": "Called the workshop; estimate looks reasonable.", "createdAt": "2026-10-23T05:43:46.254671200Z" }
```

Ask the claimant for more information. The claim moves to `MORE_INFO_REQUIRED` and the claimant is notified:

```bash
curl -s -X POST $ASSESS/api/staff/claims/251/request-info -H "Authorization: Bearer $STAFF" \
  -H 'Content-Type: application/json' -d '{"message":"Please send a repair estimate and photos of the damage."}'
```

## 5. The claimant replies, the claims staff member decides

```bash
curl -s -X POST $CLAIM/api/claims/251/additional-info -H "Authorization: Bearer $CLAIMANT" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Repair estimate from Ace Auto Works attached under reference EST-8841."}'
```

The claim is back to `UNDER_REVIEW` (the response is the claim with its history). Now the assigned staff member approves, with an
optional comment. To reject instead, `POST .../reject` with a required `{"reason": "..."}`.

```bash
curl -s -X POST $ASSESS/api/staff/claims/251/approve -H "Authorization: Bearer $STAFF" \
  -H 'Content-Type: application/json' -d '{"comment":"Damage confirmed by the repair estimate."}'
```

```json
{ "claimId": 251, "claimNumber": "CLM-2026-000251", "claimStatus": "APPROVED",
  "staffId": 2, "staffName": "Olivia Bennett", "assignedAt": "2026-10-23T05:43:45.307812Z",
  "decision": "APPROVED", "decisionReason": "Damage confirmed by the repair estimate.",
  "decidedAt": "2026-10-23T05:43:46.495286100Z" }
```

`GET $CLAIM/api/claims/CLM-2026-000251` now shows `"status": "APPROVED"` and five history entries, one per step:
`CLAIM_CREATED`, `CLAIM_ASSIGNED`, `ADDITIONAL_INFO_REQUESTED`, `ADDITIONAL_INFO_RECEIVED`, `CLAIM_APPROVED`.

## 6. Notifications

The claimant's inbox (newest first, `unreadOnly=true` filters) and badge count:

```bash
curl -s "$CLAIM/api/notifications?size=2" -H "Authorization: Bearer $CLAIMANT"
curl -s $CLAIM/api/notifications/unread-count -H "Authorization: Bearer $CLAIMANT"
```

```json
{ "content": [ { "id": 206, "claimId": 251, "claimNumber": "CLM-2026-000251", "eventType": "CLAIM_APPROVED",
                 "message": "Your claim CLM-2026-000251 has been approved.", "read": false,
                 "createdAt": "2026-10-23T05:43:46.483170Z" }, ... 1 more ],
  "page": 0, "size": 2, "totalElements": 16, "totalPages": 8, "first": true, "last": false }
```

```json
{ "unreadCount": 6 }
```

`POST /api/notifications/{id}/read` marks one as read and `POST /api/notifications/read-all` marks everything (`{"markedRead": n}`).
You only ever see and change your own; another user's id is a `404`.

Claims staff use the same endpoints with their own token. They receive two kinds of notification: `CLAIM_CREATED` for every new
claim ("A new claim CLM-2026-000251 has been submitted and is waiting in the work queue."), sent to each claims staff member
claim-service knows of, and `ADDITIONAL_INFO_RECEIVED` when a claimant answers a request on a claim they hold. Managers are
never notified.

```bash
curl -s $CLAIM/api/notifications/unread-count -H "Authorization: Bearer $STAFF"
```

## 7. Dashboards (manager only)

Computed live from claim-service on every request. Claims are in their own market's currency, so the dashboards report money
in **USD**, converting each market's totals at the fixed, indicative rates in assessment-service's configuration (per 1 USD:
AUD 1.50, HKD 7.80, JPY 150, MYR 4.50, NZD 1.65, SGD 1.35; not live rates). Each dashboard takes an optional `market` (`AU`,
`HK`, `JP`, `MY`, `NZ`, `SG`; anything else is a 400) that narrows the headline figures to that market, while `byMarket` still
lists every market. Every dashboard also takes `claimType` (one of the six types) and `fromDate` / `toDate` (`yyyy-MM-dd`; the
claim's submission date in UTC, both ends inclusive, either may be left out). All filters are optional and combine with AND, and
every response echoes the ones that were applied (`market`, `claimType`, `fromDate`, `toDate`; `null` when open). An unknown market
or claim type, a malformed date, or a range that ends before it starts is a 400. The breakdowns are the comparison, so `byMarket`
ignores the market filter and `byClaimType` ignores the claim type filter, while each follows the other filters. With the seed
data plus the one claim above (2,500.00 SGD, about 1,851.85 USD):

```bash
curl -s $ASSESS/api/dashboard/status-summary -H "Authorization: Bearer $MANAGER"
```

```json
{ "totalClaims": 251, "pendingClaims": 168, "approvedClaims": 29, "rejectedClaims": 18, "settledClaims": 36,
  "claimsByStatus": { "SUBMITTED": 100, "UNDER_REVIEW": 56, "MORE_INFO_REQUIRED": 12,
                      "APPROVED": 29, "REJECTED": 18, "SETTLED": 36 },
  "market": null, "claimType": null, "fromDate": null, "toDate": null,
  "byMarket": [ { "market": "AU", "totalClaims": 70, "pendingClaims": 50, "approvedClaims": 6,
                  "rejectedClaims": 5, "settledClaims": 9 }, ... 5 more ],
  "byClaimType": [ { "claimType": "AUTO", "totalClaims": 147, "pendingClaims": 98, "approvedClaims": 20,
                     "rejectedClaims": 10, "settledClaims": 19 },
                   { "claimType": "HOME", "totalClaims": 104, "pendingClaims": 70, "approvedClaims": 9,
                     "rejectedClaims": 8, "settledClaims": 17 } ],
  "generatedAt": "2026-10-23T08:27:51.038515600Z" }
```

```bash
curl -s $ASSESS/api/dashboard/staff-workload -H "Authorization: Bearer $MANAGER"
```

```json
{ "reportingCurrency": "USD", "market": null, "claimType": null, "fromDate": null, "toDate": null,
  "unassignedClaims": 100,
  "staff": [ { "staffId": 2, "staffName": "Olivia Bennett", "openClaims": 20, "underReview": 18,
                  "awaitingInformation": 2, "approved": 13, "rejected": 5, "settled": 16,
                  "totalAssigned": 54, "openExposure": 206317.01 }, ... 4 more ],
  "generatedAt": "2026-10-23T08:27:51.081609900Z" }
```

`openExposure` is in USD. The liability exposure is the one figure the brief defines, `SUM(claimAmount) WHERE status != SETTLED`,
here after conversion; `byMarket` shows each market in its own currency and in USD:

```bash
curl -s $ASSESS/api/dashboard/liability-exposure -H "Authorization: Bearer $MANAGER"
```

```json
{ "reportingCurrency": "USD", "market": null, "claimType": null, "fromDate": null, "toDate": null,
  "totalLiabilityExposure": 2676155.44, "exposedClaims": 215, "excludedSettledAmount": 480955.52,
  "formula": "SUM(claimAmount) WHERE status != SETTLED",
  "byStatus": [ { "status": "SUBMITTED", "claimCount": 100, "amount": 850718.02, "includedInExposure": true },
                ... 5 more ],
  "byMarket": [ { "market": "AU", "currency": "AUD", "rate": 1.5, "claimCount": 61,
                  "exposure": 969380.25, "exposureInReportingCurrency": 646253.5 }, ... 5 more ],
  "byClaimType": [ { "claimType": "AUTO", "claimCount": 128, "exposureInReportingCurrency": 825079.4 },
                   { "claimType": "HOME", "claimCount": 87, "exposureInReportingCurrency": 1851076.02 } ],
  "generatedAt": "2026-10-23T08:27:51.106878700Z" }
```

Limited to Japan (`?market=JP`), the headline figures cover Japanese claims only (40 claims, 37 of them exposed) and
`byMarket` is unchanged:

```bash
curl -s "$ASSESS/api/dashboard/liability-exposure?market=JP" -H "Authorization: Bearer $MANAGER"
```

```json
{ "reportingCurrency": "USD", "market": "JP", "claimType": null, "fromDate": null, "toDate": null,
  "totalLiabilityExposure": 602675.5, "exposedClaims": 37, "excludedSettledAmount": 46041,
  "formula": "SUM(claimAmount) WHERE status != SETTLED",
  "byStatus": [ { "status": "SUBMITTED", "claimCount": 19, "amount": 92413, "includedInExposure": true }, ... 5 more ],
  "byMarket": [ ...the same six markets as above... ],
  "byClaimType": [ { "claimType": "AUTO", "claimCount": 18, "exposureInReportingCurrency": 76172.5 },
                   { "claimType": "HOME", "claimCount": 19, "exposureInReportingCurrency": 526503 } ],
  "generatedAt": "2026-10-23T08:27:51.175797Z" }
```

### Claim type and submission dates

Only the property claims submitted from 1 September to 22 October 2026:

```bash
curl -s "$ASSESS/api/dashboard/liability-exposure?claimType=HOME&fromDate=2026-09-01&toDate=2026-10-22" -H "Authorization: Bearer $MANAGER"
```

```json
{ "reportingCurrency": "USD", "market": null, "claimType": "HOME", "fromDate": "2026-09-01", "toDate": "2026-10-22",
  "totalLiabilityExposure": 1315261.02, "exposedClaims": 61, "excludedSettledAmount": 0,
  "formula": "SUM(claimAmount) WHERE status != SETTLED",
  "byStatus": [ { "status": "SUBMITTED", "claimCount": 41, "amount": 516374, "includedInExposure": true }, ... 5 more ],
  "byMarket": [ ...every market, counting only the property claims of those dates... ],
  "byClaimType": [ { "claimType": "AUTO", "claimCount": 84, "exposureInReportingCurrency": 521410.53 },
                   { "claimType": "HOME", "claimCount": 61, "exposureInReportingCurrency": 1315261.02 } ],
  "generatedAt": "2026-10-23T11:48:14.049810100Z" }
```

The headline is the property claims only, while `byClaimType` still lists motor next to property (for the same dates), so one can
be read against the other. Each type is converted and rounded per market on its own, so the types can differ from the headline by
a few cents: over the whole seed plus the guide's claim the two add up to 2,676,155.42 against 2,676,155.44.

## 7a. Claims over time (manager only)

How many claims were submitted in each week or month, and where they stand now. It takes the same four filters, the market
included:

```bash
curl -s "$ASSESS/api/dashboard/claims-over-time" -H "Authorization: Bearer $MANAGER"
```

```json
{ "granularity": "MONTH", "market": null, "claimType": null, "fromDate": null, "toDate": null,
  "periods": [
    { "start": "2025-11-01", "totalClaims": 3, "pendingClaims": 0, "decidedClaims": 3,
      "claimsByStatus": { "SUBMITTED": 0, "UNDER_REVIEW": 0, "MORE_INFO_REQUIRED": 0, "APPROVED": 0, "REJECTED": 0, "SETTLED": 3 } },
    ... 10 more months ...
    { "start": "2026-10-01", "totalClaims": 87, "pendingClaims": 86, "decidedClaims": 1,
      "claimsByStatus": { "SUBMITTED": 78, "UNDER_REVIEW": 7, "MORE_INFO_REQUIRED": 1, "APPROVED": 1, "REJECTED": 0, "SETTLED": 0 } } ],
  "generatedAt": "2026-10-23T11:48:14.000818100Z" }
```

The claims span a year, so the periods are months. For a window of up to 183 days they are weeks (starting on Monday), here the
Singapore motor claims of September:

```bash
curl -s "$ASSESS/api/dashboard/claims-over-time?market=SG&claimType=AUTO&fromDate=2026-09-01&toDate=2026-09-30" -H "Authorization: Bearer $MANAGER"
```

```json
{ "granularity": "WEEK", "market": "SG", "claimType": "AUTO", "fromDate": "2026-09-01", "toDate": "2026-09-30",
  "periods": [
    { "start": "2026-08-31", "totalClaims": 1, "pendingClaims": 1, "decidedClaims": 0,
      "claimsByStatus": { "SUBMITTED": 0, "UNDER_REVIEW": 0, "MORE_INFO_REQUIRED": 1, "APPROVED": 0, "REJECTED": 0, "SETTLED": 0 } },
    { "start": "2026-09-07", "totalClaims": 2, ... }, { "start": "2026-09-14", "totalClaims": 2, ... },
    { "start": "2026-09-21", "totalClaims": 2, ... },
    { "start": "2026-09-28", "totalClaims": 0, "pendingClaims": 0, "decidedClaims": 0,
      "claimsByStatus": { "SUBMITTED": 0, "UNDER_REVIEW": 0, "MORE_INFO_REQUIRED": 0, "APPROVED": 0, "REJECTED": 0, "SETTLED": 0 } } ],
  "generatedAt": "2026-10-23T11:48:14.028642600Z" }
```

`start` is the first day of the period (UTC). The first week begins on the Monday on or before 1 September, and a quiet period is
a row of zeros, never a missing row. `pendingClaims` is submitted + under review + awaiting information, `decidedClaims` is approved
+ rejected + settled, and each period's `claimsByStatus` has all six statuses. A window with no claims gives `"periods": []`.

An unknown market is refused before claim-service is asked:

```json
{ "status": 400, "error": "Bad Request", "message": "Unknown market 'XX'; allowed values: [AU, HK, JP, MY, NZ, SG]",
  "path": "/api/dashboard/status-summary", "timestamp": "2026-10-23T08:27:51.188396500Z" }
```

So are an unknown claim type (`"message": "Unknown claimType 'BOAT'; allowed values: [AUTO, HOME, HEALTH, TRAVEL, PERSONAL_ACCIDENT, OTHER]"`)
and a range that ends before it starts (`"message": "fromDate must not be after toDate"`). A malformed date is a 400 with the field
named under `fieldErrors`.

The exposure is the seed's 2,674,303.58 USD plus the new claim's 2,500.00 SGD (about 1,851.85 USD; the cents differ by one
because each market-and-status total is rounded), and it is not reduced by the approval,
because only `SETTLED` claims are excluded and nothing can settle a claim yet (see the README's known limitations).

## Errors

Every service answers every failure in the same shape; `fieldErrors` appears only for validation problems.

**400, validation** (`POST /api/claims` with a bad policy number, no market, a blank description and a negative amount):

```json
{ "timestamp": "2026-10-23T05:43:43.720136Z", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "path": "/api/claims",
  "fieldErrors": {
    "policyNumber": "must be 4-30 characters: letters, digits, '-' or '/'",
    "market": "must not be null",
    "description": "must not be blank",
    "claimAmount": "must be greater than zero" } }
```

**401, no token or a bad login:**

```json
{ "timestamp": "2026-10-23T05:43:46.665256200Z", "status": 401, "error": "Unauthorized",
  "message": "Authentication is required to access this resource", "path": "/api/claims/my-claims" }
```

**403, wrong role** (a claimant on `/api/staff/...`, or a claims staff member on `/api/dashboard/...`):

```json
{ "timestamp": "2026-10-23T05:43:46.645035Z", "status": 403, "error": "Forbidden",
  "message": "You do not have permission to access this resource", "path": "/api/staff/claims/unassigned" }
```

**409, invalid transition** (approving a claim that is already approved):

```json
{ "timestamp": "2026-10-23T05:43:46.626719Z", "status": 409, "error": "Conflict",
  "message": "Cannot move a claim from APPROVED to APPROVED; allowed next statuses: [SETTLED]",
  "path": "/api/staff/claims/251/approve" }
```

| Status | When |
|---|---|
| 400 | Validation failed (including a missing or unknown `market`), malformed JSON, or an unsupported sort field (the message lists the allowed ones) |
| 401 | Missing, expired or tampered token; wrong email or password (the message does not say which) |
| 403 | The role may not call this endpoint, or the claims staff member is not the claim's assignee |
| 404 | Unknown claim, or a claim or notification that belongs to someone else |
| 409 | Status transition not allowed, or another claims staff member took the claim first (optimistic lock) |
| 502 / 503 | assessment-service could not understand / could not reach claim-service |
| 500 | Anything unexpected; the message is generic and the cause is only in the server log |
