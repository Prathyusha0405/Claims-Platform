import { MoneyPipe } from '@shared/pipes/money-pipe';
import { formatMoney } from './money';

/** Intl puts a no-break space between the code and the number, which keeps them together on one line. */
const NBSP = ' ';

describe('formatMoney', () => {
  it('puts the ISO code in front and uses two decimals for most currencies', () => {
    expect(formatMoney(3840, 'SGD')).toBe(`SGD${NBSP}3,840.00`);
    expect(formatMoney(2674303.5, 'USD')).toBe(`USD${NBSP}2,674,303.50`);
    expect(formatMoney(0.5, 'HKD')).toBe(`HKD${NBSP}0.50`);
  });

  it('uses the currency’s own decimals: yen have none', () => {
    expect(formatMoney(506850, 'JPY')).toBe(`JPY${NBSP}506,850`);
    expect(formatMoney(506850.4, 'JPY')).toBe(`JPY${NBSP}506,850`);
  });

  it('can drop the decimals for charts and headline figures', () => {
    expect(formatMoney(2674303.5, 'USD', true)).toBe(`USD${NBSP}2,674,304`);
    expect(formatMoney(850718, 'USD', true)).toBe(`USD${NBSP}850,718`);
  });

  it('tolerates lower case and stray spaces in the code', () => {
    expect(formatMoney(10, ' sgd ')).toBe(`SGD${NBSP}10.00`);
  });

  it('shows the bare number when there is no usable currency, rather than guessing one', () => {
    expect(formatMoney(1234.5, null)).toBe('1,234.50');
    expect(formatMoney(1234.5, undefined)).toBe('1,234.50');
    expect(formatMoney(1234.5, '')).toBe('1,234.50');
    expect(formatMoney(1234.5, 'dollars')).toBe('1,234.50');
    expect(formatMoney(1234.5, '12', true)).toBe('1,235');
  });

  it('shows nothing when there is no amount', () => {
    expect(formatMoney(null, 'SGD')).toBe('');
    expect(formatMoney(undefined, 'SGD')).toBe('');
    expect(formatMoney(Number.NaN, 'SGD')).toBe('');
  });
});

describe('MoneyPipe', () => {
  it('formats like formatMoney, with an optional whole-units flag', () => {
    const pipe = new MoneyPipe();

    expect(pipe.transform(2500, 'SGD')).toBe(`SGD${NBSP}2,500.00`);
    expect(pipe.transform(2500.4, 'SGD', true)).toBe(`SGD${NBSP}2,500`);
    expect(pipe.transform(null, 'SGD')).toBe('');
  });
});
