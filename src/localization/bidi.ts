const FIRST_STRONG_ISOLATE = '\u2068';
const POP_DIRECTIONAL_ISOLATE = '\u2069';
const DIRECTIONAL_CONTROLS = /[\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]/gu;

/** Isolates private display text when it is embedded in localized prose. */
export const bidiIsolate = (value: string): string =>
  `${FIRST_STRONG_ISOLATE}${value.replace(
    DIRECTIONAL_CONTROLS,
    '',
  )}${POP_DIRECTIONAL_ISOLATE}`;
