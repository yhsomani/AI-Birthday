import type { PeoplePort } from '../../application/ports/PeoplePort';
import type { ContactSummary, PeopleQuery } from '../../domain/contacts/model';
import type {
  NativeRevision,
  PageCursor,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type {
  NativeProblem,
  NativeResult,
  ProjectionEnvelope,
} from '../../domain/shared/result';
import { nativeBridgeProblem } from './nativeProblem';

export const PEOPLE_PAGE_SIZE = 50;
export const PEOPLE_REVIEW_BATCH_SIZE = 50;

// Native People storage is release-gated at 10,000 contacts. Keeping the UI
// scanner on the same explicit ceiling bounds requests, retained IDs, and
// contact projections even if a future native contract is malformed.
const PEOPLE_SCAN_CONTACT_LIMIT = 10_000;
const PEOPLE_SCAN_PAGE_LIMIT = PEOPLE_SCAN_CONTACT_LIMIT / PEOPLE_PAGE_SIZE;

const peoplePaginationContractProblem: NativeProblem = {
  kind: 'internal',
  supportCode: 'NATIVE_CONTRACT_INVALID' as SafeSupportCode,
};

export type PeopleScanQuery = Omit<PeopleQuery, 'cursor' | 'pageSize'>;

export type PeopleScan = Readonly<{
  contactIds: readonly ContactSummary['id'][];
  scannedCount: number;
  totalCount: number;
}>;

type PeopleListPort = Pick<PeoplePort, 'listPeople'>;

export const isReadyOffContact = (contact: ContactSummary): boolean =>
  contact.readiness.kind === 'ready' && contact.enrollment.kind === 'off';

export const needsBatchApproval = (contact: ContactSummary): boolean =>
  (contact.enrollment.kind === 'enabled' ||
    contact.enrollment.kind === 'paused') &&
  contact.enrollment.approval.kind !== 'valid';

const staleRevision = (latestRevision: NativeRevision): NativeProblem => ({
  kind: 'stale-revision',
  latestRevision,
});

/**
 * Reads one stable, bounded People result set and retains only contacts needed
 * by the caller. No partial result is returned: a later-page failure, revision
 * change, duplicate ID, cursor loop, or incomplete final page fails closed.
 */
export const scanPeoplePages = async (
  port: PeopleListPort,
  query: PeopleScanQuery,
  include: (contact: ContactSummary) => boolean,
): Promise<NativeResult<PeopleScan>> => {
  let cursor: PageCursor | undefined;
  let firstEnvelope:
    | ProjectionEnvelope<import('../../domain/contacts/model').PeoplePage>
    | undefined;
  let expectedRevision: NativeRevision | undefined;
  let expectedTotal: number | undefined;
  const seenCursors = new Set<PageCursor>();
  const seenContactIds = new Set<ContactSummary['id']>();
  const selectedContactIds: ContactSummary['id'][] = [];

  for (let pageIndex = 0; pageIndex < PEOPLE_SCAN_PAGE_LIMIT; pageIndex += 1) {
    let page: Awaited<ReturnType<PeopleListPort['listPeople']>>;
    try {
      page = await port.listPeople({
        ...query,
        pageSize: PEOPLE_PAGE_SIZE,
        ...(cursor === undefined ? {} : { cursor }),
      });
    } catch {
      return { kind: 'error', problem: nativeBridgeProblem };
    }
    if (page.kind === 'error') return page;

    if (
      expectedRevision !== undefined &&
      page.envelope.revision !== expectedRevision
    ) {
      return {
        kind: 'error',
        problem: staleRevision(page.envelope.revision),
      };
    }
    if (
      expectedTotal !== undefined &&
      page.envelope.value.totalCount !== expectedTotal
    ) {
      return { kind: 'error', problem: peoplePaginationContractProblem };
    }

    firstEnvelope ??= page.envelope;
    expectedRevision = page.envelope.revision;
    expectedTotal = page.envelope.value.totalCount;
    if (
      expectedTotal > PEOPLE_SCAN_CONTACT_LIMIT ||
      page.envelope.value.items.length > PEOPLE_PAGE_SIZE
    ) {
      return { kind: 'error', problem: peoplePaginationContractProblem };
    }

    for (const contact of page.envelope.value.items) {
      if (seenContactIds.has(contact.id)) {
        return { kind: 'error', problem: peoplePaginationContractProblem };
      }
      seenContactIds.add(contact.id);
      if (include(contact)) selectedContactIds.push(contact.id);
    }
    if (seenContactIds.size > expectedTotal) {
      return { kind: 'error', problem: peoplePaginationContractProblem };
    }

    const nextCursor = page.envelope.value.nextCursor;
    if (nextCursor === undefined) {
      if (seenContactIds.size !== expectedTotal) {
        return { kind: 'error', problem: peoplePaginationContractProblem };
      }
      return {
        kind: 'ok',
        envelope: {
          ...firstEnvelope,
          value: {
            contactIds: selectedContactIds,
            scannedCount: seenContactIds.size,
            totalCount: expectedTotal,
          },
        },
      };
    }
    if (seenContactIds.size >= expectedTotal || seenCursors.has(nextCursor)) {
      return { kind: 'error', problem: peoplePaginationContractProblem };
    }
    seenCursors.add(nextCursor);
    cursor = nextCursor;
  }

  return { kind: 'error', problem: peoplePaginationContractProblem };
};
