import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** One bar. `valueText` is the figure as the reader should see it (formatted by the caller). */
export interface BarDatum {
  label: string;
  value: number;
  valueText: string;
  /** A second line for the tooltip and the table view, e.g. "36 claims". */
  detail?: string;
  /** Drawn in the de-emphasis gray: it is on the chart for context but is not part of the headline total. */
  muted?: boolean;
}

interface Tooltip {
  datum: BarDatum;
  x: number;
  y: number;
}

/**
 * A horizontal bar chart of one series. Thin bars that grow from a single baseline, the value at the tip of each, a
 * tooltip on hover and keyboard focus, and a table twin of the same numbers behind "View as table", so nothing
 * depends on seeing colour or on hovering. One series means one colour and no legend box: the heading above the
 * chart says what it plots. `muted` bars are the emphasis pattern (accent for the story, gray for the rest).
 *
 * The app is light-only today, so the chart's own tokens have no dark variant yet.
 */
@Component({
  imports: [MatButtonModule, MatIconModule],
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './bar-chart.scss',
  templateUrl: './bar-chart.html',
})
export class BarChart {
  readonly data = input.required<readonly BarDatum[]>();
  /** Names the chart for screen readers and the table, e.g. "Claims by status". */
  readonly caption = input.required<string>();
  readonly labelHeading = input('Category');
  readonly valueHeading = input.required<string>();
  readonly detailHeading = input<string | null>(null);

  protected readonly tableOpen = signal(false);
  protected readonly tooltip = signal<Tooltip | null>(null);
  protected readonly largest = computed(() => Math.max(0, ...this.data().map((datum) => datum.value)));
  protected readonly hasMuted = computed(() => this.data().some((datum) => datum.muted));

  /** Share of the longest bar, 0 to 1. */
  protected ratio(datum: BarDatum): number {
    const largest = this.largest();
    return largest === 0 ? 0 : Math.max(0, datum.value) / largest;
  }

  protected description(datum: BarDatum): string {
    return `${datum.label}: ${datum.valueText}${datum.detail ? `, ${datum.detail}` : ''}`;
  }

  protected showAt(datum: BarDatum, event: PointerEvent, chart: HTMLElement): void {
    const bounds = chart.getBoundingClientRect();
    this.tooltip.set({ datum, x: event.clientX - bounds.left, y: (event.currentTarget as HTMLElement).offsetTop });
  }

  /** Keyboard focus shows the same tooltip as hover, anchored to the row. */
  protected showFor(datum: BarDatum, event: FocusEvent): void {
    const row = event.currentTarget as HTMLElement;
    this.tooltip.set({ datum, x: row.offsetLeft + row.offsetWidth / 2, y: row.offsetTop });
  }

  protected hide(): void {
    this.tooltip.set(null);
  }
}
