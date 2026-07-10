export type Instant = string & { readonly __instantBrand: unique symbol };

export type LocalDate = string & { readonly __localDateBrand: unique symbol };

export type CommandEntityKind =
  'activity' | 'backup' | 'blackout' | 'contact' | 'event' | 'gift' | 'memory' | 'message' | 'reminder';

export type ClockReading = {
  instant: Instant;
  localDate: LocalDate;
};

/**
 * Returns an atomic wall-clock reading. `instant` is used for audit/delivery
 * timestamps while `localDate` is used for calendar-year semantics.
 */
export interface Clock {
  read(): ClockReading;
}

export interface IdGenerator {
  nextId(kind: CommandEntityKind): string;
}

export type CommandDependencies = {
  clock: Clock;
  idGenerator: IdGenerator;
};

export type CommandIdAllocations = Readonly<Record<CommandEntityKind, readonly string[]>>;

export type CommandMetadata = {
  occurredAt: Instant;
  localDate: LocalDate;
  ids: CommandIdAllocations;
};

export type CommandIdCounts = Partial<Record<CommandEntityKind, number>>;

const ISO_INSTANT_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/;
const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export const instant = (value: string): Instant => {
  if (!ISO_INSTANT_PATTERN.test(value) || Number.isNaN(new Date(value).getTime())) {
    throw new TypeError(`Invalid Instant: ${value}`);
  }
  return value as Instant;
};

export const localDate = (value: string): LocalDate => {
  if (!LOCAL_DATE_PATTERN.test(value)) {
    throw new TypeError(`Invalid LocalDate: ${value}`);
  }
  const [year, month, day] = value.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (parsed.getUTCFullYear() !== year || parsed.getUTCMonth() !== month - 1 || parsed.getUTCDate() !== day) {
    throw new TypeError(`Invalid LocalDate: ${value}`);
  }
  return value as LocalDate;
};

const padCalendarPart = (value: number) => String(value).padStart(2, '0');

const localDateFor = (value: Date): LocalDate =>
  localDate(`${value.getFullYear()}-${padCalendarPart(value.getMonth() + 1)}-${padCalendarPart(value.getDate())}`);

export const systemClock: Clock = {
  read: () => {
    const value = new Date();
    return {
      instant: instant(value.toISOString()),
      localDate: localDateFor(value)
    };
  }
};

export const createFixedClock = (instantValue: string, localDateValue?: string): Clock => {
  const fixedInstant = instant(instantValue);
  const fixedLocalDate = localDate(localDateValue ?? instantValue.slice(0, 10));
  return {
    read: () => ({ instant: fixedInstant, localDate: fixedLocalDate })
  };
};

let fallbackSequence = 0;

const randomUuid = (): string => {
  const cryptoApi = globalThis.crypto;
  if (typeof cryptoApi?.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }

  if (typeof cryptoApi?.getRandomValues === 'function') {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map(value => value.toString(16).padStart(2, '0'));
    return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex
      .slice(6, 8)
      .join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10).join('')}`;
  }

  // Some React Native runtimes do not expose Web Crypto. Combining a
  // monotonic process sequence, wall time, and 106 pseudo-random bits keeps
  // this compatibility path collision resistant without timestamp-only IDs.
  fallbackSequence = (fallbackSequence + 1) % Number.MAX_SAFE_INTEGER;
  const entropy = Array.from({ length: 2 }, () =>
    Math.floor(Math.random() * Number.MAX_SAFE_INTEGER).toString(36)
  ).join('');
  return `${Date.now().toString(36)}-${fallbackSequence.toString(36)}-${entropy}`;
};

export const createCollisionResistantIdGenerator = (): IdGenerator => ({
  nextId: kind => `${kind}-${randomUuid()}`
});

export const systemCommandDependencies: CommandDependencies = {
  clock: systemClock,
  idGenerator: createCollisionResistantIdGenerator()
};

const entityKinds: readonly CommandEntityKind[] = [
  'activity',
  'backup',
  'blackout',
  'contact',
  'event',
  'gift',
  'memory',
  'message',
  'reminder'
];

export const allocateCommandMetadata = (
  dependencies: CommandDependencies,
  counts: CommandIdCounts,
  occurredAtOverride?: string
): CommandMetadata => {
  const reading = dependencies.clock.read();
  const ids = Object.fromEntries(
    entityKinds.map(kind => [
      kind,
      Array.from({ length: Math.max(0, Math.trunc(counts[kind] ?? 0)) }, () => dependencies.idGenerator.nextId(kind))
    ])
  ) as Record<CommandEntityKind, string[]>;

  return {
    occurredAt: occurredAtOverride ? instant(occurredAtOverride) : reading.instant,
    localDate: reading.localDate,
    ids
  };
};

export const commandId = (metadata: CommandMetadata, kind: CommandEntityKind, index = 0): string => {
  const value = metadata.ids[kind][index];
  if (!value) {
    throw new RangeError(`Missing ${kind} ID allocation at index ${index}.`);
  }
  return value;
};

export const localCalendarYear = (value: LocalDate): number => Number(value.slice(0, 4));
