import { ComponentFixture, TestBed } from '@angular/core/testing';

import { root, settle, texts } from '@testing/dom-testing';
import { ChartSeries } from '../chart-series';
import { ColumnChart, ColumnDatum, niceScale } from './column-chart';

const SERIES: ChartSeries[] = [
  { key: 'pending', label: 'Pending', color: '#2a78d6' },
  { key: 'decided', label: 'Decided', color: '#898781', textColor: '#0b0b0b' },
];

const WEEKS: ColumnDatum[] = [
  { label: '28 Sep', title: 'Week of 28 Sep 2026', values: { pending: 6, decided: 14 } },
  { label: '5 Oct', title: 'Week of 5 Oct 2026', values: { pending: 9, decided: 8 } },
  { label: '12 Oct', title: 'Week of 12 Oct 2026', values: { pending: 0, decided: 0 } },
  { label: '19 Oct', title: 'Week of 19 Oct 2026', values: { pending: 12, decided: 2 } },
];

describe('niceScale', () => {
  it.each([
    [0, 1, [0, 1]],
    [1, 1, [0, 1]],
    [3, 3, [0, 1, 2, 3]],
    [7, 8, [0, 2, 4, 6, 8]],
    [20, 20, [0, 5, 10, 15, 20]],
    [250, 300, [0, 100, 200, 300]],
    [1500, 1500, [0, 500, 1000, 1500]],
  ])('gives a total of %s a scale up to %s with clean ticks', (max, top, ticks) => {
    expect(niceScale(max)).toEqual({ top, ticks });
  });

  it('never needs more than five ticks, and always reaches the largest total', () => {
    for (const max of [1, 2, 9, 13, 47, 99, 101, 640, 9_999, 123_456]) {
      const { top, ticks } = niceScale(max);

      expect(top).toBeGreaterThanOrEqual(max);
      expect(ticks.length).toBeLessThanOrEqual(6);
      expect(ticks[0]).toBe(0);
      expect(ticks[ticks.length - 1]).toBe(top);
    }
  });
});

describe('ColumnChart', () => {
  async function render(data: ColumnDatum[], options: { emptyText?: string } = {}): Promise<ComponentFixture<ColumnChart>> {
    const fixture = TestBed.createComponent(ColumnChart);
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('series', SERIES);
    fixture.componentRef.setInput('caption', 'Claims filed per week');
    if (options.emptyText) {
      fixture.componentRef.setInput('emptyText', options.emptyText);
    }
    await settle(fixture);
    return fixture;
  }

  const slots = (fixture: ComponentFixture<unknown>) => Array.from(root(fixture).querySelectorAll<HTMLElement>('.slot'));
  /** Each tooltip row as [value, label]; CSS, not whitespace, separates them on screen. */
  const tipRows = (fixture: ComponentFixture<unknown>) =>
    Array.from(root(fixture).querySelectorAll('.tip__row')).map((row) => [
      row.querySelector('.tip__value')!.textContent!.trim(),
      row.querySelector('.tip__label')!.textContent!.trim(),
    ]);
  const tableRows = (fixture: ComponentFixture<unknown>) =>
    Array.from(root(fixture).querySelectorAll('.chart__table tbody tr')).map((row) =>
      Array.from(row.children).map((cell) => cell.textContent!.trim()),
    );
  const hover = (slot: HTMLElement) => slot.dispatchEvent(new MouseEvent('pointermove', { bubbles: true, clientX: 50 }));

  describe('columns', () => {
    it('draws one slot per period with a segment for every series that has claims', async () => {
      const fixture = await render(WEEKS);

      expect(slots(fixture).length).toBe(4);
      expect(slots(fixture).map((slot) => slot.querySelectorAll('.seg').length)).toEqual([2, 2, 0, 2]);
    });

    it('sizes each segment as a share of the y axis, which tops out at the first clean tick above the tallest column', async () => {
      const fixture = await render(WEEKS);
      // The tallest column is 20 claims, so the axis runs 0 to 20 and 6 claims are 30% of the height.
      const heights = Array.from(slots(fixture)[0].querySelectorAll<HTMLElement>('.seg')).map((seg) => seg.style.height);

      expect(heights).toEqual(['30%', '70%']);
    });

    it('stacks the first series at the bottom and rounds only the top of the column', async () => {
      const fixture = await render(WEEKS);
      const segments = Array.from(slots(fixture)[0].querySelectorAll<HTMLElement>('.seg'));

      expect(segments.map((seg) => seg.style.background)).toEqual(['rgb(42, 120, 214)', 'rgb(137, 135, 129)']);
      expect(segments.map((seg) => seg.classList.contains('seg--end'))).toEqual([false, true]);
    });

    it('rounds the only segment of a single-series column', async () => {
      const fixture = await render([{ label: 'a', title: 'A', values: { pending: 5 } }]);

      expect(Array.from(root(fixture).querySelectorAll('.seg')).map((seg) => seg.classList.contains('seg--end'))).toEqual([true]);
    });
  });

  describe('axes', () => {
    it('starts the y axis at zero with compact ticks, and a hairline gridline above every tick but zero', async () => {
      const fixture = await render(WEEKS);

      expect(texts(fixture, '.ytick')).toEqual(['0', '5', '10', '15', '20']);
      expect(root(fixture).querySelectorAll('.gridline').length).toBe(4);
    });

    it('writes thousands compactly', async () => {
      const fixture = await render([{ label: 'a', title: 'A', values: { pending: 1000, decided: 500 } }]);

      expect(texts(fixture, '.ytick')).toEqual(['0', '500', '1K', '1.5K']);
    });

    it('labels every period while there is room', async () => {
      const fixture = await render(WEEKS);

      expect(texts(fixture, '.xlabel')).toEqual(['28 Sep', '5 Oct', '12 Oct', '19 Oct']);
    });

    it('thins the labels when the columns are crowded, so they never touch', async () => {
      const crowded = Array.from({ length: 40 }, (_, index) => ({
        label: `w${index}`,
        title: `Week ${index}`,
        values: { pending: index % 7, decided: 1 },
      }));
      const fixture = await render(crowded);

      // 40 columns in a 640px plot leave 16px each; a label needs 64px, so every fourth one is shown.
      const shown = texts(fixture, '.xlabel');
      expect(shown.length).toBe(10);
      expect(shown.slice(0, 3)).toEqual(['w0', 'w4', 'w8']);
      // Every slot is still there, and still describes itself.
      expect(slots(fixture).length).toBe(40);
    });
  });

  describe('legend', () => {
    it('shows a swatch for each series', async () => {
      const fixture = await render(WEEKS);

      expect(texts(fixture, '.legend__item')).toEqual(['Pending', 'Decided']);
    });
  });

  describe('tooltip', () => {
    it('lists every series and the total for the slot, on hover', async () => {
      const fixture = await render(WEEKS);

      hover(slots(fixture)[1]);
      await settle(fixture);

      expect(texts(fixture, '.tip__title')).toEqual(['Week of 5 Oct 2026']);
      expect(tipRows(fixture)).toEqual([['9', 'Pending'], ['8', 'Decided']]);
      expect(texts(fixture, '.tip__total')).toEqual(['Total 17']);
    });

    it('works on an empty period too, since the whole slot is the target', async () => {
      const fixture = await render(WEEKS);

      hover(slots(fixture)[2]);
      await settle(fixture);

      expect(tipRows(fixture)).toEqual([['0', 'Pending'], ['0', 'Decided']]);
      expect(texts(fixture, '.tip__total')).toEqual(['Total 0']);
    });

    it('shows the same on keyboard focus and hides on blur or when the pointer leaves', async () => {
      const fixture = await render(WEEKS);
      const [first, second] = slots(fixture);

      first.dispatchEvent(new FocusEvent('focus'));
      await settle(fixture);
      expect(texts(fixture, '.tip__title')).toEqual(['Week of 28 Sep 2026']);
      first.dispatchEvent(new FocusEvent('blur'));
      await settle(fixture);
      expect(root(fixture).querySelector('.tip')).toBeNull();

      hover(second);
      await settle(fixture);
      expect(root(fixture).querySelector('.tip')).not.toBeNull();
      second.dispatchEvent(new MouseEvent('pointerleave'));
      await settle(fixture);
      expect(root(fixture).querySelector('.tip')).toBeNull();
    });

    it('makes every slot a focusable image that describes its numbers', async () => {
      const fixture = await render(WEEKS);

      expect(slots(fixture).map((slot) => [slot.getAttribute('role'), slot.tabIndex])).toEqual([
        ['img', 0],
        ['img', 0],
        ['img', 0],
        ['img', 0],
      ]);
      expect(slots(fixture)[0].getAttribute('aria-label')).toBe('Week of 28 Sep 2026: Pending 6, Decided 14; total 20');
    });
  });

  describe('table twin', () => {
    it('is closed until asked for, then lists every period and number', async () => {
      const fixture = await render(WEEKS);
      expect(root(fixture).querySelector('.chart__table')).toBeNull();

      root(fixture).querySelector<HTMLButtonElement>('.chart__toggle')!.click();
      await settle(fixture);

      expect(texts(fixture, '.chart__table thead th')).toEqual(['Period', 'Pending', 'Decided', 'Total']);
      expect(tableRows(fixture)).toEqual([
        ['Week of 28 Sep 2026', '6', '14', '20'],
        ['Week of 5 Oct 2026', '9', '8', '17'],
        ['Week of 12 Oct 2026', '0', '0', '0'],
        ['Week of 19 Oct 2026', '12', '2', '14'],
      ]);
    });
  });

  it('says so when there is nothing to plot, without axes or a table', async () => {
    const fixture = await render([], { emptyText: 'No claims in this selection.' });

    expect(texts(fixture, '.chart__empty')).toEqual(['No claims in this selection.']);
    expect(root(fixture).querySelector('.plot')).toBeNull();
    expect(root(fixture).querySelector('.chart__toggle')).toBeNull();
  });
});
