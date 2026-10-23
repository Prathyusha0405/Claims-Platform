import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter } from '@angular/router';

import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { claimSummary, page } from '@testing/claim-fixtures';
import { root, settle, texts } from '@testing/dom-testing';
import { UnassignedQueue } from './unassigned-queue';

describe('UnassignedQueue', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
    signInAs('CLAIMS_STAFF');
  });

  const queueRequest = () => http.expectOne((r) => r.url === '/api/staff/claims/unassigned');

  async function render(): Promise<ComponentFixture<UnassignedQueue>> {
    const fixture = TestBed.createComponent(UnassignedQueue);
    fixture.detectChanges();
    return fixture;
  }

  async function chooseMarket(fixture: ComponentFixture<UnassignedQueue>, name: string): Promise<void> {
    const select = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness);
    await select.open();
    await select.clickOptions({ text: name });
  }

  it('lists every market, oldest first, until one is chosen', async () => {
    const fixture = await render();
    const request = queueRequest();
    expect(request.request.params.has('market')).toBe(false);
    expect(request.request.params.get('sort')).toBe('createdAt,asc');
    request.flush(page([claimSummary(), claimSummary({ id: 43, claimNumber: 'CLM-2026-000043', market: 'JP' })]));
    await settle(fixture);

    expect(texts(fixture, 'tr[mat-row] td:nth-child(3)')).toEqual(['Singapore', 'Japan']);
    expect(texts(fixture, 'tr[mat-row] button')).toEqual(['Take claim', 'Take claim']);
  });

  it('narrows the queue to the chosen market, from the first page, and back to all of them', async () => {
    const fixture = await render();
    queueRequest().flush(page([claimSummary()]));
    await settle(fixture);

    await chooseMarket(fixture, 'Japan');
    const narrowed = queueRequest();
    expect(narrowed.request.params.get('market')).toBe('JP');
    expect(narrowed.request.params.get('page')).toBe('0');
    narrowed.flush(page([claimSummary({ id: 43, claimNumber: 'CLM-2026-000043', market: 'JP' })]));
    await settle(fixture);
    expect(texts(fixture, 'tr[mat-row] td:nth-child(3)')).toEqual(['Japan']);

    await chooseMarket(fixture, 'All markets');
    expect(queueRequest().request.params.has('market')).toBe(false);
  });

  it('says which market has nothing waiting', async () => {
    const fixture = await render();
    queueRequest().flush(page([claimSummary()]));
    await settle(fixture);

    await chooseMarket(fixture, 'New Zealand');
    queueRequest().flush(page([]));
    await settle(fixture);

    expect(root(fixture).textContent).toContain('No submitted claim is waiting in New Zealand.');
  });
});
