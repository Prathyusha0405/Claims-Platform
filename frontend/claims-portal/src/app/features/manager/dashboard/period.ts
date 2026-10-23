import { toIsoDate } from '@shared/utils/dates';

/** The ready-made date ranges on the dashboard's period filter. */
export type Period = 'all' | 'last30' | 'last90' | 'ytd' | 'custom';

export const PERIODS: readonly { value: Period; label: string }[] = [
  { value: 'all', label: 'All time' },
  { value: 'last30', label: 'Last 30 days' },
  { value: 'last90', label: 'Last 90 days' },
  { value: 'ytd', label: 'Year to date' },
  { value: 'custom', label: 'Custom range' },
];

/** A submission date range as the API takes it: `yyyy-MM-dd`, both ends inclusive, either one possibly missing. */
export interface DateRange {
  fromDate?: string;
  toDate?: string;
}

/**
 * The dates a period stands for, worked out in the browser's time zone from `today`: the last 30 days are today and
 * the 29 days before it, the last 90 days likewise, and the year to date runs from 1 January. `custom` takes whatever
 * the user picked, and either end may be left open.
 */
export function periodRange(period: Period, today: Date, custom: { from: Date | null; to: Date | null } = { from: null, to: null }): DateRange {
  switch (period) {
    case 'all':
      return {};
    case 'last30':
      return { fromDate: toIsoDate(daysBefore(today, 29)), toDate: toIsoDate(today) };
    case 'last90':
      return { fromDate: toIsoDate(daysBefore(today, 89)), toDate: toIsoDate(today) };
    case 'ytd':
      return { fromDate: toIsoDate(new Date(today.getFullYear(), 0, 1)), toDate: toIsoDate(today) };
    case 'custom': {
      const range: DateRange = {};
      if (custom.from) {
        range.fromDate = toIsoDate(custom.from);
      }
      if (custom.to) {
        range.toDate = toIsoDate(custom.to);
      }
      return range;
    }
  }
}

function daysBefore(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - days);
}

// Written out rather than taken from Intl, whose short month names differ by locale and version ("Sep" or "Sept").
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function parts(iso: string): { year: number; month: string; day: number } {
  const [year, month, day] = iso.split('-').map(Number);
  return { year, month: MONTHS[month - 1], day };
}

/** `2026-07-24` as "24 Jul 2026", read as the calendar day it names (no time zone is involved). */
export function formatIsoDate(iso: string): string {
  const { year, month, day } = parts(iso);
  return `${day} ${month} ${year}`;
}

/** The label under a weekly column: the day the week starts, "3 Aug". */
export function dayAndMonth(iso: string): string {
  const { month, day } = parts(iso);
  return `${day} ${month}`;
}

/** The label under a monthly column: "Aug 2026". */
export function monthAndYear(iso: string): string {
  const { year, month } = parts(iso);
  return `${month} ${year}`;
}

/** "24 Jul 2026 to 22 Oct 2026", "from 24 Jul 2026", "up to 22 Oct 2026", or an empty string when neither end is set. */
export function describeRange(range: DateRange): string {
  if (range.fromDate && range.toDate) {
    return `${formatIsoDate(range.fromDate)} to ${formatIsoDate(range.toDate)}`;
  }
  if (range.fromDate) {
    return `from ${formatIsoDate(range.fromDate)}`;
  }
  if (range.toDate) {
    return `up to ${formatIsoDate(range.toDate)}`;
  }
  return '';
}
