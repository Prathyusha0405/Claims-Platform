import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ApiError } from '@core/models/api-error';

/** Shown in place of a page's content when loading it failed. `retry` lets the user try again without a reload. */
@Component({
  imports: [MatButtonModule, MatIconModule],
  selector: 'app-load-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './load-error.scss',
  templateUrl: './load-error.html',
})
export class LoadError {
  readonly error = input.required<ApiError>();
  /** What was being loaded, e.g. "your claims"; completes the heading "We could not load ...". */
  readonly subject = input('this page');
  readonly retry = output<void>();
}
