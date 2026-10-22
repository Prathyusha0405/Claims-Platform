import { ChangeDetectionStrategy, Component, DestroyRef, inject, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';

import { CLAIM_STATUS_LABELS, CLAIM_STATUSES, ClaimSearchCriteria, ClaimStatus } from '@shared/models/claim';
import { toIsoDate } from '@shared/utils/dates';

/** How long typing pauses before a search is run, so a claim number is not searched letter by letter. */
const TYPING_PAUSE_MS = 300;

/**
 * The search bar over a claim list: claim number, policy number, status and a submission date range. It only
 * reports what the user wants (`criteriaChange`); the page decides which endpoint to ask.
 */
@Component({
  imports: [
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  providers: [provideNativeDateAdapter()],
  selector: 'app-claim-filters',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './claim-filters.scss',
  templateUrl: './claim-filters.html',
})
export class ClaimFilters {
  readonly criteriaChange = output<ClaimSearchCriteria>();

  protected readonly statuses = CLAIM_STATUSES;
  protected readonly statusLabels = CLAIM_STATUS_LABELS;
  protected readonly form = new FormGroup({
    claimNumber: new FormControl('', { nonNullable: true }),
    policyNumber: new FormControl('', { nonNullable: true }),
    status: new FormControl<ClaimStatus | ''>('', { nonNullable: true }),
    fromDate: new FormControl<Date | null>(null),
    toDate: new FormControl<Date | null>(null),
  });

  constructor() {
    this.form.valueChanges
      .pipe(
        debounceTime(TYPING_PAUSE_MS),
        map(() => this.criteria()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((criteria) => this.criteriaChange.emit(criteria));
  }

  protected clear(): void {
    this.form.reset();
  }

  /** Only the filters that have a value; an unset one must not be sent at all. */
  private criteria(): ClaimSearchCriteria {
    const { claimNumber, policyNumber, status, fromDate, toDate } = this.form.getRawValue();
    const criteria: ClaimSearchCriteria = {};
    if (claimNumber.trim()) {
      criteria.claimNumber = claimNumber.trim();
    }
    if (policyNumber.trim()) {
      criteria.policyNumber = policyNumber.trim();
    }
    if (status) {
      criteria.status = status;
    }
    if (fromDate) {
      criteria.fromDate = toIsoDate(fromDate);
    }
    if (toDate) {
      criteria.toDate = toIsoDate(toDate);
    }
    return criteria;
  }
}
