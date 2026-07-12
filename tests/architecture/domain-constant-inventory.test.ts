import { LEAP_DAY_POLICIES } from '../../src/domain/birthdays/model';
import { LIFECYCLE_REPAIR_KINDS } from '../../src/domain/device/model';
import { PUBLIC_RESOURCE_KINDS } from '../../src/domain/legal/model';

describe('closed domain constant inventories', () => {
  it('keeps the reviewed leap-day choices closed and stable', () => {
    expect(LEAP_DAY_POLICIES).toEqual(['feb-28', 'mar-01', 'skip']);
  });

  it('keeps destructive lifecycle repairs explicit', () => {
    expect(LIFECYCLE_REPAIR_KINDS).toEqual([
      'disconnect-contacts',
      'revoke-google-access',
      'sign-out-wipe',
      'wipe-local-data',
    ]);
  });

  it('keeps public legal and account resources explicit', () => {
    expect(PUBLIC_RESOURCE_KINDS).toEqual([
      'privacy',
      'terms',
      'support',
      'delete-account',
    ]);
  });
});
