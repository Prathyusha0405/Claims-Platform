import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDatepickerInputHarness } from '@angular/material/datepicker/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter, Router } from '@angular/router';

import { Notify } from '@core/services/notify';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { claimDetail, claimSummary, historyEntry, page } from '@testing/claim-fixtures';
import { buttonLabelled, click, root, settle, texts, type } from '@testing/dom-testing';
import { ClaimDetails } from './claim-details/claim-details';
import { CreateClaim } from './create-claim/create-claim';
import { MyClaims } from './my-claims/my-claims';

describe('claimant pages', () => {
  let http: HttpTestingController;
  let notify: Notify;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
    notify = TestBed.inject(Notify);
    vi.spyOn(notify, 'success').mockImplementation(() => undefined);
    vi.spyOn(notify, 'error').mockImplementation(() => undefined);
    signInAs('CLAIMANT');
  });

  afterEach(() => vi.restoreAllMocks());

  describe('CreateClaim', () => {
    let fixture: ComponentFixture<CreateClaim>;
    let navigate: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
      navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      fixture = TestBed.createComponent(CreateClaim);
      await settle(fixture);
    });

    async function fillValidForm(): Promise<void> {
      const loader = TestbedHarnessEnvironment.loader(fixture);
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="policyNumber"]' }))).setValue('POL-2026-12345');
      const select = await loader.getHarness(MatSelectHarness);
      await select.open();
      await select.clickOptions({ text: 'Auto' });
      await (await loader.getHarness(MatDatepickerInputHarness)).setValue('3/4/2025');
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="claimAmount"]' }))).setValue('2500.50');
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="incidentLocation"]' }))).setValue('  Orchard Road  ');
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="description"]' }))).setValue('Rear-ended at a traffic light.');
    }

    async function submit(): Promise<void> {
      const loader = TestbedHarnessEnvironment.loader(fixture);
      await (await loader.getHarness(MatButtonHarness.with({ text: 'Submit claim' }))).click();
    }

    it('shows what is missing and sends nothing when the form is empty', async () => {
      await submit();
      await settle(fixture);

      http.expectNone('/api/claims');
      expect(texts(fixture, 'mat-error')).toContain('This field is required');
      // Policy, type, date, amount and description are required; the location is optional.
      expect(texts(fixture, 'mat-error')).toEqual(Array(5).fill('This field is required'));
    });

    it('sends the claim as the backend expects it, then opens its tracking page', async () => {
      await fillValidForm();
      await submit();

      const request = http.expectOne('/api/claims');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        policyNumber: 'POL-2026-12345',
        claimType: 'AUTO',
        incidentDate: '2025-03-04',
        incidentLocation: 'Orchard Road',
        description: 'Rear-ended at a traffic light.',
        claimAmount: 2500.5,
      });
      request.flush(claimDetail({ claimNumber: 'CLM-2026-000099' }), { status: 201, statusText: 'Created' });

      expect(notify.success).toHaveBeenCalledWith('Claim CLM-2026-000099 submitted.');
      expect(navigate).toHaveBeenCalledWith(['/claimant/claims', 'CLM-2026-000099']);
    });

    it('leaves the optional location out of the request when it is blank', async () => {
      await fillValidForm();
      const loader = TestbedHarnessEnvironment.loader(fixture);
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="incidentLocation"]' }))).setValue('   ');
      await submit();

      const body = http.expectOne('/api/claims').request.body;
      expect('incidentLocation' in body).toBe(false);
    });

    it('rejects an amount with more than two decimal places and a badly formed policy number', async () => {
      await fillValidForm();
      const loader = TestbedHarnessEnvironment.loader(fixture);
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="claimAmount"]' }))).setValue('10.999');
      await (await loader.getHarness(MatInputHarness.with({ selector: '[formControlName="policyNumber"]' }))).setValue('!!');
      await submit();
      await settle(fixture);

      http.expectNone('/api/claims');
      expect(texts(fixture, 'mat-error')).toEqual([
        "Use 4 to 30 characters: letters, digits, '-' or '/'",
        'Use at most 2 decimal places',
      ]);
    });

    it("shows the server's field errors under the fields and lets the user resubmit", async () => {
      await fillValidForm();
      await submit();

      http
        .expectOne('/api/claims')
        .flush(
          { message: 'Validation failed', fieldErrors: { incidentDate: 'must not be in the future' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await settle(fixture);

      expect(texts(fixture, 'mat-error')).toEqual(['must not be in the future']);
      expect((buttonLabelled(fixture, 'Submit claim') as HTMLButtonElement).disabled).toBe(false);
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('MyClaims', () => {
    async function render(): Promise<ComponentFixture<MyClaims>> {
      const fixture = TestBed.createComponent(MyClaims);
      fixture.detectChanges();
      return fixture;
    }

    const myClaimsRequest = () => http.expectOne((r) => r.url === '/api/claims/my-claims');

    it('lists the claimant’s claims, newest first, each linking to its tracking page', async () => {
      const fixture = await render();
      const request = myClaimsRequest();
      expect(request.request.params.get('sort')).toBe('createdAt,desc');
      request.flush(
        page([
          claimSummary(),
          claimSummary({ id: 43, claimNumber: 'CLM-2026-000043', status: 'APPROVED', claimAmount: 120000.5 }),
        ]),
      );
      await settle(fixture);

      expect(texts(fixture, 'tr[mat-row] td:first-child')).toEqual(['CLM-2026-000042', 'CLM-2026-000043']);
      expect(root(fixture).querySelector('a[href="/claimant/claims/CLM-2026-000043"]')).not.toBeNull();
      expect(texts(fixture, '.chip__label')).toEqual(['Submitted', 'Approved']);
      expect(root(fixture).textContent).toContain('$120,000.50');
    });

    it('invites the claimant to file a first claim when there are none', async () => {
      const fixture = await render();
      myClaimsRequest().flush(page([]));
      await settle(fixture);

      expect(root(fixture).textContent).toContain('You have not submitted any claims yet.');
      expect(root(fixture).querySelector('a[href="/claimant/claims/new"]')).not.toBeNull();
    });

    it('switches to the search endpoint once a filter is typed, and back to my-claims when it is cleared', async () => {
      const fixture = await render();
      myClaimsRequest().flush(page([claimSummary()]));
      await settle(fixture);

      type(fixture, 'input[formControlName="claimNumber"]', ' CLM-2026-0000 ');
      await new Promise((resolve) => setTimeout(resolve, 400)); // the search waits for typing to pause
      fixture.detectChanges();

      const search = http.expectOne((r) => r.url === '/api/claims/search');
      expect(search.request.params.get('claimNumber')).toBe('CLM-2026-0000');
      expect(search.request.params.get('page')).toBe('0');
      search.flush(page([]));
      await settle(fixture);
      expect(root(fixture).textContent).toContain('No claims match these filters.');

      click(fixture, '.filters__clear');
      await new Promise((resolve) => setTimeout(resolve, 400));
      fixture.detectChanges();
      myClaimsRequest().flush(page([claimSummary()]));
    });

    it('explains a failure and tries again on request', async () => {
      const fixture = await render();
      myClaimsRequest().flush({ message: 'Claim service is unavailable' }, { status: 503, statusText: 'Unavailable' });
      await settle(fixture);

      expect(root(fixture).textContent).toContain('We could not load your claims');
      expect(root(fixture).textContent).toContain('Claim service is unavailable');

      buttonLabelled(fixture, 'Try again')!.click();
      fixture.detectChanges();
      myClaimsRequest().flush(page([claimSummary()]));
      await settle(fixture);

      expect(texts(fixture, 'tr[mat-row] td:first-child')).toEqual(['CLM-2026-000042']);
    });
  });

  describe('ClaimDetails', () => {
    async function render(claimNumber = 'CLM-2026-000042'): Promise<ComponentFixture<ClaimDetails>> {
      const fixture = TestBed.createComponent(ClaimDetails);
      fixture.componentRef.setInput('claimNumber', claimNumber);
      fixture.detectChanges();
      return fixture;
    }

    const claimRequest = () => http.expectOne('/api/claims/CLM-2026-000042');

    it('tracks the claim: status, progress, facts and its history', async () => {
      const fixture = await render();
      claimRequest().flush(
        claimDetail({
          status: 'UNDER_REVIEW',
          assignedOfficerId: 2,
          history: [
            historyEntry(),
            historyEntry({
              eventType: 'CLAIM_ASSIGNED',
              fromStatus: 'SUBMITTED',
              toStatus: 'UNDER_REVIEW',
              performedBy: 2,
              performedByRole: 'OFFICER',
              createdAt: '2026-10-17T03:00:00Z',
            }),
          ],
        }),
      );
      await settle(fixture);

      expect(texts(fixture, 'h1')).toEqual(['CLM-2026-000042']);
      expect(texts(fixture, '.progress__step[aria-current="step"] .progress__label')[0]).toContain('Under review');
      expect(root(fixture).textContent).toContain('POL-2026-12345');
      expect(root(fixture).textContent).toContain('$2,500.00');
      expect(texts(fixture, '.timeline__label')).toEqual(['Claim submitted', 'Assigned to a claims officer']);
      expect(root(fixture).textContent).toContain('Claims officer');
      expect(root(fixture).querySelector('textarea')).toBeNull(); // nothing to answer
    });

    it('shows the officer’s question and sends the claimant’s reply, then reloads the claim', async () => {
      const fixture = await render();
      claimRequest().flush(
        claimDetail({
          status: 'MORE_INFO_REQUIRED',
          history: [
            historyEntry(),
            historyEntry({
              eventType: 'ADDITIONAL_INFO_REQUESTED',
              toStatus: 'MORE_INFO_REQUIRED',
              performedByRole: 'OFFICER',
              details: 'Please send a repair estimate.',
            }),
          ],
        }),
      );
      await settle(fixture);

      expect(texts(fixture, '.question')).toEqual(['Please send a repair estimate.']);

      // Whitespace is not a reply.
      type(fixture, 'textarea', '   ');
      click(fixture, 'form button[type="submit"]');
      await settle(fixture);
      http.expectNone('/api/claims/42/additional-info');

      type(fixture, 'textarea', 'Estimate EST-8841 from Ace Auto Works.');
      click(fixture, 'form button[type="submit"]');
      const reply = http.expectOne('/api/claims/42/additional-info');
      expect(reply.request.body).toEqual({ message: 'Estimate EST-8841 from Ace Auto Works.' });
      reply.flush(claimDetail({ status: 'UNDER_REVIEW' }));

      expect(notify.success).toHaveBeenCalled();
      fixture.detectChanges(); // the reload is requested by an effect
      claimRequest().flush(claimDetail({ status: 'UNDER_REVIEW' }));
      await settle(fixture);
      expect(root(fixture).querySelector('textarea')).toBeNull();
    });

    it('says so when the claim does not exist or is not theirs', async () => {
      const fixture = await render();
      claimRequest().flush({ message: 'Claim not found' }, { status: 404, statusText: 'Not Found' });
      await settle(fixture);

      expect(texts(fixture, 'h1')).toEqual(['Claim not found']);
      expect(root(fixture).textContent).toContain('CLM-2026-000042');
      expect(root(fixture).querySelector('app-load-error')).toBeNull();
    });
  });
});
