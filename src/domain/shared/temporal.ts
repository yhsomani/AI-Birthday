import type { Brand } from './brand';

export type UtcInstant = Brand<string, 'UtcInstant'>;
export type LocalDate = Brand<string, 'LocalDate'>;
export type LocalTime = Brand<string, 'LocalTime'>;
export type IanaTimeZone = Brand<string, 'IanaTimeZone'>;

const LOCAL_DATE_PATTERN = /^(\d{4})-(0[1-9]|1[0-2])-([0-2]\d|3[01])$/;
const LOCAL_TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;
const UTC_INSTANT_PATTERN =
  /^(\d{4})-(0[1-9]|1[0-2])-([0-2]\d|3[01])T([01]\d|2[0-3]):([0-5]\d):([0-5]\d)(?:\.\d{1,9})?Z$/;

const isRealUtcDate = (year: number, month: number, day: number): boolean => {
  const probe = new Date(0);
  probe.setUTCHours(0, 0, 0, 0);
  probe.setUTCFullYear(year, month - 1, day);
  return (
    probe.getUTCFullYear() === year &&
    probe.getUTCMonth() === month - 1 &&
    probe.getUTCDate() === day
  );
};

export const isLocalDate = (value: string): value is LocalDate => {
  const match = LOCAL_DATE_PATTERN.exec(value);
  if (match === null) {
    return false;
  }

  return isRealUtcDate(Number(match[1]), Number(match[2]), Number(match[3]));
};

export const isLocalTime = (value: string): value is LocalTime =>
  LOCAL_TIME_PATTERN.test(value);

export const isUtcInstant = (value: string): value is UtcInstant => {
  const match = UTC_INSTANT_PATTERN.exec(value);
  return (
    match !== null &&
    isRealUtcDate(Number(match[1]), Number(match[2]), Number(match[3])) &&
    Number.isFinite(Date.parse(value))
  );
};

export const localTimeToMinutes = (value: LocalTime): number => {
  const hours = Number(value.slice(0, 2));
  const minutes = Number(value.slice(3, 5));
  return hours * 60 + minutes;
};
