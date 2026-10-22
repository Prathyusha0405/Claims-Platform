import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';

import { fieldError } from '@shared/utils/errors';

export interface TextPromptData {
  title: string;
  /** What will happen, in a sentence. */
  description?: string;
  label: string;
  confirmLabel: string;
  /** When true the confirm button stays disabled until something is typed. */
  required: boolean;
  maxLength: number;
  /** A button that cannot be undone (reject) is drawn as a warning instead of the main action. */
  destructive?: boolean;
}

/** Asks for one piece of free text, e.g. why a claim is rejected. Closes with the trimmed text, or nothing on cancel. */
@Component({
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  selector: 'app-text-prompt-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './text-prompt-dialog.scss',
  templateUrl: './text-prompt-dialog.html',
})
export class TextPromptDialog {
  protected readonly data = inject<TextPromptData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<TextPromptDialog, string>>(MatDialogRef);

  protected readonly text = new FormControl('', {
    nonNullable: true,
    validators: [
      ...(this.data.required ? [Validators.required, trimmedRequired] : []),
      Validators.maxLength(this.data.maxLength),
    ],
  });
  protected readonly error = () => fieldError(this.text);

  protected confirm(): void {
    if (this.text.invalid) {
      this.text.markAsTouched();
      return;
    }
    this.dialogRef.close(this.text.value.trim());
  }
}

/** Whitespace alone does not count as an answer. */
const trimmedRequired: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim() === '' ? { required: true } : null;

/** Opens the prompt; emits the entered text, or `undefined` if the user cancelled. */
export function promptForText(dialog: MatDialog, data: TextPromptData): Observable<string | undefined> {
  return dialog
    .open<TextPromptDialog, TextPromptData, string>(TextPromptDialog, { data, width: '520px', maxWidth: '95vw' })
    .afterClosed();
}
