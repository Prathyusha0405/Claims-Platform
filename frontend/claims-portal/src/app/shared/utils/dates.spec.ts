import { startOfToday, toIsoDate } from './dates';

describe('toIsoDate', () => {
  it('formats the date the user picked, with zero-padded month and day', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(toIsoDate(new Date(2026, 10, 21))).toBe('2026-11-21');
  });

  it('keeps the calendar day just after local midnight, which a UTC conversion would move back a day east of Greenwich', () => {
    expect(toIsoDate(new Date(2026, 10, 1, 0, 30))).toBe('2026-11-01');
  });
});

describe('startOfToday', () => {
  it('is local midnight today', () => {
    const today = startOfToday();

    expect([today.getHours(), today.getMinutes(), today.getSeconds()]).toEqual([0, 0, 0]);
    expect(toIsoDate(today)).toBe(toIsoDate(new Date()));
  });
});
