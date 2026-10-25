import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { provideRouter } from '@angular/router';

import { Notify } from '@core/services/notify';
import { ClaimDetail } from '@shared/models/claim';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { claimDetail } from '@testing/claim-fixtures';
import { buttonLabelled, root, settle } from '@testing/dom-testing';
import { ClaimReview } from './claim-review';

/** The signed-in claims staff member from `signInAs` has id 7. */
const ME = 7;

describe('ClaimReview', () => {
  let http: HttpTestingController;
  let notify: Notify;

  beforeEach(() => {
    sessionStorage.clear();
    // jsdom never finishes a CSS animation, and a dialog only reports that it closed once its animation has finished.
    TestBed.configureTestingModule({
      providers: [provideRouter([]), ...provideHttpTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    http = TestBed.inject(HttpTestingController);
    notify = TestBed.inject(Notify);
    vi.spyOn(notify, 'success').mockImplementation(() => undefined);
    vi.spyOn(notify, 'error').mockImplementation(() => undefined);
    signInAs('CLAIMS_STAFF');
  });

  afterEach(() => vi.restoreAllMocks());

  async function render(claim: Partial<ClaimDetail>): Promise<ComponentFixture<ClaimReview>> {
    const fixture = TestBed.createComponent(ClaimReview);
    fixture.componentRef.setInput('claimNumber', 'CLM-2026-000042');
    fixture.detectChanges();
    http.expectOne('/api/claims/CLM-2026-000042').flush(claimDetail(claim));
    await settle(fixture);
    http.expectOne('/api/staff/claims/42/notes').flush([]);
    await settle(fixture);
    return fixture;
  }

  it('offers a staff member the decisions on a claim they are reviewing, and no settlement yet', async () => {
    const fixture = await render({ status: 'UNDER_REVIEW', assignedStaffId: ME });

    expect(buttonLabelled(fixture, 'Approve')).not.toBeNull();
    expect(buttonLabelled(fixture, 'Reject')).not.toBeNull();
    expect(buttonLabelled(fixture, 'Settle claim')).toBeNull();
  });

  it.each(['APPROVED', 'REJECTED'] as const)('lets the staff member holding a %s claim settle it', async (status) => {
    const fixture = await render({ status, assignedStaffId: ME });

    expect(buttonLabelled(fixture, 'Settle claim')).not.toBeNull();
    expect(buttonLabelled(fixture, 'Approve')).toBeNull();
    expect(root(fixture).textContent).toContain('it stops counting towards liability exposure');
  });

  it.each(['APPROVED', 'REJECTED'] as const)('shows another staff member only who can settle a %s claim', async (status) => {
    const fixture = await render({ status, assignedStaffId: 9 });

    expect(buttonLabelled(fixture, 'Settle claim')).toBeNull();
    expect(root(fixture).textContent).toContain('held by staff member #9');
    expect(root(fixture).textContent).toContain('Only they can settle it');
  });

  it('offers nothing further on a settled claim', async () => {
    const fixture = await render({ status: 'SETTLED', assignedStaffId: ME });

    expect(buttonLabelled(fixture, 'Settle claim')).toBeNull();
    expect(root(fixture).textContent).toContain('Settled, so no further decision can be made');
  });

  it('asks for an optional comment, settles with it, then shows the claim as settled', async () => {
    const fixture = await render({ status: 'APPROVED', assignedStaffId: ME });

    buttonLabelled(fixture, 'Settle claim')!.click();
    const dialog = await TestbedHarnessEnvironment.documentRootLoader(fixture).getHarness(MatDialogHarness);
    await (await dialog.getHarness(MatInputHarness)).setValue('  Paid by bank transfer  ');
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Settle claim' }))).click();

    const request = http.expectOne('/api/staff/claims/42/settle');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ comment: 'Paid by bank transfer' });
    request.flush({});
    expect(notify.success).toHaveBeenCalledWith('CLM-2026-000042 settled.');

    fixture.detectChanges(); // the reload is requested by an effect
    http.expectOne('/api/claims/CLM-2026-000042').flush(claimDetail({ status: 'SETTLED', assignedStaffId: ME }));
    await settle(fixture);
    http.expectOne('/api/staff/claims/42/notes').flush([]);
    await settle(fixture);

    expect(buttonLabelled(fixture, 'Settle claim')).toBeNull();
    expect(root(fixture).textContent).toContain('Settled, so no further decision can be made');
  });

  it('settles with no comment at all when none is typed, and sends nothing when the dialog is cancelled', async () => {
    const fixture = await render({ status: 'REJECTED', assignedStaffId: ME });
    const documentLoader = TestbedHarnessEnvironment.documentRootLoader(fixture);

    buttonLabelled(fixture, 'Settle claim')!.click();
    let dialog = await documentLoader.getHarness(MatDialogHarness);
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Cancel' }))).click();
    http.expectNone('/api/staff/claims/42/settle');

    buttonLabelled(fixture, 'Settle claim')!.click();
    dialog = await documentLoader.getHarness(MatDialogHarness);
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Settle claim' }))).click();
    expect(http.expectOne('/api/staff/claims/42/settle').request.body).toEqual({});
  });
});
