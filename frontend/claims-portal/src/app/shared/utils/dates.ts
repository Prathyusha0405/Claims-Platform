/**
 * `yyyy-MM-dd` for a date picked in the browser, from the date as the user sees it. `toISOString()` would convert to
 * UTC first and, east of Greenwich (this is an APAC product), turn "1 November" into "31 October".
 */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Midnight at the start of today in the browser's time zone. */
export function startOfToday(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}
