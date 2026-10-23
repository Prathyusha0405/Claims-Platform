import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { distinctUntilChanged, filter, map, skip, startWith, tap } from 'rxjs';

import { CLAIM_TYPE_LABELS, CLAIM_TYPES, ClaimType, Market, MARKET_LABELS, MARKETS } from '@shared/models/claim';
import { DashboardFilters } from '@shared/models/dashboard';
import { startOfToday } from '@shared/utils/dates';
import { Period, periodRange, PERIODS } from './period';

/** A custom range must not end before it starts; either end may be left open. */
function endsAfterItStarts(group: AbstractControl): ValidationErrors | null {
  const from = group.get('fromDate')?.value as Date | null;
  const to = group.get('toDate')?.value as Date | null;
  return from && to && from.getTime() > to.getTime() ? { rangeOrder: true } : null;
}

/**
 * The one row of filters above the manager dashboard: market, period, claim type and, for a custom period, the two
 * dates. It only reports what the manager chose (`filtersChange`, with a field left out for every filter that is open);
 * the page decides what to load. A preset period is turned into dates when it is chosen, from today's date in the
 * browser's time zone.
 */
@Component({
  imports: [
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  providers: [provideNativeDateAdapter()],
  selector: 'app-dashboard-filters',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dashboard-filters.scss',
  templateUrl: './dashboard-filters.html',
})
export class DashboardFilterBar {
  readonly filtersChange = output<DashboardFilters>();

  protected readonly markets = MARKETS;
  protected readonly marketLabels = MARKET_LABELS;
  protected readonly periods = PERIODS;
  protected readonly claimTypes = CLAIM_TYPES;
  protected readonly claimTypeLabels = CLAIM_TYPE_LABELS;

  protected readonly form = new FormGroup(
    {
      market: new FormControl<Market | ''>('', { nonNullable: true }),
      period: new FormControl<Period>('all', { nonNullable: true }),
      fromDate: new FormControl<Date | null>(null),
      toDate: new FormControl<Date | null>(null),
      claimType: new FormControl<ClaimType | ''>('', { nonNullable: true }),
    },
    { validators: [endsAfterItStarts] },
  );

  private readonly period = toSignal(this.form.controls.period.valueChanges, { initialValue: this.form.controls.period.value });
  /** The two dates are only asked for when the period is a custom one. */
  protected readonly custom = computed(() => this.period() === 'custom');
  /** True while any filter narrows the dashboard, which is when "Clear filters" is offered. */
  protected readonly filtered = signal(false);
  /** True while the custom range ends before it starts; nothing is reported until it is put right. */
  protected readonly rangeReversed = signal(false);

  constructor() {
    this.form.valueChanges
      .pipe(
        tap(() => this.rangeReversed.set(this.form.hasError('rangeOrder'))),
        filter(() => !this.rangeReversed()),
        map(() => this.filters()),
        // Start from "nothing filtered", which the page already shows, so only a real change is reported.
        startWith(this.filters()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        skip(1),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((filters) => {
        this.filtered.set(Object.keys(filters).length > 0);
        this.filtersChange.emit(filters);
      });
  }

  protected clear(): void {
    this.form.reset();
  }

  /** Only the filters that have a value; an open one must not be sent at all. */
  private filters(): DashboardFilters {
    const { market, period, fromDate, toDate, claimType } = this.form.getRawValue();
    const filters: DashboardFilters = {};
    if (market) {
      filters.market = market;
    }
    if (claimType) {
      filters.claimType = claimType;
    }
    return { ...filters, ...periodRange(period, startOfToday(), { from: fromDate, to: toDate }) };
  }
}
