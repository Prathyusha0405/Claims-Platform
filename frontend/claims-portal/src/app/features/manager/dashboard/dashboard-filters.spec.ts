import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDateRangeInputHarness } from '@angular/material/datepicker/testing';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatSelectHarness } from '@angular/material/select/testing';

import { DashboardFilters } from '@shared/models/dashboard';
import { root, settle } from '@testing/dom-testing';
import { DashboardFilterBar } from './dashboard-filters';

describe('DashboardFilterBar', () => {
  let fixture: ComponentFixture<DashboardFilterBar>;
  let emitted: DashboardFilters[];

  beforeEach(async () => {
    // Thursday 22 October 2026. Only the clock's date is faked, so Material's async work still runs.
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 22, 15, 30));
    fixture = TestBed.createComponent(DashboardFilterBar);
    emitted = [];
    fixture.componentInstance.filtersChange.subscribe((filters) => emitted.push(filters));
    await settle(fixture);
  });

  afterEach(() => vi.useRealTimers());

  const loader = () => TestbedHarnessEnvironment.loader(fixture);

  async function select(label: string): Promise<MatSelectHarness> {
    const field = await loader().getHarness(MatFormFieldHarness.with({ floatingLabelText: label }));
    return (await field.getControl(MatSelectHarness))!;
  }

  async function choose(label: string, option: string): Promise<void> {
    const control = await select(label);
    await control.open();
    await control.clickOptions({ text: option });
    await settle(fixture);
  }

  const clearButton = () => loader().getHarnessOrNull(MatButtonHarness.with({ text: /Clear filters/ }));
  const last = () => emitted[emitted.length - 1];

  it('says nothing and offers no "Clear filters" until something is filtered', async () => {
    expect(emitted).toEqual([]);
    expect(await clearButton()).toBeNull();
  });

  it('offers every market, every period and all six claim types, each with an "all" or default choice', async () => {
    const options = async (label: string) => {
      const control = await select(label);
      await control.open();
      const texts = await Promise.all((await control.getOptions()).map((option) => option.getText()));
      await control.close();
      return texts;
    };

    expect(await options('Market')).toEqual(['All markets', 'Australia', 'Hong Kong', 'Japan', 'Malaysia', 'New Zealand', 'Singapore']);
    expect(await options('Period')).toEqual(['All time', 'Last 30 days', 'Last 90 days', 'Year to date', 'Custom range']);
    expect(await options('Claim type')).toEqual(['All types', 'Auto', 'Home', 'Health', 'Travel', 'Personal accident', 'Other']);
  });

  it('reports the market, and nothing else, when only a market is chosen', async () => {
    await choose('Market', 'Japan');

    expect(last()).toEqual({ market: 'JP' });
    expect(await clearButton()).not.toBeNull();
  });

  it('reports the claim type', async () => {
    await choose('Claim type', 'Personal accident');

    expect(last()).toEqual({ claimType: 'PERSONAL_ACCIDENT' });
  });

  it.each([
    ['Last 30 days', { fromDate: '2026-09-23', toDate: '2026-10-22' }],
    ['Last 90 days', { fromDate: '2026-07-25', toDate: '2026-10-22' }],
    ['Year to date', { fromDate: '2026-01-01', toDate: '2026-10-22' }],
  ])('turns "%s" into dates from today', async (period, dates) => {
    await choose('Period', period);

    expect(last()).toEqual(dates);
  });

  it('opens the period back up with "All time"', async () => {
    await choose('Period', 'Last 90 days');
    await choose('Period', 'All time');

    expect(last()).toEqual({});
    expect(await clearButton()).toBeNull();
  });

  it('combines the market, the claim type and the period in one report', async () => {
    await choose('Market', 'Singapore');
    await choose('Claim type', 'Home');
    await choose('Period', 'Last 90 days');

    expect(last()).toEqual({ market: 'SG', claimType: 'HOME', fromDate: '2026-07-25', toDate: '2026-10-22' });
    expect(emitted.length).toBe(3);
  });

  describe('custom range', () => {
    it('shows the two dates only once "Custom range" is chosen', async () => {
      expect(root(fixture).querySelector('mat-date-range-input')).toBeNull();

      await choose('Period', 'Custom range');
      expect(root(fixture).querySelector('mat-date-range-input')).not.toBeNull();

      await choose('Period', 'Last 30 days');
      expect(root(fixture).querySelector('mat-date-range-input')).toBeNull();
    });

    it('is open on both ends until a date is entered, so choosing it alone filters nothing', async () => {
      await choose('Period', 'Custom range');

      expect(emitted).toEqual([]);
      expect(await clearButton()).toBeNull();
    });

    it('reports the dates entered, and either end may stay open', async () => {
      await choose('Period', 'Custom range');
      const range = await loader().getHarness(MatDateRangeInputHarness);

      await (await range.getStartInput()).setValue('7/1/2026');
      await settle(fixture);
      expect(last()).toEqual({ fromDate: '2026-07-01' });

      await (await range.getEndInput()).setValue('7/31/2026');
      await settle(fixture);
      expect(last()).toEqual({ fromDate: '2026-07-01', toDate: '2026-07-31' });
    });

    it('refuses a range that ends before it starts: nothing is reported and the manager is told why', async () => {
      await choose('Period', 'Custom range');
      const range = await loader().getHarness(MatDateRangeInputHarness);
      await (await range.getStartInput()).setValue('7/31/2026');
      await settle(fixture);
      const reported = emitted.length;

      await (await range.getEndInput()).setValue('7/1/2026');
      await settle(fixture);

      expect(emitted.length).toBe(reported);
      expect(root(fixture).querySelector('[role=alert]')?.textContent).toContain('end date is before the start date');

      await (await range.getEndInput()).setValue('8/15/2026');
      await settle(fixture);
      expect(last()).toEqual({ fromDate: '2026-07-31', toDate: '2026-08-15' });
      expect(root(fixture).querySelector('[role=alert]')).toBeNull();
    });

    it('stops sending the dates when a preset replaces the custom range', async () => {
      await choose('Period', 'Custom range');
      const range = await loader().getHarness(MatDateRangeInputHarness);
      await (await range.getStartInput()).setValue('7/1/2026');
      await settle(fixture);

      await choose('Period', 'All time');

      expect(last()).toEqual({});
    });
  });

  describe('Clear filters', () => {
    it('opens every filter back up, reports it, and goes away', async () => {
      await choose('Market', 'Japan');
      await choose('Claim type', 'Auto');
      await choose('Period', 'Last 30 days');

      await (await clearButton())!.click();
      await settle(fixture);

      expect(last()).toEqual({});
      expect(await clearButton()).toBeNull();
      expect(await (await select('Market')).getValueText()).toBe('All markets');
      expect(await (await select('Period')).getValueText()).toBe('All time');
      expect(await (await select('Claim type')).getValueText()).toBe('All types');
    });
  });
});
