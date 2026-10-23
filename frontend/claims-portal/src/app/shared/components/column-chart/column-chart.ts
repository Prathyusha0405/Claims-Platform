import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ChartSeries, countFormat } from '../chart-series';

/** One column: the short text under it, a longer title for the tooltip and the table, and one number per series. */
export interface ColumnDatum {
  /** Under the column on the x axis; may be left out of the axis when the columns are crowded. */
  label: string;
  /** In the tooltip and the table, e.g. "Week of 3 Aug 2026". */
  title: string;
  /** A series missing from `values` counts as zero. */
  values: Readonly<Record<string, number>>;
}

interface SegmentView {
  key: string;
  color: string;
  /** Height as a percentage of the plot. */
  height: number;
  /** The topmost visible segment: its outer end is the rounded one. */
  end: boolean;
}

interface ColumnView {
  label: string;
  title: string;
  showLabel: boolean;
  totalText: string;
  segments: SegmentView[];
  entries: { key: string; label: string; color: string; valueText: string }[];
  description: string;
}

interface Tooltip {
  view: ColumnView;
  x: number;
  y: number;
}

/** The plot's width until it has been measured (and always, where there is no layout, as in tests). */
const DEFAULT_PLOT_WIDTH = 640;
/** The least room an x-axis label gets, so neighbouring labels never touch. */
const LABEL_ROOM_PX = 64;
/** Half the width of the widest tooltip, so one near either edge is kept inside the chart. */
const TIP_HALF_WIDTH = 110;
const COMPACT = new Intl.NumberFormat('en-US', { notation: 'compact' });

/**
 * Stacked vertical columns over time, one column per period and one segment per series. One y axis that starts at
 * zero, clean compact ticks and hairline gridlines; thin columns (24px at most) with a 4px rounded top and a 2px gap
 * between segments; x labels thinned so they never collide. The whole slot above a column is the hover and focus
 * target, and its tooltip lists every series and the total. A legend for two or more series, a table twin behind
 * "View as table", and an empty state when there is nothing to plot.
 */
@Component({
  imports: [MatButtonModule, MatIconModule],
  selector: 'app-column-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './column-chart.scss',
  templateUrl: './column-chart.html',
})
export class ColumnChart {
  readonly data = input.required<readonly ColumnDatum[]>();
  readonly series = input.required<readonly ChartSeries[]>();
  /** Names the chart for screen readers and the table, e.g. "Claims filed per week". */
  readonly caption = input.required<string>();
  readonly labelHeading = input('Period');
  readonly totalHeading = input('Total');
  readonly format = input<(value: number) => string>((value) => countFormat.format(value));
  readonly emptyText = input('Nothing to show.');

  protected readonly tableOpen = signal(false);
  protected readonly tooltip = signal<Tooltip | null>(null);
  protected readonly plotWidth = signal(DEFAULT_PLOT_WIDTH);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  private readonly totals = computed(() =>
    this.data().map((datum) => this.series().reduce((sum, s) => sum + Math.max(0, datum.values[s.key] ?? 0), 0)),
  );

  /** The y axis: the top of the scale and a tick at every step, as percentages of the plot height. */
  protected readonly scale = computed(() => niceScale(Math.max(0, ...this.totals())));
  protected readonly ticks = computed(() => {
    const { top, ticks } = this.scale();
    return ticks.map((tick) => ({ pct: (tick / top) * 100, text: COMPACT.format(tick), grid: tick > 0 }));
  });

  protected readonly views = computed<ColumnView[]>(() => {
    const series = this.series();
    const data = this.data();
    const { top } = this.scale();
    const totals = this.totals();
    const every = Math.max(1, Math.ceil(LABEL_ROOM_PX / (this.plotWidth() / Math.max(1, data.length))));
    const write = this.format();

    return data.map((datum, index) => {
      const entries = series.map((s) => ({
        key: s.key,
        label: s.label,
        color: s.color,
        value: Math.max(0, datum.values[s.key] ?? 0),
      }));
      const visible = entries.filter((entry) => entry.value > 0);
      const totalText = write(totals[index]);
      const texts = entries.map((entry) => ({ key: entry.key, label: entry.label, color: entry.color, valueText: write(entry.value) }));
      return {
        label: datum.label,
        title: datum.title,
        showLabel: index % every === 0,
        totalText,
        segments: visible.map((entry, position) => ({
          key: entry.key,
          color: entry.color,
          height: (entry.value / top) * 100,
          end: position === visible.length - 1,
        })),
        entries: texts,
        description: `${datum.title}: ${texts.map((entry) => `${entry.label} ${entry.valueText}`).join(', ')}; total ${totalText}`,
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

  protected showAt(view: ColumnView, event: PointerEvent, chart: HTMLElement): void {
    const bounds = chart.getBoundingClientRect();
    this.tooltip.set({ view, x: clampX(event.clientX - bounds.left, bounds.width), y: this.plotTop(event.currentTarget as HTMLElement) });
  }

  /** Keyboard focus shows the same tooltip as hover, anchored to the middle of the slot. */
  protected showFor(view: ColumnView, event: FocusEvent): void {
    const slot = event.currentTarget as HTMLElement;
    const chart = slot.closest('.chart') as HTMLElement | null;
    const left = chart === null ? 0 : slot.getBoundingClientRect().left - chart.getBoundingClientRect().left;
    this.tooltip.set({ view, x: clampX(left + slot.offsetWidth / 2, chart?.clientWidth ?? 0), y: this.plotTop(slot) });
  }

  protected hide(): void {
    this.tooltip.set(null);
  }

  /** The tooltip hangs just inside the top of the plot (its CSS places it below this point). */
  private plotTop(slot: HTMLElement): number {
    const plot = slot.closest('.plot') as HTMLElement | null;
    return (plot?.offsetTop ?? 0) + 8;
  }

  private measure(): void {
    const plot = this.host.nativeElement.querySelector<HTMLElement>('.plot');
    const width = plot?.clientWidth ?? 0;
    if (width > 0) {
      this.plotWidth.set(width);
    }
  }
}

/** Keeps a tooltip anchor far enough from both edges that the tooltip stays inside the chart. */
function clampX(x: number, width: number): number {
  if (width <= TIP_HALF_WIDTH * 2) {
    return width / 2;
  }
  return Math.min(Math.max(x, TIP_HALF_WIDTH), width - TIP_HALF_WIDTH);
}

/**
 * A y axis for counts: up to four steps of 1, 2 or 5 times a power of ten, from zero to the first step at or above
 * the largest total. An empty chart gets a 0 to 1 axis.
 */
export function niceScale(max: number): { top: number; ticks: number[] } {
  if (max <= 0) {
    return { top: 1, ticks: [0, 1] };
  }
  const raw = max / 4;
  const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = Math.max(1, [1, 2, 5, 10].map((factor) => factor * magnitude).find((candidate) => candidate >= raw) ?? magnitude * 10);
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let tick = 0; tick <= top; tick += step) {
    ticks.push(tick);
  }
  return { top, ticks };
}
