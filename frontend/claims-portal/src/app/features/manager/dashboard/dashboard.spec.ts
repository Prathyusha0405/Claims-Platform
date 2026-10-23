import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter } from '@angular/router';

import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { liabilityExposure, marketSummary, staffWorkloadReport, statusSummary } from '@testing/dashboard-fixtures';
import { buttonLabelled, root, settle, texts } from '@testing/dom-testing';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
    signInAs('MANAGER');
  });

  /** The text of every cell in the `index`th element matching `rowSelector`. */
  function cells(fixture: ComponentFixture<Dashboard>, rowSelector: string, index: number): string[] {
    const row = root(fixture).querySelectorAll(rowSelector)[index];
    return Array.from(row.querySelectorAll('td')).map((cell) => (cell.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  const summaryRequest = () => http.expectOne((r) => r.url === '/api/dashboard/status-summary');
  const exposureRequest = () => http.expectOne((r) => r.url === '/api/dashboard/liability-exposure');
  const workloadRequest = () => http.expectOne((r) => r.url === '/api/dashboard/staff-workload');

  async function render(): Promise<ComponentFixture<Dashboard>> {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    return fixture;
  }

  /** Answers the three reports with the defaults, or with the given ones. */
  async function loadAll(
    fixture: ComponentFixture<Dashboard>,
    reports: { summary?: object; exposure?: object; workload?: object } = {},
  ): Promise<void> {
    summaryRequest().flush(reports.summary ?? statusSummary());
    exposureRequest().flush(reports.exposure ?? liabilityExposure());
    workloadRequest().flush(reports.workload ?? staffWorkloadReport());
    await settle(fixture);
  }

  async function chooseMarket(fixture: ComponentFixture<Dashboard>, name: string): Promise<void> {
    const select = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness);
    await select.open();
    await select.clickOptions({ text: name });
  }

  it('asks for every market at first', async () => {
    const fixture = await render();

    expect(summaryRequest().request.params.has('market')).toBe(false);
    expect(exposureRequest().request.params.has('market')).toBe(false);
    expect(workloadRequest().request.params.has('market')).toBe(false);
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

  it('compares every market in a table, each exposure in its own currency and in USD, with a totals row', async () => {
    const fixture = await render();
    await loadAll(fixture);

    const table = 'table[aria-label="Claims and liability exposure by market"]';
    expect(texts(fixture, `${table} tr[mat-row] td:first-child`)).toEqual(['Australia', 'Japan', 'Singapore']);
    expect(cells(fixture, `${table} tr[mat-row]`, 1)).toEqual([
      'Japan',
      'JPY',
      '40',
      '30',
      'JPY 91,000,000',
      'USD 606,666.67',
    ]);
    expect(cells(fixture, `${table} tr[mat-row]`, 0)[4]).toBe('AUD 812,345.67');
    // Counts add up across markets, and so does the exposure that has already been converted to one currency.
    expect(cells(fixture, `${table} tr[mat-footer-row]`, 0)).toEqual(['All markets', '', '160', '114', '', 'USD 1,963,045.26']);
  });

  it('shows each staff member’s open exposure in the reporting currency', async () => {
    const fixture = await render();
    await loadAll(fixture);

    expect(root(fixture).textContent).toContain('Open exposure is in USD');
    expect(texts(fixture, 'table[aria-label="Claims staff workload"] tr[mat-row] td:last-child')).toEqual(['USD 206,317.00', 'USD 563,285.00']);
  });

  it('limits all three reports to the chosen market but keeps the comparison complete', async () => {
    const fixture = await render();
    await loadAll(fixture);

    await chooseMarket(fixture, 'Japan');
    const summary = summaryRequest();
    const exposure = exposureRequest();
    const workload = workloadRequest();
    expect(summary.request.params.get('market')).toBe('JP');
    expect(exposure.request.params.get('market')).toBe('JP');
    expect(workload.request.params.get('market')).toBe('JP');

    summary.flush(statusSummary({ market: 'JP', totalClaims: 40, pendingClaims: 30 }));
    exposure.flush(liabilityExposure({ market: 'JP', totalLiabilityExposure: 606_666.67 }));
    workload.flush(staffWorkloadReport({ market: 'JP', unassignedClaims: 19 }));
    await settle(fixture);

    expect(root(fixture).textContent).toContain('Showing Japan');
    expect(texts(fixture, '.hero__figure')).toEqual(['USD 606,666.67']);
    expect(texts(fixture, '.kpi__value')[0]).toBe('40');
    // The by-market table is the comparison: still every market, with the chosen one marked.
    const rows = 'table[aria-label="Claims and liability exposure by market"] tr[mat-row]';
    expect(texts(fixture, `${rows} td:first-child`)).toEqual(['Australia', 'Japan', 'Singapore']);
    expect(root(fixture).querySelector(`${rows}.selected td`)?.textContent?.trim()).toBe('Japan');

    await chooseMarket(fixture, 'All markets');
    expect(summaryRequest().request.params.has('market')).toBe(false);
    exposureRequest();
    workloadRequest();
  });

  it('shows a market the exposure report has no row for as nothing owed, in its own currency', async () => {
    const fixture = await render();
    await loadAll(fixture, { summary: statusSummary({ byMarket: [marketSummary({ market: 'NZ', totalClaims: 3, pendingClaims: 1 })] }) });

    expect(texts(fixture, 'table[aria-label="Claims and liability exposure by market"] tr[mat-row] td:first-child')).toEqual(['New Zealand']);
    // No exposure row for NZ in the default fixture: shown as nothing owed, in the market's own currency.
    expect(texts(fixture, 'table[aria-label="Claims and liability exposure by market"] tr[mat-row] td:nth-child(5)')).toEqual(['NZD 0.00']);
  });

  it('explains a failure, keeps the rest of the page, and tries again on request', async () => {
    const fixture = await render();
    summaryRequest().flush(statusSummary());
    exposureRequest().flush({ message: 'Claim service is unavailable' }, { status: 503, statusText: 'Unavailable' });
    workloadRequest().flush(staffWorkloadReport());
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
});
