import { ComponentFixture } from '@angular/core/testing';

/** Test helpers only (never imported by app code): drive a rendered component through its DOM. */

/** Lets signals, effects and the HTTP replies already flushed reach the view. */
export async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

export const root = (fixture: ComponentFixture<unknown>): HTMLElement => fixture.nativeElement as HTMLElement;

/** Types into a text field as a user would, so the form control hears it. */
export function type(fixture: ComponentFixture<unknown>, selector: string, value: string): void {
  const field = root(fixture).querySelector<HTMLInputElement | HTMLTextAreaElement>(selector);
  if (field === null) {
    throw new Error(`No field matches ${selector}`);
  }
  field.value = value;
  field.dispatchEvent(new Event('input', { bubbles: true }));
}

export function click(fixture: ComponentFixture<unknown>, selector: string): void {
  const element = root(fixture).querySelector<HTMLElement>(selector);
  if (element === null) {
    throw new Error(`No element matches ${selector}`);
  }
  element.click();
}

/** The trimmed text of every element matching `selector`. */
export function texts(fixture: ComponentFixture<unknown>, selector: string): string[] {
  return Array.from(root(fixture).querySelectorAll(selector)).map((element) => (element.textContent ?? '').replace(/\s+/g, ' ').trim());
}

/** The button whose visible label contains `label`. */
export function buttonLabelled(fixture: ComponentFixture<unknown>, label: string): HTMLButtonElement | HTMLAnchorElement | null {
  const candidates = Array.from(root(fixture).querySelectorAll<HTMLButtonElement | HTMLAnchorElement>('button, a[mat-button], a[mat-flat-button]'));
  return candidates.find((button) => (button.textContent ?? '').includes(label)) ?? null;
}
