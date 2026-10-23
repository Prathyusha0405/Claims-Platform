import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter } from '@angular/router';

import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import {
  claimsOverTime,
  claimTypeExposure,
  claimTypeSummary,
  liabilityExposure,
  marketSummary,
  staffWorkloadReport,
  statusSummary,
  timePeriod,
} from '@testing/dashboard-fixtures';
import { buttonLabelled, root, settle, texts } from '@testing/dom-testing';
import { Dashboard } from './dashboard';

/** Text as a reader sees it: the no-break space after a currency code is an ordinary space. */
const clean = (text: string | null): string => (text ?? '').replace(/\s+/g, ' ').trim();

describe('Dashboard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
    signInAs('MANAGER');
  });

  afterEach(() => vi.useRealTimers());

  /** The text of every cell in the `index`th element matching `rowSelector`. */
  function cells(fixture: ComponentFixture<Dashboard>, rowSelector: string, index: number): string[] {
    const row = root(fixture).querySelectorAll(rowSelector)[index];
    return Array.from(row.querySelectorAll('td')).map((cell) => (cell.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  const summaryRequest = () => http.expectOne((r) => r.url === '/api/dashboard/status-summary');
  const exposureRequest = () => http.expectOne((r) => r.url === '/api/dashboard/liability-exposure');
  const workloadRequest = () => http.expectOne((r) => r.url === '/api/dashboard/staff-workload');
  const trendRequest = () => http.expectOne((r) => r.url === '/api/dashboard/claims-over-time');

  async function render(): Promise<ComponentFixture<Dashboard>> {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    return fixture;
  }

  /** Answers the four reports with the defaults, or with the given ones. */
  async function loadAll(
    fixture: ComponentFixture<Dashboard>,
    reports: { summary?: object; exposure?: object; workload?: object; trend?: object } = {},
  ): Promise<void> {
    summaryRequest().flush(reports.summary ?? statusSummary());
    exposureRequest().flush(reports.exposure ?? liabilityExposure());
    workloadRequest().flush(reports.workload ?? staffWorkloadReport());
    trendRequest().flush(reports.trend ?? claimsOverTime());
    await settle(fixture);
  }

  /** The four requests for a changed filter, each with the query parameters it carried. */
  function reloaded(): { requests: { name: string; params: Record<string, string | null> }[]; flushAll: (reports?: object) => void } {
    const calls = [
      ['summary', summaryRequest()],
      ['exposure', exposureRequest()],
      ['workload', workloadRequest()],
      ['trend', trendRequest()],
    ] as const;
    return {
      requests: calls.map(([name, call]) => ({
        name,
        params: Object.fromEntries(call.request.params.keys().map((key) => [key, call.request.params.get(key)])),
      })),
      flushAll: () => {
        calls[0][1].flush(statusSummary());
        calls[1][1].flush(liabilityExposure());
        calls[2][1].flush(staffWorkloadReport());
        calls[3][1].flush(claimsOverTime());
      },
    };
  }

  async function choose(fixture: ComponentFixture<Dashboard>, label: string, option: string): Promise<void> {
    const field = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatFormFieldHarness.with({ floatingLabelText: label }));
    const select = (await field.getControl(MatSelectHarness))!;
    await select.open();
    await select.clickOptions({ text: option });
  }

  describe('at first', () => {
    it('asks for everything, with no filter on any of the four reports', async () => {
      const fixture = await render();

      for (const request of [summaryRequest(), exposureRequest(), workloadRequest(), trendRequest()]) {
        expect(request.request.params.keys()).toEqual([]);
      }
      expect(root(fixture).textContent).toContain('Showing All markets');
    });

    it('leads with the exposure in the reporting currency and says how it was converted', async () => {
      const fixture = await render();
      await loadAll(fixture);

      expect(texts(fixture, '.hero__figure')).toEqual(['USD 2,674,303.50']);
      const caption = texts(fixture, '.hero__caption')[0];
      expect(caption).toContain('SUM(claimAmount) WHERE status != SETTLED');
      expect(caption).toContain('across 214 claims');
      expect(caption).toContain('USD 480,955.50 of settled claims left out');
      expect(caption).toContain('converted to USD at indicative fixed rates, not live ones');
      expect(texts(fixture, '.kpi__value')).toEqual(['250', '168', '28', '18', '36']);
    });

    it('puts the one row of filters above every chart, with no filter inside any card', async () => {
      const fixture = await render();
      await loadAll(fixture);
      const page = root(fixture);

      const filters = page.querySelector('app-dashboard-filters')!;
      expect(page.querySelectorAll('app-dashboard-filters').length).toBe(1);
      expect(filters.querySelectorAll('mat-select').length).toBe(3);
      for (const panel of Array.from(page.querySelectorAll('.panel'))) {
        expect(panel.querySelector('mat-select, mat-form-field')).toBeNull();
      }
      // It comes before the first chart in the page.
      const firstChart = page.querySelector('app-column-chart, app-bar-chart, app-stacked-bar-chart')!;
      expect(filters.compareDocumentPosition(firstChart) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });
  });

  describe('claims over time', () => {
    it('plots pending and decided claims per week, with the week named in the tooltip and the table', async () => {
      const fixture = await render();
      await loadAll(fixture);
      const card = root(fixture).querySelector('section[aria-labelledby="trend-title"]')!;

      expect(card.querySelector('.panel__title')?.textContent).toBe('Claims over time');
      expect(card.querySelector('.note')?.textContent).toContain('Claims filed per week');
      expect(card.querySelectorAll('.slot').length).toBe(4);
      expect(Array.from(card.querySelectorAll('.legend__item')).map((item) => item.textContent?.trim())).toEqual(['Pending', 'Decided']);
      expect(Array.from(card.querySelectorAll('.xlabel')).map((label) => label.textContent?.trim())).toEqual(['28 Sep', '5 Oct', '12 Oct', '19 Oct']);
      expect(card.querySelector('.slot')?.getAttribute('aria-label')).toBe('Week of 28 Sep 2026: Pending 6, Decided 14; total 20');
    });

    it('goes monthly when the server says so', async () => {
      const fixture = await render();
      await loadAll(fixture, {
        trend: claimsOverTime({ granularity: 'MONTH', periods: [timePeriod('2026-08-01', 30, 40), timePeriod('2026-09-01', 25, 45)] }),
      });
      const card = root(fixture).querySelector('section[aria-labelledby="trend-title"]')!;

      expect(card.querySelector('.note')?.textContent).toContain('Claims filed per month');
      expect(Array.from(card.querySelectorAll('.xlabel')).map((label) => label.textContent?.trim())).toEqual(['Aug 2026', 'Sep 2026']);
      expect(card.querySelector('.slot')?.getAttribute('aria-label')).toBe('Aug 2026: Pending 30, Decided 40; total 70');
    });

    it('says so when no claim matches', async () => {
      const fixture = await render();
      await loadAll(fixture, { trend: claimsOverTime({ periods: [] }) });

      expect(texts(fixture, 'section[aria-labelledby="trend-title"] .chart__empty')).toEqual(['No claims in this selection.']);
    });
  });

  describe('by market', () => {
    it('charts claims by market, biggest first, pending against decided', async () => {
      const fixture = await render();
      await loadAll(fixture);
      const card = root(fixture).querySelector('section[aria-labelledby="markets-title"]')!;
      const claims = card.querySelector('app-stacked-bar-chart')!;

      expect(Array.from(claims.querySelectorAll('.bar__label')).map((label) => label.textContent?.trim())).toEqual(['Australia', 'Singapore', 'Japan']);
      expect(Array.from(claims.querySelectorAll('.bar__value')).map((value) => value.textContent?.trim())).toEqual(['70', '50', '40']);
      // Singapore: 34 pending, and 6 + 4 + 6 decided.
      expect(claims.querySelectorAll('.bar')[1].getAttribute('aria-label')).toBe('Singapore: Pending 34, Decided 16; total 50');
    });

    it('charts exposure by market in the reporting currency, biggest first, with the own-currency figure in the tooltip text', async () => {
      const fixture = await render();
      await loadAll(fixture);
      const exposure = root(fixture).querySelector('section[aria-labelledby="markets-title"] app-bar-chart')!;

      expect(Array.from(exposure.querySelectorAll('.bar__label')).map((label) => label.textContent?.trim())).toEqual(['Singapore', 'Japan', 'Australia']);
      expect(texts(fixture, 'section[aria-labelledby="markets-title"] app-bar-chart .bar__value')).toEqual(['USD 814,815', 'USD 606,667', 'USD 541,564']);
      expect(clean(exposure.querySelectorAll('.bar')[1].getAttribute('aria-label'))).toBe('Japan: USD 606,667, 30 claims, JPY 91,000,000');
    });

    it('compares every market in a table, each exposure in its own currency and in USD, with a totals row', async () => {
      const fixture = await render();
      await loadAll(fixture);

      const table = 'table[aria-label="Claims and liability exposure by market"]';
      expect(texts(fixture, `${table} tr[mat-row] td:first-child`)).toEqual(['Australia', 'Japan', 'Singapore']);
      expect(cells(fixture, `${table} tr[mat-row]`, 1)).toEqual(['Japan', 'JPY', '40', '30', 'JPY 91,000,000', 'USD 606,666.67']);
      expect(cells(fixture, `${table} tr[mat-row]`, 0)[4]).toBe('AUD 812,345.67');
      // Counts add up across markets, and so does the exposure that has already been converted to one currency.
      expect(cells(fixture, `${table} tr[mat-footer-row]`, 0)).toEqual(['All markets', '', '160', '114', '', 'USD 1,963,045.26']);
    });

    it('shows a market the exposure report has no row for as nothing owed, in its own currency', async () => {
      const fixture = await render();
      await loadAll(fixture, { summary: statusSummary({ byMarket: [marketSummary({ market: 'NZ', totalClaims: 3, pendingClaims: 1 })] }) });

      expect(texts(fixture, 'table[aria-label="Claims and liability exposure by market"] tr[mat-row] td:first-child')).toEqual(['New Zealand']);
      // No exposure row for NZ in the default fixture: shown as nothing owed, in the market's own currency.
      expect(texts(fixture, 'table[aria-label="Claims and liability exposure by market"] tr[mat-row] td:nth-child(5)')).toEqual(['NZD 0.00']);
    });
  });

  describe('claims by type', () => {
    const card = (fixture: ComponentFixture<Dashboard>) => root(fixture).querySelector('section[aria-labelledby="types-title"]')!;
    const swatches = (fixture: ComponentFixture<Dashboard>) =>
      Object.fromEntries(
        Array.from(card(fixture).querySelectorAll<HTMLElement>('.legend__item')).map((item) => [
          item.textContent?.trim(),
          (item.querySelector('.legend__swatch') as HTMLElement).style.background,
        ]),
      );

    it('compares the mix of claims and of exposure as two full-width rows, in whole reporting-currency dollars', async () => {
      const fixture = await render();
      await loadAll(fixture);

      expect(Array.from(card(fixture).querySelectorAll('.bar__label')).map((label) => label.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'Claims 250',
        'Liability exposure USD 2,674,304',
      ]);
      // Auto is 146 of 250 claims and 1,500,123 of 2,674,304 dollars.
      expect(Array.from(card(fixture).querySelectorAll('.seg__text')).map((label) => label.textContent?.trim())).toEqual(['58%', '42%', '56%', '44%']);
      expect(card(fixture).querySelector('.note')?.textContent).toContain('whole USD and each figure is rounded on its own');
    });

    it('gives each claim type its own legend and a colour that is fixed to the type', async () => {
      const fixture = await render();
      await loadAll(fixture);

      expect(swatches(fixture)).toEqual({ Auto: 'rgb(42, 120, 214)', Home: 'rgb(235, 104, 52)' });
    });

    it('lists only the types that have claims, and a type that drops out never repaints the others', async () => {
      const fixture = await render();
      await loadAll(fixture, {
        summary: statusSummary({ byClaimType: [claimTypeSummary({ claimType: 'HOME', totalClaims: 104 }), claimTypeSummary({ claimType: 'TRAVEL', totalClaims: 7 })] }),
        exposure: liabilityExposure({
          byClaimType: [claimTypeExposure({ claimType: 'HOME', exposureInReportingCurrency: 1_174_180.1 }), claimTypeExposure({ claimType: 'TRAVEL', exposureInReportingCurrency: 9_000 })],
        }),
      });

      // Auto is gone; Home keeps its orange rather than moving up to blue, and Travel keeps its own slot.
      expect(swatches(fixture)).toEqual({ Home: 'rgb(235, 104, 52)', Travel: 'rgb(237, 161, 0)' });
    });

    it('draws a type that has claims but no exposure (all settled) as zero exposure', async () => {
      const fixture = await render();
      await loadAll(fixture, {
        summary: statusSummary({ byClaimType: [claimTypeSummary(), claimTypeSummary({ claimType: 'OTHER', totalClaims: 2 })] }),
        exposure: liabilityExposure({ byClaimType: [claimTypeExposure()] }),
      });

      expect(Object.keys(swatches(fixture))).toEqual(['Auto', 'Other']);
      const exposureRow = card(fixture).querySelectorAll('.bar')[1];
      expect(clean(exposureRow.getAttribute('aria-label'))).toBe('Liability exposure: Auto USD 1,500,123 (100%), Other USD 0 (0%); total USD 1,500,123');
    });

    it('says so when no claim type has claims', async () => {
      const fixture = await render();
      await loadAll(fixture, { summary: statusSummary({ byClaimType: [] }), exposure: liabilityExposure({ byClaimType: [] }) });

      expect(texts(fixture, 'section[aria-labelledby="types-title"] .chart__empty')).toEqual(['No claims in this selection.']);
    });
  });

  describe('claims staff workload', () => {
    it('shows each staff member’s open claims in colour and decided claims in gray, as a stacked bar', async () => {
      const fixture = await render();
      await loadAll(fixture);
      const card = root(fixture).querySelector('section[aria-labelledby="workload-title"]')!;
      const chart = card.querySelector('app-stacked-bar-chart')!;

      expect(Array.from(chart.querySelectorAll('.bar__label')).map((label) => label.textContent?.trim())).toEqual(['Olivia Bennett', 'Staff #3']);
      expect(Array.from(chart.querySelectorAll('.legend__item')).map((item) => item.textContent?.trim())).toEqual(['Under review', 'Awaiting information', 'Decided']);
      // Olivia: 18 under review, 2 awaiting information, and 12 + 5 + 16 decided.
      expect(chart.querySelectorAll('.bar')[0].getAttribute('aria-label')).toBe('Olivia Bennett: Under review 18, Awaiting information 2, Decided 33; total 53');
    });

    it('shows each staff member’s open exposure in the reporting currency', async () => {
      const fixture = await render();
      await loadAll(fixture);

      expect(root(fixture).textContent).toContain('Open exposure is in USD');
      expect(texts(fixture, 'table[aria-label="Claims staff workload"] tr[mat-row] td:last-child')).toEqual(['USD 206,317.00', 'USD 563,285.00']);
    });
  });

  describe('filters', () => {
    it('limits all four reports to the chosen market but keeps the comparison complete', async () => {
      const fixture = await render();
      await loadAll(fixture);

      await choose(fixture, 'Market', 'Japan');
      const summary = summaryRequest();
      const exposure = exposureRequest();
      const workload = workloadRequest();
      const trend = trendRequest();
      for (const request of [summary, exposure, workload, trend]) {
        expect(request.request.params.get('market')).toBe('JP');
      }

      summary.flush(statusSummary({ market: 'JP', totalClaims: 40, pendingClaims: 30 }));
      exposure.flush(liabilityExposure({ market: 'JP', totalLiabilityExposure: 606_666.67 }));
      workload.flush(staffWorkloadReport({ market: 'JP', unassignedClaims: 19 }));
      trend.flush(claimsOverTime({ market: 'JP' }));
      await settle(fixture);

      expect(root(fixture).textContent).toContain('Showing Japan');
      expect(texts(fixture, '.hero__figure')).toEqual(['USD 606,666.67']);
      expect(texts(fixture, '.kpi__value')[0]).toBe('40');
      // The by-market table is the comparison: still every market, with the chosen one marked.
      const rows = 'table[aria-label="Claims and liability exposure by market"] tr[mat-row]';
      expect(texts(fixture, `${rows} td:first-child`)).toEqual(['Australia', 'Japan', 'Singapore']);
      expect(root(fixture).querySelector(`${rows}.selected td`)?.textContent?.trim()).toBe('Japan');

      await choose(fixture, 'Market', 'All markets');
      expect(reloaded().requests.map((request) => request.params)).toEqual([{}, {}, {}, {}]);
    });

    it('sends the claim type and the submission dates to all four reports, and says what it is showing', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 22, 15, 30));
      const fixture = await render();
      await loadAll(fixture);

      await choose(fixture, 'Claim type', 'Home');
      const afterType = reloaded();
      expect(afterType.requests.map((request) => request.params)).toEqual(Array(4).fill({ claimType: 'HOME' }));
      afterType.flushAll();
      await settle(fixture);

      await choose(fixture, 'Period', 'Last 90 days');
      expect(reloaded().requests.map((request) => request.params)).toEqual(
        Array(4).fill({ claimType: 'HOME', fromDate: '2026-07-25', toDate: '2026-10-22' }),
      );
    });

    it('names the market, the claim type and the date range in the subtitle', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 22, 15, 30));
      const fixture = await render();
      await loadAll(fixture);

      await choose(fixture, 'Market', 'Singapore');
      reloaded().flushAll();
      await settle(fixture);
      await choose(fixture, 'Claim type', 'Auto');
      reloaded().flushAll();
      await settle(fixture);
      await choose(fixture, 'Period', 'Last 90 days');
      await settle(fixture);

      expect(texts(fixture, '.page__subtitle')[0]).toContain('Showing Singapore · Auto · 25 Jul 2026 to 22 Oct 2026.');
    });

    it('keeps the previous figures on screen, a little fainter, while the new ones load', async () => {
      const fixture = await render();
      await loadAll(fixture);

      await choose(fixture, 'Claim type', 'Home');
      await settle(fixture);

      const trendCard = root(fixture).querySelector('section[aria-labelledby="trend-title"]')!;
      expect(trendCard.classList).toContain('stale');
      expect(trendCard.querySelectorAll('.slot').length).toBe(4);
      expect(texts(fixture, '.hero__figure')).toEqual(['USD 2,674,303.50']);
      expect(root(fixture).querySelector('mat-progress-bar')).not.toBeNull();

      reloaded().flushAll();
      await settle(fixture);
      expect(trendCard.classList).not.toContain('stale');
      expect(root(fixture).querySelector('mat-progress-bar')).toBeNull();
    });

    it('opens everything back up with "Clear filters"', async () => {
      const fixture = await render();
      await loadAll(fixture);
      await choose(fixture, 'Market', 'Japan');
      reloaded().flushAll();
      await settle(fixture);

      buttonLabelled(fixture, 'Clear filters')!.click();
      await settle(fixture);

      expect(reloaded().requests.map((request) => request.params)).toEqual([{}, {}, {}, {}]);
      expect(root(fixture).textContent).toContain('Showing All markets');
    });
  });

  describe('Refresh', () => {
    it('re-reads all four reports with the filters still applied', async () => {
      const fixture = await render();
      await loadAll(fixture);
      await choose(fixture, 'Claim type', 'Travel');
      reloaded().flushAll();
      await settle(fixture);

      buttonLabelled(fixture, 'Refresh')!.click();
      await settle(fixture);

      expect(reloaded().requests.map((request) => request.params)).toEqual(Array(4).fill({ claimType: 'TRAVEL' }));
    });
  });

  it('explains a failure, keeps the rest of the page, and tries again on request', async () => {
    const fixture = await render();
    summaryRequest().flush(statusSummary());
    exposureRequest().flush({ message: 'Claim service is unavailable' }, { status: 503, statusText: 'Unavailable' });
    workloadRequest().flush(staffWorkloadReport());
    trendRequest().flush(claimsOverTime());
    await settle(fixture);

    expect(root(fixture).textContent).toContain('We could not load the liability exposure');
    expect(root(fixture).textContent).toContain('Claim service is unavailable');
    expect(texts(fixture, '.kpi__value')).toEqual(['250', '168', '28', '18', '36']);

    buttonLabelled(fixture, 'Try again')!.click();
    fixture.detectChanges();
    await loadAll(fixture);

    expect(texts(fixture, '.hero__figure')).toEqual(['USD 2,674,303.50']);
    expect(root(fixture).textContent).not.toContain('We could not load');
  });

  it('explains a failure of the claims-over-time report on its own card', async () => {
    const fixture = await render();
    summaryRequest().flush(statusSummary());
    exposureRequest().flush(liabilityExposure());
    workloadRequest().flush(staffWorkloadReport());
    trendRequest().flush({ message: 'Claim service is unavailable' }, { status: 503, statusText: 'Unavailable' });
    await settle(fixture);

    expect(texts(fixture, 'section[aria-labelledby="trend-title"]')[0]).toContain('We could not load claims over time');
    expect(texts(fixture, '.kpi__value')).toEqual(['250', '168', '28', '18', '36']);
  });
});
