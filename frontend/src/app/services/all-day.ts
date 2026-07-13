/*
 * All-day events reuse the backend's existing date fields instead of a dedicated flag:
 * they are stored exactly the way FullCalendar represents them natively — starting at
 * midnight and ending at midnight of the day AFTER the last day (exclusive end).
 * These helpers detect and build that shape so the backend entity stays untouched.
 */

const MIDNIGHT = /^00:00(:00(\.0+)?)?$/;

/** True when an ISO local date-time string (or date-only string) sits exactly on midnight. */
function isMidnight(value: string): boolean {
  const time = value.split('T')[1];
  return time === undefined || MIDNIGHT.test(time);
}

/**
 * True when the given range encodes an all-day event: both ends on midnight and the
 * (exclusive) end on a later day than the start.
 */
export function isAllDayRange(start?: string | null, end?: string | null): boolean {
  if (!start || !end) return false;
  return isMidnight(start) && isMidnight(end) && end.substring(0, 10) > start.substring(0, 10);
}

/** Shifts an ISO day ("YYYY-MM-DD") by the given number of days. */
export function addDays(day: string, days: number): string {
  // Noon instead of midnight keeps the date stable across DST transitions.
  const date = new Date(`${day.substring(0, 10)}T12:00:00`);
  date.setDate(date.getDate() + days);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const dayOfMonth = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${dayOfMonth}`;
}
