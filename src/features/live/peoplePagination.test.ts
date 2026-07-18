import type { PeoplePort } from '../../application/ports/PeoplePort';
import type { ContactSummary } from '../../domain/contacts/model';
import type {
  ContactId,
  NativeRevision,
  PageCursor,
  PrivateDisplayName,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { scanPeoplePages } from './peoplePagination';

const revision = (value: string) => value as NativeRevision;
const generatedAt = '2026-07-12T07:00:00Z' as UtcInstant;

const contact = (index: number): ContactSummary => ({
  id: `contact-${index}` as ContactId,
  displayName: `Person ${index}` as PrivateDisplayName,
  readiness: { kind: 'ready' },
  enrollment: { kind: 'off' },
});

const ok = <Value>(
  value: Value,
  currentRevision = revision('1'),
): NativeResult<Value> => ({
  kind: 'ok',
  envelope: {
    contractVersion: 1,
    revision: currentRevision,
    generatedAt,
    value,
  },
});

describe('scanPeoplePages', () => {
  it('traverses and retains matching contacts beyond the first 50', async () => {
    const contacts = Array.from({ length: 75 }, (_, index) =>
      contact(index + 1),
    );
    const listPeople = jest.fn(async ({ cursor }: { cursor?: PageCursor }) =>
      cursor === undefined
        ? ok({
            items: contacts.slice(0, 50),
            nextCursor: 'page-2' as PageCursor,
            totalCount: contacts.length,
          })
        : ok({ items: contacts.slice(50), totalCount: contacts.length }),
    );

    const result = await scanPeoplePages(
      { listPeople } as Pick<PeoplePort, 'listPeople'>,
      { filter: 'all' },
      person => person.enrollment.kind === 'off',
    );

    expect(result.kind).toBe('ok');
    if (result.kind !== 'ok') return;
    expect(result.envelope.value.contactIds).toHaveLength(75);
    expect(result.envelope.value.scannedCount).toBe(75);
    expect(result.envelope.value.contactIds.at(-1)).toBe('contact-75');
    expect(listPeople).toHaveBeenNthCalledWith(1, {
      filter: 'all',
      pageSize: 50,
    });
    expect(listPeople).toHaveBeenNthCalledWith(2, {
      cursor: 'page-2',
      filter: 'all',
      pageSize: 50,
    });
  });

  it('returns a later-page failure without exposing the first page as complete', async () => {
    const laterProblem = {
      kind: 'internal' as const,
      supportCode: 'LATER_PAGE_FAILED' as SafeSupportCode,
    };
    const listPeople = jest
      .fn()
      .mockResolvedValueOnce(
        ok({
          items: Array.from({ length: 50 }, (_, index) => contact(index + 1)),
          nextCursor: 'page-2' as PageCursor,
          totalCount: 75,
        }),
      )
      .mockResolvedValueOnce({ kind: 'error', problem: laterProblem });

    const result = await scanPeoplePages(
      { listPeople } as Pick<PeoplePort, 'listPeople'>,
      { filter: 'all' },
      () => true,
    );

    expect(result).toEqual({ kind: 'error', problem: laterProblem });
    expect(listPeople).toHaveBeenCalledTimes(2);
  });

  it('turns a revision change between pages into a stale-revision result', async () => {
    const listPeople = jest
      .fn()
      .mockResolvedValueOnce(
        ok({
          items: Array.from({ length: 50 }, (_, index) => contact(index + 1)),
          nextCursor: 'page-2' as PageCursor,
          totalCount: 51,
        }),
      )
      .mockResolvedValueOnce(
        ok({ items: [contact(51)], totalCount: 51 }, revision('2')),
      );

    await expect(
      scanPeoplePages(
        { listPeople } as Pick<PeoplePort, 'listPeople'>,
        { filter: 'all' },
        () => true,
      ),
    ).resolves.toEqual({
      kind: 'error',
      problem: { kind: 'stale-revision', latestRevision: '2' },
    });
  });

  it('accepts the exact 10,000-contact release ceiling without truncation', async () => {
    const totalCount = 10_000;
    const pageSize = 50;
    const listPeople = jest.fn(async ({ cursor }: { cursor?: PageCursor }) => {
      const pageIndex = cursor ? Number(cursor.replace('page-', '')) : 0;
      const start = pageIndex * pageSize;
      const items = Array.from({ length: pageSize }, (_, index) =>
        contact(start + index + 1),
      );
      const nextPage = pageIndex + 1;
      return ok({
        items,
        ...(nextPage * pageSize < totalCount
          ? { nextCursor: `page-${nextPage}` as PageCursor }
          : {}),
        totalCount,
      });
    });

    const result = await scanPeoplePages(
      { listPeople } as Pick<PeoplePort, 'listPeople'>,
      { filter: 'all' },
      () => true,
    );

    expect(result.kind).toBe('ok');
    if (result.kind !== 'ok') return;
    expect(result.envelope.value).toMatchObject({
      scannedCount: totalCount,
      totalCount,
    });
    expect(result.envelope.value.contactIds).toHaveLength(totalCount);
    expect(result.envelope.value.contactIds.at(-1)).toBe('contact-10000');
    expect(listPeople).toHaveBeenCalledTimes(200);
  });

  it('fails closed before scanning a result above the 10,000-contact ceiling', async () => {
    const listPeople = jest.fn(async () =>
      ok({
        items: Array.from({ length: 50 }, (_, index) => contact(index + 1)),
        nextCursor: 'page-1' as PageCursor,
        totalCount: 10_001,
      }),
    );

    const result = await scanPeoplePages(
      { listPeople } as Pick<PeoplePort, 'listPeople'>,
      { filter: 'all' },
      () => true,
    );

    expect(result).toEqual({
      kind: 'error',
      problem: {
        kind: 'internal',
        supportCode: 'NATIVE_CONTRACT_INVALID',
      },
    });
    expect(listPeople).toHaveBeenCalledTimes(1);
  });

  it('rejects a duplicate contact ID across pages', async () => {
    const firstPage = Array.from({ length: 50 }, (_, index) =>
      contact(index + 1),
    );
    const listPeople = jest
      .fn()
      .mockResolvedValueOnce(
        ok({
          items: firstPage,
          nextCursor: 'page-2' as PageCursor,
          totalCount: 51,
        }),
      )
      .mockResolvedValueOnce(ok({ items: [firstPage[0]], totalCount: 51 }));

    const result = await scanPeoplePages(
      { listPeople } as Pick<PeoplePort, 'listPeople'>,
      { filter: 'all' },
      () => true,
    );

    expect(result).toEqual({
      kind: 'error',
      problem: {
        kind: 'internal',
        supportCode: 'NATIVE_CONTRACT_INVALID',
      },
    });
  });
});
