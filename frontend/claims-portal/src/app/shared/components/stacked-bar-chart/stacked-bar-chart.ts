import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ChartSeries, countFormat, labelWidth, textColorFor } from '../chart-series';

/** One row of the chart: a label and one number per series (a series missing from `values` counts as zero). */
export interface StackedRow {
  label: string;
  values: Readonly<Record<string, number>>;
  /** How this row's numbers are written. Defaults to the chart's own `format`, so two rows can use different units. */
  format?: (value: number) => string;
}

export type StackedMode = 'absolute' | 'percent';

interface SegmentView {
  key: string;
  label: string;
  color: string;
  textColor: string;
  value: number;
  valueText: string;
  shareText: string;
  /** CSS width of the segment: its share of the row's stack. */
  width: string;
  /** The text drawn inside the segment; empty when it would not fit. */
  inside: string;
  /** The last visible segment of the row: its outer end is the rounded one. */
  end: boolean;
}

interface RowView {
  label: string;
  totalText: string;
  /** CSS width of the whole stack: the row's total against the longest row, or all of it in percent mode. */
  stackWidth: string;
  /** Every series, zero or not, for the tooltip and the table. */
  entries: { key: string; label: string; color: string; valueText: string; shareText: string }[];
  segments: SegmentView[];
  description: string;
}

interface Tooltip {
  view: RowView;
  x: number;
  y: number;
}

/** Room kept to the right of the longest bar for its total, as in BarChart (10rem). */
const VALUE_ROOM_PX = 160;
/** The plot's width until it has been measured (and always, where there is no layout, as in tests). */
const DEFAULT_PLOT_WIDTH = 520;

/**
 * Stacked horizontal bars, one row per category and one segment per series. In `absolute` mode a row's length is its
 * total against the longest row, with the total at the tip. In `percent` mode every row fills the width and shows each
 * series' share, so two rows with different units can be compared by mix. Thin bars from one baseline, a 2px surface gap
 * between segments, a 4px rounded outer end, a legend for two or more series, a tooltip listing every series (on
 * hover and on keyboard focus) and a table twin behind "View as table". A value is written inside a segment only when
 * it fits; otherwise the tooltip and the table carry it.
 *
 * Series colours are chosen by the caller and must follow what a series means, not its position.
 */
@Component({
  imports: [MatButtonModule, MatIconModule],
  selector: 'app-stacked-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './stacked-bar-chart.scss',
  templateUrl: './stacked-bar-chart.html',
})
export class StackedBarChart {
  readonly rows = input.required<readonly StackedRow[]>();
  readonly series = input.required<readonly ChartSeries[]>();
  /** Names the chart for screen readers and the table, e.g. "Claims by market". */
  readonly caption = input.required<string>();
  readonly labelHeading = input('Category');
  readonly totalHeading = input('Total');
  readonly mode = input<StackedMode>('absolute');
  /** How numbers are written unless a row brings its own format. */
  readonly format = input<(value: number) => string>((value) => countFormat.format(value));
  readonly emptyText = input('Nothing to show.');

  protected readonly tableOpen = signal(false);
  protected readonly tooltip = signal<Tooltip | null>(null);
  protected readonly plotWidth = signal(DEFAULT_PLOT_WIDTH);
  protected readonly percent = computed(() => this.mode() === 'percent');

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly views = computed<RowView[]>(() => {
    const series = this.series();
    const rows = this.rows();
    const percent = this.percent();
    const totals = rows.map((row) => series.reduce((sum, s) => sum + Math.max(0, row.values[s.key] ?? 0), 0));
    const longest = Math.max(0, ...totals);
    const barPx = Math.max(0, this.plotWidth() - VALUE_ROOM_PX);

    return rows.map((row, index) => {
      const total = totals[index];
      const write = row.format ?? this.format();
      const entries = series.map((s) => {
        const value = Math.max(0, row.values[s.key] ?? 0);
        return { series: s, value, share: total === 0 ? 0 : value / total };
      });
      const visible = entries.filter((entry) => entry.value > 0);
      const segments: SegmentView[] = visible.map((entry, position) => {
        const valueText = write(entry.value);
        const shareText = shareOf(entry.share);
        const lengthPx = percent ? entry.share * this.plotWidth() : longest === 0 ? 0 : (entry.value / longest) * barPx;
        const text = percent ? shareText : valueText;
        return {
          key: entry.series.key,
          label: entry.series.label,
          color: entry.series.color,
          textColor: textColorFor(entry.series),
          value: entry.value,
          valueText,
          shareText,
          width: `${entry.share * 100}%`,
          inside: lengthPx >= labelWidth(text) ? text : '',
          end: position === visible.length - 1,
        };
      });
      const all = entries.map((entry) => ({
        key: entry.series.key,
        label: entry.series.label,
        color: entry.series.color,
        valueText: write(entry.value),
        shareText: shareOf(entry.share),
      }));
      const totalText = write(total);
      const parts = all.map((entry) => `${entry.label} ${entry.valueText}${percent ? ` (${entry.shareText})` : ''}`);
      return {
        label: row.label,
        totalText,
        stackWidth: percent ? '100%' : `calc((100% - var(--value-room)) * ${longest === 0 ? 0 : total / longest})`,
        entries: all,
        segments,
        description: `${row.label}: ${parts.join(', ')}; total ${totalText}`,
      };
    });
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      // Where there is no layout engine or observer (tests), the default width stands.
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(() => this.measure());
      observer.observe(this.host.nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
      this.measure();
    });
  }

  protected showAt(view: RowView, event: PointerEvent, chart: HTMLElement): void {
    const bounds = chart.getBoundingClientRect();
    this.tooltip.set({ view, x: event.clientX - bounds.left, y: (event.currentTarget as HTMLElement).offsetTop });
  }

  /** Keyboard focus shows the same tooltip as hover, anchored to the row. */
  protected showFor(view: RowView, event: FocusEvent): void {
    const row = event.currentTarget as HTMLElement;
    this.tooltip.set({ view, x: row.offsetLeft + row.offsetWidth / 2, y: row.offsetTop });
  }

  protected hide(): void {
    this.tooltip.set(null);
  }

  private measure(): void {
    const plot = this.host.nativeElement.querySelector<HTMLElement>('.bar__plot');
    const width = plot?.clientWidth ?? 0;
    if (width > 0) {
      this.plotWidth.set(width);
    }
  }
}

/** A share as a whole percentage; a real but tiny share is "<1%", never "0%". */
function shareOf(share: number): string {
  if (share > 0 && share < 0.005) {
    return '<1%';
  }
  return `${Math.round(share * 100)}%`;
}
