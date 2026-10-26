import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { MatInputHarness } from '@angular/material/input/testing';

import { promptForText, TextPromptData } from './text-prompt-dialog';

@Component({ template: '' })
class Host {}

const OPTIONAL: TextPromptData = {
  title: 'Approve CLM-2026-000042',
  label: 'Comment (optional)',
  confirmLabel: 'Approve claim',
  required: false,
  maxLength: 2000,
};
const REQUIRED: TextPromptData = { ...OPTIONAL, title: 'Reject CLM-2026-000042', label: 'Reason', confirmLabel: 'Reject claim', required: true };

describe('promptForText', () => {
  beforeEach(() => {
    // jsdom never finishes a CSS animation, and a dialog only reports that it closed once its animation has finished.
    TestBed.configureTestingModule({ providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }] });
  });

  async function open(data: TextPromptData) {
    const fixture = TestBed.createComponent(Host);
    const answers: (string | undefined)[] = [];
    promptForText(TestBed.inject(MatDialog), data).subscribe((answer) => answers.push(answer));
    const dialog = await TestbedHarnessEnvironment.documentRootLoader(fixture).getHarness(MatDialogHarness);
    return { dialog, answers };
  }

  it('answers nothing at all, not an empty text, when the user cancels (so nothing is approved or settled)', async () => {
    const { dialog, answers } = await open(OPTIONAL);

    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Cancel' }))).click();

    expect(answers).toEqual([undefined]);
  });

  it('answers nothing when the dialog is dismissed with Escape', async () => {
    const { dialog, answers } = await open(REQUIRED);

    await dialog.close();

    expect(answers).toEqual([undefined]);
  });

  it('answers the trimmed text when it is confirmed', async () => {
    const { dialog, answers } = await open(OPTIONAL);

    await (await dialog.getHarness(MatInputHarness)).setValue('  Damage confirmed  ');
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Approve claim' }))).click();

    expect(answers).toEqual(['Damage confirmed']);
  });

  it('answers an empty text when an optional prompt is confirmed with nothing typed', async () => {
    const { dialog, answers } = await open(OPTIONAL);

    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Approve claim' }))).click();

    expect(answers).toEqual(['']);
  });

  it('does not accept an empty or whitespace-only text when one is required', async () => {
    const { dialog, answers } = await open(REQUIRED);

    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Reject claim' }))).click();
    await (await dialog.getHarness(MatInputHarness)).setValue('   ');
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Reject claim' }))).click();

    expect(answers).toEqual([]);
  });
});
