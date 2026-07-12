export type FixturePersonStatus = 'ready' | 'attention' | 'excluded';

export type FixturePerson = {
  id: string;
  name: string;
  givenName: string;
  initials: string;
  birthday: string;
  maskedPhone?: string;
  status: FixturePersonStatus;
};

export const fixturePeople: readonly FixturePerson[] = [
  {
    id: 'person-asha',
    name: 'Asha Mehta',
    givenName: 'Asha',
    initials: 'AM',
    birthday: '2026-07-14',
    maskedPhone: '+91 •••• 1204',
    status: 'ready',
  },
  {
    id: 'person-kabir',
    name: 'Kabir Rao',
    givenName: 'Kabir',
    initials: 'KR',
    birthday: '2026-07-16',
    maskedPhone: '+91 •••• 7782',
    status: 'ready',
  },
  {
    id: 'person-mina',
    name: 'Mina Sen',
    givenName: 'Mina',
    initials: 'MS',
    birthday: '2026-07-20',
    status: 'attention',
  },
  {
    id: 'person-dev',
    name: 'Dev Kapoor',
    givenName: 'Dev',
    initials: 'DK',
    birthday: '2026-08-02',
    maskedPhone: '+91 •••• 4410',
    status: 'excluded',
  },
] as const;

export const fixtureMessageFor = (givenName: string) =>
  `Happy birthday, ${givenName}! Wishing you a wonderful day.`;

export type FixtureActivity = {
  id: string;
  kind:
    | 'android-submitted'
    | 'android-sent'
    | 'delivery-unknown'
    | 'ios-opened'
    | 'ios-reported';
  timestamp: string;
};

export const androidFixtureActivity: readonly FixtureActivity[] = [
  {
    id: 'activity-a1',
    kind: 'android-submitted',
    timestamp: '09:02',
  },
  { id: 'activity-a2', kind: 'android-sent', timestamp: '09:03' },
  { id: 'activity-a3', kind: 'delivery-unknown', timestamp: '09:18' },
] as const;

export const iosFixtureActivity: readonly FixtureActivity[] = [
  { id: 'activity-i1', kind: 'ios-opened', timestamp: '09:02' },
  { id: 'activity-i2', kind: 'ios-reported', timestamp: '09:04' },
] as const;
