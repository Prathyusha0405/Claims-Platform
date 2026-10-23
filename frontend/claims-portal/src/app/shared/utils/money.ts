const formatters = new Map<string, Intl.NumberFormat>();

/**
 * An amount with its ISO 4217 code in front, in the currency's own number of decimals: `SGD 3,840.00`, `JPY 506,850`.
 * The code, not a symbol, because the app now shows several currencies side by side and "$" is five of them.
 * Angular's CurrencyPipe cannot do this (its 'code' display glues the code to the number), hence this helper.
 *
 * `whole` drops the decimals, for charts and headline figures. A missing amount is an empty string; a missing or
 * malformed currency shows the bare number rather than guessing one.
 */
export function formatMoney(
  amount: number | null | undefined,
  currency: string | null | undefined,
  whole = false,
): string {
  if (amount === null || amount === undefined || !Number.isFinite(amount)) {
    return '';
  }
  return formatterFor((currency ?? '').trim().toUpperCase(), whole).format(amount);
}

function formatterFor(code: string, whole: boolean): Intl.NumberFormat {
  const key = `${code}|${whole}`;
  let formatter = formatters.get(key);
  if (formatter === undefined) {
    formatter = createFormatter(code, whole);
    formatters.set(key, formatter);
  }
  return formatter;
}

function createFormatter(code: string, whole: boolean): Intl.NumberFormat {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: code,
      currencyDisplay: 'code',
      ...(whole ? { minimumFractionDigits: 0, maximumFractionDigits: 0 } : {}),
    });
  } catch {
    // An empty or malformed code is a RangeError: fall back to the plain number.
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: whole ? 0 : 2,
      maximumFractionDigits: whole ? 0 : 2,
    });
  }
}
