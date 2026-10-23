/** One series of a stacked chart: which values it takes from a row or column, what to call it, and its colour. */
export interface ChartSeries {
  /** The key into a row's or column's `values`. */
  key: string;
  label: string;
  /** A fill colour. It is fixed by what the series means, never by its position, so filtering never repaints it. */
  color: string;
  /** Text drawn inside the fill. Defaults to white, or near-black on a light fill such as the palette's yellow. */
  textColor?: string;
}

const WHITE = '#ffffff';
const NEAR_BLACK = '#0b0b0b';

/** Relative luminance (WCAG) of a `#rrggbb` colour; 0 for anything else. */
function luminance(color: string): number {
  const match = /^#([0-9a-f]{6})$/i.exec(color.trim());
  if (match === null) {
    return 0;
  }
  const [r, g, b] = [0, 2, 4].map((offset) => {
    const channel = parseInt(match[1].slice(offset, offset + 2), 16) / 255;
    return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** White on the palette's mid and dark fills, near-black on a light one (the yellow). */
export function textColorFor(series: ChartSeries): string {
  return series.textColor ?? (luminance(series.color) > 0.35 ? NEAR_BLACK : WHITE);
}

/** A rough width in pixels for a short label at the chart's label size, used to decide whether it fits in its mark. */
export function labelWidth(text: string): number {
  return text.length * 7.2 + 14;
}

export const countFormat = new Intl.NumberFormat('en-US');
