import { ComponentFixture, TestBed } from '@angular/core/testing';

import { root, settle, texts } from '@testing/dom-testing';
import { ChartSeries } from '../chart-series';
import { StackedBarChart, StackedMode, StackedRow } from './stacked-bar-chart';

const PENDING_DECIDED: ChartSeries[] = [
  { key: 'pending', label: 'Pending', color: '#2a78d6' },
  { key: 'decided', label: 'Decided', color: '#898781', textColor: '#0b0b0b' },
];

const MARKETS: StackedRow[] = [
  { label: 'Singapore', values: { pending: 34, decided: 16 } },
  { label: 'Japan', values: { pending: 10, decided: 5 } },
];

describe('StackedBarChart', () => {
  async function render(
    rows: StackedRow[],
    options: { series?: ChartSeries[]; mode?: StackedMode; emptyText?: string } = {},
  ): Promise<ComponentFixture<StackedBarChart>> {
    const fixture = TestBed.createComponent(StackedBarChart);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('series', options.series ?? PENDING_DECIDED);
    fixture.componentRef.setInput('caption', 'Claims by market');
    fixture.componentRef.setInput('labelHeading', 'Market');
    fixture.componentRef.setInput('totalHeading', 'Claims');
    if (options.mode) {
      fixture.componentRef.setInput('mode', options.mode);
    }
    if (options.emptyText) {
      fixture.componentRef.setInput('emptyText', options.emptyText);
    }
    await settle(fixture);
    return fixture;
  }

  const rowElements = (fixture: ComponentFixture<unknown>) => Array.from(root(fixture).querySelectorAll<HTMLElement>('.bar'));
  /** Each tooltip row as [value, label]; CSS, not whitespace, separates them on screen. */
  const tipRows = (fixture: ComponentFixture<unknown>) =>
    Array.from(root(fixture).querySelectorAll('.tip__row')).map((row) => [
      row.querySelector('.tip__value')!.textContent!.trim(),
      row.querySelector('.tip__label')!.textContent!.replace(/\s+/g, ' ').trim(),
    ]);
  const tableRows = (fixture: ComponentFixture<unknown>) =>
    Array.from(root(fixture).querySelectorAll('.chart__table tbody tr')).map((row) =>
      Array.from(row.children).map((cell) => cell.textContent!.replace(/\s+/g, ' ').trim()),
    );
  const hover = (row: HTMLElement) => row.dispatchEvent(new MouseEvent('pointermove', { bubbles: true, clientX: 30 }));

  describe('absolute mode', () => {
    it('draws one row per category with the total at the tip of its bar', async () => {
      const fixture = await render(MARKETS);

      expect(texts(fixture, '.bar__label')).toEqual(['Singapore', 'Japan']);
      expect(texts(fixture, '.bar__value')).toEqual(['50', '15']);
    });

    it('sizes each bar against the longest row and each segment by its share of its own bar', async () => {
      const fixture = await render(MARKETS);
      const stacks = Array.from(root(fixture).querySelectorAll<HTMLElement>('.bar__stack'));

      // The longest row takes all the room that is left for bars; the shorter one a proportional part of it.
      expect(stacks[0].style.width).toContain('* 1)');
      expect(stacks[1].style.width).toContain('* 0.3)');
      const first = Array.from(stacks[0].querySelectorAll<HTMLElement>('.seg')).map((seg) => seg.style.width);
      expect(first).toEqual(['68%', '32%']);
    });

    it('rounds only the outer end of the last segment and puts the series colour on the fill', async () => {
      const fixture = await render(MARKETS);
      const segments = Array.from(rowElements(fixture)[0].querySelectorAll<HTMLElement>('.seg'));

      expect(segments.map((seg) => seg.classList.contains('seg--end'))).toEqual([false, true]);
      expect(segments.map((seg) => seg.style.background)).toEqual(['rgb(42, 120, 214)', 'rgb(137, 135, 129)']);
    });

    it('writes a value inside a segment only where it fits, and never skips the tooltip or table', async () => {
      const fixture = await render([
        { label: 'Big', values: { pending: 500, decided: 3 } },
        { label: 'Small', values: { pending: 1, decided: 1 } },
      ]);

      // 500 of 503 has room for its number; 3 of 503 and the two 1s are far too narrow to hold one.
      expect(texts(fixture, '.seg__text')).toEqual(['500']);
      expect(texts(fixture, '.bar__value')).toEqual(['503', '2']);
    });

    it('draws no segment for a zero and treats a missing series as zero', async () => {
      const fixture = await render([{ label: 'Quiet', values: { pending: 4 } }]);

      expect(root(fixture).querySelectorAll('.seg').length).toBe(1);
      expect(texts(fixture, '.bar__value')).toEqual(['4']);
    });

    it('writes white on a blue fill and near-black on the gray one, never the series colour', async () => {
      const fixture = await render(MARKETS);
      const labels = Array.from(rowElements(fixture)[0].querySelectorAll<HTMLElement>('.seg__text'));

      expect(labels.map((label) => label.style.color)).toEqual(['rgb(255, 255, 255)', 'rgb(11, 11, 11)']);
    });
  });

  describe('percent mode', () => {
    const TYPES: ChartSeries[] = [
      { key: 'AUTO', label: 'Auto', color: '#2a78d6' },
      { key: 'HOME', label: 'Home', color: '#eb6834' },
    ];
    const ROWS: StackedRow[] = [
      { label: 'Claims', values: { AUTO: 146, HOME: 104 } },
      { label: 'Liability exposure', values: { AUTO: 1_500_123, HOME: 1_174_181 }, format: (value) => `USD ${Math.round(value).toLocaleString('en-US')}` },
    ];

    it('fills every row to the full width and shows each series’ share inside its segment', async () => {
      const fixture = await render(ROWS, { series: TYPES, mode: 'percent' });
      const stacks = Array.from(root(fixture).querySelectorAll<HTMLElement>('.bar__stack'));

      expect(stacks.map((stack) => stack.style.width)).toEqual(['100%', '100%']);
      expect(texts(fixture, '.seg__text')).toEqual(['58%', '42%', '56%', '44%']);
      expect(root(fixture).querySelector('.bar__value')).toBeNull();
    });

    it('shows each row’s total under its label, in that row’s own unit', async () => {
      const fixture = await render(ROWS, { series: TYPES, mode: 'percent' });

      expect(texts(fixture, '.bar__label')).toEqual(['Claims 250', 'Liability exposure USD 2,674,304']);
    });

    it('calls a real but tiny share "<1%", never "0%"', async () => {
      const fixture = await render([{ label: 'Claims', values: { AUTO: 1, HOME: 1000 } }], { series: TYPES, mode: 'percent' });
      rowElements(fixture)[0].click();
      root(fixture).querySelector<HTMLButtonElement>('.chart__toggle')!.click();
      await settle(fixture);

      expect(texts(fixture, '.chart__table tbody td')).toEqual(['1 (<1%)', '1,000 (100%)', '1,001']);
    });
  });

  describe('legend', () => {
    it('names every series with a swatch when there are two or more', async () => {
      const fixture = await render(MARKETS);

      expect(texts(fixture, '.legend__item')).toEqual(['Pending', 'Decided']);
      const swatches = Array.from(root(fixture).querySelectorAll<HTMLElement>('.legend__swatch')).map((swatch) => swatch.style.background);
      expect(swatches).toEqual(['rgb(42, 120, 214)', 'rgb(137, 135, 129)']);
    });

    it('has no legend box for a single series: the heading above the chart names it', async () => {
      const fixture = await render([{ label: 'Singapore', values: { pending: 3 } }], { series: [PENDING_DECIDED[0]] });

      expect(root(fixture).querySelector('.legend')).toBeNull();
    });
  });

  describe('tooltip', () => {
    it('lists every series, value first, and the total, on hover', async () => {
      const fixture = await render(MARKETS);

      hover(rowElements(fixture)[0]);
      await settle(fixture);

      expect(texts(fixture, '.tip__title')).toEqual(['Singapore']);
      expect(tipRows(fixture)).toEqual([['34', 'Pending'], ['16', 'Decided']]);
      expect(texts(fixture, '.tip__total')).toEqual(['Total 50']);
    });

    it('also lists the shares in percent mode', async () => {
      const fixture = await render([{ label: 'Claims', values: { pending: 3, decided: 1 } }], { mode: 'percent' });

      hover(rowElements(fixture)[0]);
      await settle(fixture);

      expect(tipRows(fixture)).toEqual([['3', 'Pending · 75%'], ['1', 'Decided · 25%']]);
    });

    it('shows the same tooltip on keyboard focus, and hides it on blur or when the pointer leaves', async () => {
      const fixture = await render(MARKETS);
      const [first, second] = rowElements(fixture);

      first.dispatchEvent(new FocusEvent('focus'));
      await settle(fixture);
      expect(texts(fixture, '.tip__title')).toEqual(['Singapore']);

      first.dispatchEvent(new FocusEvent('blur'));
      await settle(fixture);
      expect(root(fixture).querySelector('.tip')).toBeNull();

      hover(second);
      await settle(fixture);
      expect(texts(fixture, '.tip__title')).toEqual(['Japan']);
      second.dispatchEvent(new MouseEvent('pointerleave'));
      await settle(fixture);
      expect(root(fixture).querySelector('.tip')).toBeNull();
    });

    it('makes every row a focusable image that describes all of its numbers', async () => {
      const fixture = await render(MARKETS);

      expect(rowElements(fixture).map((row) => [row.getAttribute('role'), row.tabIndex, row.getAttribute('aria-label')])).toEqual([
        ['img', 0, 'Singapore: Pending 34, Decided 16; total 50'],
        ['img', 0, 'Japan: Pending 10, Decided 5; total 15'],
      ]);
    });
  });

  describe('table twin', () => {
    it('is closed until asked for, then lists every number in the chart', async () => {
      const fixture = await render(MARKETS);
      expect(root(fixture).querySelector('.chart__table')).toBeNull();

      const toggle = root(fixture).querySelector<HTMLButtonElement>('.chart__toggle')!;
      expect(toggle.textContent).toContain('View as table');
      toggle.click();
      await settle(fixture);

      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      expect(toggle.textContent).toContain('Hide table');
      expect(texts(fixture, '.chart__table thead th')).toEqual(['Market', 'Pending', 'Decided', 'Claims']);
      expect(tableRows(fixture)).toEqual([['Singapore', '34', '16', '50'], ['Japan', '10', '5', '15']]);

      toggle.click();
      await settle(fixture);
      expect(root(fixture).querySelector('.chart__table')).toBeNull();
    });
  });

  it('says so when there is nothing to draw', async () => {
    const fixture = await render([], { emptyText: 'No claims in this selection.' });

    expect(texts(fixture, '.chart__empty')).toEqual(['No claims in this selection.']);
    expect(root(fixture).querySelector('.bar')).toBeNull();
    expect(root(fixture).querySelector('.chart__toggle')).toBeNull();
  });
});
