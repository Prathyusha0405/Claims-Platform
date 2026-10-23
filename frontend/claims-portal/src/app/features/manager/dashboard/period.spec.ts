import { dayAndMonth, describeRange, formatIsoDate, monthAndYear, periodRange, PERIODS } from './period';

// Thursday 22 October 2026, mid-afternoon: the time of day must not matter.
const TODAY = new Date(2026, 9, 22, 15, 30);

describe('periodRange', () => {
  it('leaves every date open for all time', () => {
    expect(periodRange('all', TODAY)).toEqual({});
  });

  it('runs the last 30 days from 29 days ago to today, both ends included', () => {
    expect(periodRange('last30', TODAY)).toEqual({ fromDate: '2026-09-23', toDate: '2026-10-22' });
  });

  it('runs the last 90 days from 89 days ago to today', () => {
    expect(periodRange('last90', TODAY)).toEqual({ fromDate: '2026-07-25', toDate: '2026-10-22' });
  });

  it('runs the year to date from 1 January', () => {
    expect(periodRange('ytd', TODAY)).toEqual({ fromDate: '2026-01-01', toDate: '2026-10-22' });
  });

  it('counts back across a year boundary', () => {
    expect(periodRange('last30', new Date(2027, 0, 5))).toEqual({ fromDate: '2026-12-07', toDate: '2027-01-05' });
  });

  it('takes a custom range as picked, and lets either end stay open', () => {
    const from = new Date(2026, 6, 1);
    const to = new Date(2026, 6, 31);

    expect(periodRange('custom', TODAY, { from, to })).toEqual({ fromDate: '2026-07-01', toDate: '2026-07-31' });
    expect(periodRange('custom', TODAY, { from, to: null })).toEqual({ fromDate: '2026-07-01' });
    expect(periodRange('custom', TODAY, { from: null, to })).toEqual({ toDate: '2026-07-31' });
    expect(periodRange('custom', TODAY)).toEqual({});
  });

  it('ignores dates picked earlier once a preset is chosen', () => {
    expect(periodRange('all', TODAY, { from: new Date(2026, 0, 1), to: new Date(2026, 1, 1) })).toEqual({});
  });
});

describe('PERIODS', () => {
  it('offers the presets first and the custom range last', () => {
    expect(PERIODS.map((period) => period.label)).toEqual(['All time', 'Last 30 days', 'Last 90 days', 'Year to date', 'Custom range']);
  });
});

describe('describeRange', () => {
  it('names both ends, one end, or nothing', () => {
    expect(describeRange({ fromDate: '2026-07-24', toDate: '2026-10-22' })).toBe('24 Jul 2026 to 22 Oct 2026');
    expect(describeRange({ fromDate: '2026-07-24' })).toBe('from 24 Jul 2026');
    expect(describeRange({ toDate: '2026-10-22' })).toBe('up to 22 Oct 2026');
    expect(describeRange({})).toBe('');
  });
});

describe('column labels', () => {
  it('names a week by the day it starts and a month by its name, spelling every month the same way', () => {
    expect(dayAndMonth('2026-09-28')).toBe('28 Sep');
    expect(monthAndYear('2026-09-01')).toBe('Sep 2026');
    expect(monthAndYear('2026-12-01')).toBe('Dec 2026');
  });
});

describe('formatIsoDate', () => {
  it('reads the date as the calendar day it names, whatever the time zone', () => {
    expect(formatIsoDate('2026-11-01')).toBe('1 Nov 2026');
    expect(formatIsoDate('2026-01-05')).toBe('5 Jan 2026');
  });
});
