import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';

import type {
  ContactReadiness,
  ContactSummary,
  EnrollmentReview,
  PeopleFilter,
  SyncProjection,
} from '../../domain/contacts/model';
import type {
  ContactId,
  NativeRevision,
  PageCursor,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  KeyValue,
  PersonRow,
  ReadinessBanner,
  Screen,
  SearchField,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import type { StatusTone } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { bidiIsolate } from '../../localization/bidi';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
  LiveActionFeedback,
} from './LiveProjectionState';
import { nativeBridgeProblem, staleRevisionProblem } from './nativeProblem';
import {
  isReadyOffContact,
  PEOPLE_PAGE_SIZE,
  PEOPLE_REVIEW_BATCH_SIZE,
  scanPeoplePages,
} from './peoplePagination';
import { useLiveProjection } from './useLiveProjection';

const initials = (name: string): string =>
  name
    .trim()
    .split(/\s+/u)
    .slice(0, 2)
    .map(part => part.charAt(0).toUpperCase())
    .join('') || '•';

const filters: readonly Readonly<{
  label: TranslationKey;
  value: PeopleFilter;
}>[] = [
  { label: 'live.people.filterAll', value: 'all' },
  { label: 'live.people.filterEnabled', value: 'enabled' },
  { label: 'live.people.filterReady', value: 'ready' },
  { label: 'live.people.filterAttention', value: 'needs-attention' },
  { label: 'live.people.filterExcluded', value: 'excluded' },
];

type EnrollmentReviewState = Readonly<{
  review: EnrollmentReview;
  revision: NativeRevision;
  sourceRevision: NativeRevision;
  queryKey: string;
  requestedIds: readonly ContactId[];
  remainingIds: readonly ContactId[];
  completedCount: number;
  totalCount: number;
}>;

type IncompleteEnrollment = Readonly<{
  completedCount: number;
  totalCount: number;
}>;

type JoinedPeopleTruth = Readonly<{
  usable: boolean;
  queryKey: string;
  revision?: NativeRevision | undefined;
}>;

type SyncNotice =
  | Readonly<{ kind: 'verified' }>
  | Readonly<{ kind: 'unverified' }>
  | Readonly<{ kind: 'projection'; projection: SyncProjection }>;

type SyncPresentation = Readonly<{
  title: TranslationKey;
  detail: TranslationKey;
  tone: StatusTone;
  reason?: string | undefined;
}>;

type ContactsActionKind = 'authorize' | 'sync';

const invalidEnrollmentReviewProblem: NativeProblem = {
  kind: 'internal',
  supportCode: 'INVALID_ENROLLMENT_REVIEW' as SafeSupportCode,
};

const peopleTruthUnavailableProblem: NativeProblem = {
  kind: 'internal',
  supportCode: 'PEOPLE_TRUTH_UNAVAILABLE' as SafeSupportCode,
};

const emptyMessageKey = (
  filter: PeopleFilter,
  hasSearch: boolean,
): TranslationKey => {
  if (hasSearch) return 'live.people.emptySearch';
  switch (filter) {
    case 'all':
      return 'live.people.emptyAll';
    case 'enabled':
      return 'live.people.emptyEnabled';
    case 'ready':
      return 'live.people.emptyReady';
    case 'needs-attention':
      return 'live.people.emptyNeedsAttention';
    case 'excluded':
      return 'live.people.emptyExcluded';
  }
};

export function LivePeopleScreen({
  onBack,
  onOpenPerson,
  port,
}: {
  onBack?: () => void;
  onOpenPerson: (contactId: ContactId) => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const [filter, setFilter] = useState<PeopleFilter>('all');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const normalizedSearch = debouncedSearch.trim();
  const queryKey = `${filter}\u0000${normalizedSearch}`;
  const queryKeyRef = useRef(queryKey);
  queryKeyRef.current = queryKey;
  const [pageState, setPageState] = useState<{
    key: string;
    history: readonly (PageCursor | null)[];
  }>({ key: queryKey, history: [null] });
  const activeHistory =
    pageState.key === queryKey ? pageState.history : ([null] as const);
  const cursor = activeHistory[activeHistory.length - 1] ?? null;
  const loadPeople = useCallback(
    () =>
      port.listPeople({
        filter,
        pageSize: PEOPLE_PAGE_SIZE,
        ...(normalizedSearch ? { search: normalizedSearch } : {}),
        ...(cursor ? { cursor } : {}),
      }),
    [cursor, filter, normalizedSearch, port],
  );
  const people = useLiveProjection(loadPeople, port, ['contacts']);
  const loadContactsStatus = useCallback(() => port.getHome(), [port]);
  const contactsStatus = useLiveProjection(loadContactsStatus, port, [
    'contacts',
  ]);
  const [syncPending, setSyncPending] = useState(false);
  const [syncProblem, setSyncProblem] = useState<NativeProblem>();
  const [syncNotice, setSyncNotice] = useState<SyncNotice>();
  const [enrollmentReview, setEnrollmentReview] =
    useState<EnrollmentReviewState>();
  const [enrollmentPending, setEnrollmentPending] = useState(false);
  const [enrollmentProblem, setEnrollmentProblem] = useState<NativeProblem>();
  const [enrollmentMessage, setEnrollmentMessage] = useState<string>();
  const [incompleteEnrollment, setIncompleteEnrollment] =
    useState<IncompleteEnrollment>();
  const mountedRef = useRef(true);
  const protectedWorkGenerationRef = useRef(0);
  const protectedRequestPendingRef = useRef(false);
  const protectedSourceRevisionRef = useRef<NativeRevision | undefined>(
    undefined,
  );
  const protectedInvalidationRevisionsRef = useRef<Set<NativeRevision>>(
    new Set(),
  );
  const reviewRef = useRef<EnrollmentReviewState | undefined>(undefined);
  const joinedTruthRef = useRef<JoinedPeopleTruth>({
    usable: false,
    queryKey,
  });
  const peopleReloadRef = useRef(people.reload);
  const contactsStatusReloadRef = useRef(contactsStatus.reload);
  const syncGenerationRef = useRef(0);
  const syncRequestPendingRef = useRef(false);
  const syncInvalidationRevisionsRef = useRef<Set<NativeRevision>>(new Set());
  const contactsActionKindRef = useRef<ContactsActionKind>('sync');

  const peopleQuerySettled =
    pageState.key === queryKey && search.trim() === normalizedSearch;
  const peopleUsable =
    people.state.kind === 'ready' &&
    peopleQuerySettled &&
    !people.state.refreshing &&
    !people.state.refreshProblem;
  const contactsStatusUsable =
    contactsStatus.state.kind === 'ready' &&
    !contactsStatus.state.refreshing &&
    !contactsStatus.state.refreshProblem;
  const peopleRevision =
    people.state.kind === 'ready'
      ? people.state.result.envelope.revision
      : undefined;
  const contactsStatusRevision =
    contactsStatus.state.kind === 'ready'
      ? contactsStatus.state.result.envelope.revision
      : undefined;
  const contactsAreFresh =
    contactsStatus.state.kind === 'ready' &&
    contactsStatus.state.result.envelope.value.contactsSync.kind === 'fresh';
  const syncNoticeAllowsBulk =
    syncNotice === undefined || syncNotice.kind === 'verified';
  const joinedRevision =
    peopleUsable &&
    contactsStatusUsable &&
    contactsAreFresh &&
    syncNoticeAllowsBulk &&
    peopleRevision === contactsStatusRevision
      ? peopleRevision
      : undefined;
  const joinedPeopleUsable = joinedRevision !== undefined;
  const joinedTruth: JoinedPeopleTruth = {
    usable: joinedPeopleUsable,
    queryKey,
    ...(joinedRevision === undefined ? {} : { revision: joinedRevision }),
  };
  joinedTruthRef.current = joinedTruth;
  reviewRef.current = enrollmentReview;
  peopleReloadRef.current = people.reload;
  contactsStatusReloadRef.current = contactsStatus.reload;

  const invalidateProtectedWork = useCallback(() => {
    protectedWorkGenerationRef.current += 1;
    protectedRequestPendingRef.current = false;
    protectedSourceRevisionRef.current = undefined;
    protectedInvalidationRevisionsRef.current.clear();
    reviewRef.current = undefined;
    if (!mountedRef.current) return;
    setEnrollmentReview(undefined);
    setEnrollmentPending(false);
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 275);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    if (pageState.key !== queryKey) {
      setPageState({ key: queryKey, history: [null] });
      invalidateProtectedWork();
      setEnrollmentProblem(undefined);
      setEnrollmentMessage(undefined);
      setIncompleteEnrollment(undefined);
    }
  }, [invalidateProtectedWork, pageState.key, queryKey]);

  useEffect(() => {
    mountedRef.current = true;
    const protectedInvalidations = protectedInvalidationRevisionsRef.current;
    const syncInvalidations = syncInvalidationRevisionsRef.current;
    return () => {
      mountedRef.current = false;
      protectedWorkGenerationRef.current += 1;
      protectedRequestPendingRef.current = false;
      protectedSourceRevisionRef.current = undefined;
      protectedInvalidations.clear();
      syncGenerationRef.current += 1;
      syncRequestPendingRef.current = false;
      syncInvalidations.clear();
    };
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (event.areas.includes('contacts')) {
          if (protectedRequestPendingRef.current) {
            protectedInvalidationRevisionsRef.current.add(event.revision);
          } else {
            invalidateProtectedWork();
          }
          if (syncRequestPendingRef.current) {
            syncInvalidationRevisionsRef.current.add(event.revision);
          } else if (mountedRef.current) {
            setSyncNotice(undefined);
          }
        }
      }),
    [invalidateProtectedWork, port],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        invalidateProtectedWork();
        if (!syncRequestPendingRef.current) setSyncNotice(undefined);
      }
    });
    return () => subscription.remove();
  }, [invalidateProtectedWork]);

  useEffect(() => {
    const sourceRevision = protectedSourceRevisionRef.current;
    if (sourceRevision === undefined || protectedRequestPendingRef.current) {
      return;
    }
    if (
      !joinedPeopleUsable ||
      joinedRevision !== sourceRevision ||
      reviewRef.current?.queryKey !== queryKey
    ) {
      invalidateProtectedWork();
    }
  }, [invalidateProtectedWork, joinedPeopleUsable, joinedRevision, queryKey]);

  const readinessLabel = (readiness: ContactReadiness): string => {
    switch (readiness.kind) {
      case 'ready':
        return t('live.people.readyReview');
      case 'needs-attention':
        return t('live.people.statusAttention', {
          reasons: readiness.reasons
            .map(reason => t(safeReasonMessageKey(reason)))
            .join(', '),
        });
      case 'unavailable':
        return t('live.people.statusUnavailable', {
          reasons: readiness.reasons
            .map(reason => t(safeReasonMessageKey(reason)))
            .join(', '),
        });
    }
  };
  const enrollmentLabel = (contact: ContactSummary): string => {
    const approvalLabel =
      contact.enrollment.kind === 'enabled' ||
      contact.enrollment.kind === 'paused'
        ? t(
            contact.enrollment.approval.kind === 'valid'
              ? 'live.people.approvalApproved'
              : 'live.people.approvalNeedsReview',
          )
        : undefined;
    switch (contact.enrollment.kind) {
      case 'off':
        return contact.readiness.kind === 'ready'
          ? t('live.people.readyToSetUp')
          : readinessLabel(contact.readiness);
      case 'enabled':
        return t('live.people.statusEnabled', {
          readiness:
            contact.readiness.kind === 'ready'
              ? approvalLabel
              : readinessLabel(contact.readiness),
        });
      case 'paused':
        return t('live.people.statusPaused', {
          reason: `${t(
            safeReasonMessageKey(contact.enrollment.reason),
          )} · ${approvalLabel}`,
        });
      case 'excluded':
        return t('live.common.excluded');
    }
  };
  const statusTone = (contact: ContactSummary): StatusTone => {
    if (contact.enrollment.kind === 'excluded') {
      return 'neutral';
    }
    if (contact.readiness.kind === 'unavailable') {
      return 'critical';
    }
    if (
      contact.enrollment.kind === 'paused' ||
      contact.readiness.kind === 'needs-attention'
    ) {
      return 'warning';
    }
    return contact.enrollment.kind === 'enabled' ? 'positive' : 'info';
  };

  const isProtectedRequestCurrent = (
    generation: number,
    sourceRevision: NativeRevision,
    selectionQueryKey: string,
  ) =>
    mountedRef.current &&
    generation === protectedWorkGenerationRef.current &&
    protectedRequestPendingRef.current &&
    protectedSourceRevisionRef.current === sourceRevision &&
    queryKeyRef.current === selectionQueryKey;

  const beginProtectedWork = (
    sourceRevision: NativeRevision,
    selectionQueryKey: string,
  ): number | undefined => {
    const truth = joinedTruthRef.current;
    if (
      protectedRequestPendingRef.current ||
      syncRequestPendingRef.current ||
      !truth.usable ||
      truth.revision !== sourceRevision ||
      truth.queryKey !== selectionQueryKey
    ) {
      return undefined;
    }
    const generation = protectedWorkGenerationRef.current + 1;
    protectedWorkGenerationRef.current = generation;
    protectedRequestPendingRef.current = true;
    protectedSourceRevisionRef.current = sourceRevision;
    protectedInvalidationRevisionsRef.current.clear();
    return generation;
  };

  const reloadJoinedTruth = async (
    generation: number,
    sourceRevision: NativeRevision,
    expectedRevision: NativeRevision,
    selectionQueryKey: string,
  ) => {
    if (
      !isProtectedRequestCurrent(generation, sourceRevision, selectionQueryKey)
    ) {
      return { kind: 'retired' as const };
    }
    const [refreshedPeople, refreshedStatus] = await Promise.all([
      peopleReloadRef.current(),
      contactsStatusReloadRef.current(),
    ]);
    if (
      !isProtectedRequestCurrent(generation, sourceRevision, selectionQueryKey)
    ) {
      return { kind: 'retired' as const };
    }
    if (refreshedPeople.kind === 'error') {
      return {
        kind: 'error' as const,
        problem: refreshedPeople.problem,
      };
    }
    if (refreshedStatus.kind === 'error') {
      return {
        kind: 'error' as const,
        problem: refreshedStatus.problem,
      };
    }
    const conflictingInvalidation = [
      ...protectedInvalidationRevisionsRef.current,
    ].find(revision => revision !== expectedRevision);
    if (conflictingInvalidation !== undefined) {
      return {
        kind: 'error' as const,
        problem: staleRevisionProblem(conflictingInvalidation),
      };
    }
    if (
      refreshedPeople.envelope.revision !== expectedRevision ||
      refreshedStatus.envelope.revision !== expectedRevision
    ) {
      return {
        kind: 'error' as const,
        problem: staleRevisionProblem(
          refreshedStatus.envelope.revision !== expectedRevision
            ? refreshedStatus.envelope.revision
            : refreshedPeople.envelope.revision,
        ),
      };
    }
    if (refreshedStatus.envelope.value.contactsSync.kind !== 'fresh') {
      return {
        kind: 'error' as const,
        problem: peopleTruthUnavailableProblem,
      };
    }

    protectedSourceRevisionRef.current = expectedRevision;
    protectedInvalidationRevisionsRef.current.clear();
    joinedTruthRef.current = {
      usable: true,
      queryKey: selectionQueryKey,
      revision: expectedRevision,
    };
    return { kind: 'ok' as const, revision: expectedRevision };
  };

  const failEnrollment = async (
    nextProblem: NativeProblem,
    completedCount: number,
    totalCount: number,
    refreshTruth = false,
  ) => {
    invalidateProtectedWork();
    if (!mountedRef.current) return;
    setEnrollmentProblem(nextProblem);
    setIncompleteEnrollment(
      completedCount > 0 && completedCount < totalCount
        ? { completedCount, totalCount }
        : undefined,
    );
    if (
      nextProblem.kind === 'stale-revision' ||
      completedCount > 0 ||
      refreshTruth
    ) {
      joinedTruthRef.current = { usable: false, queryKey: queryKeyRef.current };
      const recoveryGeneration = protectedWorkGenerationRef.current;
      await Promise.all([
        peopleReloadRef.current(),
        contactsStatusReloadRef.current(),
      ]);
      if (
        !mountedRef.current ||
        recoveryGeneration !== protectedWorkGenerationRef.current
      ) {
        return;
      }
    }
  };

  const finishAcceptedUnverified = (
    nextProblem: NativeProblem,
    completedCount: number,
    totalCount: number,
  ) => {
    invalidateProtectedWork();
    if (!mountedRef.current) return;
    setEnrollmentProblem(nextProblem);
    setIncompleteEnrollment(
      completedCount < totalCount ? { completedCount, totalCount } : undefined,
    );
    setEnrollmentMessage(
      t('live.people.enrollmentAcceptedUnverified', {
        count: completedCount,
      }),
    );
  };

  const finishEnrollment = (completedCount: number) => {
    invalidateProtectedWork();
    if (!mountedRef.current) return;
    setEnrollmentProblem(undefined);
    setIncompleteEnrollment(undefined);
    setEnrollmentMessage(
      t('live.people.enrollmentAccepted', { count: completedCount }),
    );
  };

  const prepareNextEnrollment = async ({
    completedCount,
    expectedRevision,
    generation,
    remainingIds,
    selectionQueryKey,
    sourceRevision,
    totalCount,
  }: {
    completedCount: number;
    expectedRevision: NativeRevision;
    generation: number;
    remainingIds: readonly ContactId[];
    selectionQueryKey: string;
    sourceRevision: NativeRevision;
    totalCount: number;
  }): Promise<void> => {
    if (
      !isProtectedRequestCurrent(generation, sourceRevision, selectionQueryKey)
    ) {
      return;
    }
    const candidateIds = remainingIds.slice(0, PEOPLE_REVIEW_BATCH_SIZE);
    const followingIds = remainingIds.slice(PEOPLE_REVIEW_BATCH_SIZE);
    if (candidateIds.length === 0) {
      finishEnrollment(completedCount);
      return;
    }
    let result: Awaited<ReturnType<LiveAppPort['prepareEnrollmentReview']>>;
    try {
      result = await port.prepareEnrollmentReview({
        contactIds: candidateIds,
        expectedRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !isProtectedRequestCurrent(generation, sourceRevision, selectionQueryKey)
    ) {
      return;
    }
    if (result.kind === 'error') {
      await failEnrollment(result.problem, completedCount, totalCount);
      return;
    }

    const requestedIds = [...candidateIds].sort();
    const returnedIds = result.envelope.value.recipients
      .map(contact => contact.id)
      .sort();
    const validReview =
      result.envelope.value.explicitConfirmationRequired === true &&
      result.envelope.value.readyCount === candidateIds.length &&
      result.envelope.value.attentionCount === 0 &&
      returnedIds.length === requestedIds.length &&
      new Set(returnedIds).size === returnedIds.length &&
      returnedIds.every((id, index) => id === requestedIds[index]) &&
      result.envelope.value.recipients.every(
        contact =>
          contact.readiness.kind === 'ready' &&
          contact.enrollment.kind === 'off',
      );
    if (!validReview) {
      await failEnrollment(
        invalidEnrollmentReviewProblem,
        completedCount,
        totalCount,
      );
      return;
    }

    const refreshed = await reloadJoinedTruth(
      generation,
      sourceRevision,
      result.envelope.revision,
      selectionQueryKey,
    );
    if (refreshed.kind === 'retired') return;
    if (refreshed.kind === 'error') {
      await failEnrollment(refreshed.problem, completedCount, totalCount);
      return;
    }

    const nextReview: EnrollmentReviewState = {
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision: refreshed.revision,
      queryKey: selectionQueryKey,
      requestedIds: candidateIds,
      remainingIds: followingIds,
      completedCount,
      totalCount,
    };
    reviewRef.current = nextReview;
    setEnrollmentReview(nextReview);
    protectedRequestPendingRef.current = false;
    setEnrollmentPending(false);
  };

  const prepareAllReadyEnrollment = async () => {
    const truth = joinedTruthRef.current;
    const selectionQueryKey = queryKeyRef.current;
    if (!truth.usable || truth.revision === undefined) return;
    const generation = beginProtectedWork(truth.revision, selectionQueryKey);
    if (generation === undefined) return;
    setEnrollmentPending(true);
    setEnrollmentProblem(undefined);
    setEnrollmentMessage(undefined);
    setEnrollmentReview(undefined);
    reviewRef.current = undefined;
    setIncompleteEnrollment(undefined);

    const sourceRevision = truth.revision;
    const result = await scanPeoplePages(
      port,
      {
        filter,
        ...(normalizedSearch ? { search: normalizedSearch } : {}),
      },
      isReadyOffContact,
    );
    if (
      !isProtectedRequestCurrent(generation, sourceRevision, selectionQueryKey)
    ) {
      return;
    }
    if (result.kind === 'error') {
      await failEnrollment(result.problem, 0, 0);
      return;
    }
    const conflictingInvalidation = [
      ...protectedInvalidationRevisionsRef.current,
    ].find(revision => revision !== sourceRevision);
    if (
      result.envelope.revision !== sourceRevision ||
      conflictingInvalidation !== undefined
    ) {
      await failEnrollment(
        staleRevisionProblem(
          conflictingInvalidation ?? result.envelope.revision,
        ),
        0,
        0,
      );
      return;
    }
    const candidateIds = result.envelope.value.contactIds;
    if (candidateIds.length === 0) {
      invalidateProtectedWork();
      if (!mountedRef.current) return;
      setEnrollmentMessage(t('live.people.noReadyAcrossPages'));
      return;
    }
    await prepareNextEnrollment({
      completedCount: 0,
      expectedRevision: sourceRevision,
      generation,
      remainingIds: candidateIds,
      selectionQueryKey,
      sourceRevision,
      totalCount: candidateIds.length,
    });
  };

  const confirmPageEnrollment = async () => {
    if (protectedRequestPendingRef.current) return;
    const currentReview = reviewRef.current;
    const truth = joinedTruthRef.current;
    if (
      !currentReview ||
      !truth.usable ||
      truth.queryKey !== currentReview.queryKey ||
      truth.revision !== currentReview.sourceRevision
    ) {
      invalidateProtectedWork();
      return;
    }
    const generation = beginProtectedWork(
      currentReview.sourceRevision,
      currentReview.queryKey,
    );
    if (generation === undefined) return;
    reviewRef.current = undefined;
    setEnrollmentReview(undefined);
    setEnrollmentPending(true);
    setEnrollmentProblem(undefined);
    setEnrollmentMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmEnrollment']>>;
    try {
      result = await port.confirmEnrollment({
        handle: currentReview.review.handle,
        expectedRevision: currentReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !isProtectedRequestCurrent(
        generation,
        currentReview.sourceRevision,
        currentReview.queryKey,
      )
    ) {
      return;
    }
    if (result.kind === 'error') {
      await failEnrollment(
        result.problem,
        currentReview.completedCount,
        currentReview.totalCount,
      );
      return;
    }

    const requestedIds = [...currentReview.requestedIds].sort();
    const changedIds = [...result.envelope.value.changedContactIds].sort();
    const validMutation =
      changedIds.length === requestedIds.length &&
      new Set(changedIds).size === changedIds.length &&
      changedIds.every((id, index) => id === requestedIds[index]);
    if (!validMutation) {
      await failEnrollment(
        invalidEnrollmentReviewProblem,
        currentReview.completedCount,
        currentReview.totalCount,
        true,
      );
      return;
    }

    const completedCount =
      currentReview.completedCount + currentReview.requestedIds.length;
    const refreshed = await reloadJoinedTruth(
      generation,
      currentReview.sourceRevision,
      result.envelope.revision,
      currentReview.queryKey,
    );
    if (refreshed.kind === 'retired') return;
    if (refreshed.kind === 'error') {
      finishAcceptedUnverified(
        refreshed.problem,
        completedCount,
        currentReview.totalCount,
      );
      return;
    }
    if (currentReview.remainingIds.length === 0) {
      finishEnrollment(completedCount);
      return;
    }
    await prepareNextEnrollment({
      completedCount,
      expectedRevision: refreshed.revision,
      generation,
      remainingIds: currentReview.remainingIds,
      selectionQueryKey: currentReview.queryKey,
      sourceRevision: refreshed.revision,
      totalCount: currentReview.totalCount,
    });
  };

  const cancelEnrollmentReview = async () => {
    const currentReview = reviewRef.current;
    if (!currentReview) return;
    const { completedCount, totalCount } = currentReview;
    invalidateProtectedWork();
    if (!mountedRef.current || completedCount === 0) return;
    setEnrollmentPending(true);
    setIncompleteEnrollment({ completedCount, totalCount });
    joinedTruthRef.current = { usable: false, queryKey: queryKeyRef.current };
    const refreshGeneration = protectedWorkGenerationRef.current;
    const [refreshedPeople, refreshedStatus] = await Promise.all([
      peopleReloadRef.current(),
      contactsStatusReloadRef.current(),
    ]);
    if (
      !mountedRef.current ||
      refreshGeneration !== protectedWorkGenerationRef.current
    ) {
      return;
    }
    if (refreshedPeople.kind === 'error') {
      setEnrollmentProblem(refreshedPeople.problem);
    } else if (refreshedStatus.kind === 'error') {
      setEnrollmentProblem(refreshedStatus.problem);
    }
    setEnrollmentPending(false);
  };

  const runContactsAction = async () => {
    if (
      syncRequestPendingRef.current ||
      protectedRequestPendingRef.current ||
      reviewRef.current !== undefined
    ) {
      return;
    }
    const actionKind = contactsActionKindRef.current;
    const generation = syncGenerationRef.current + 1;
    syncGenerationRef.current = generation;
    syncRequestPendingRef.current = true;
    syncInvalidationRevisionsRef.current.clear();
    invalidateProtectedWork();
    setSyncPending(true);
    setSyncProblem(undefined);
    setSyncNotice(undefined);
    let result: Awaited<ReturnType<LiveAppPort['syncContacts']>>;
    try {
      result =
        actionKind === 'authorize'
          ? await port.authorizeContacts()
          : await port.syncContacts('user');
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !mountedRef.current ||
      generation !== syncGenerationRef.current ||
      !syncRequestPendingRef.current
    ) {
      return;
    }
    if (result.kind === 'error') {
      syncRequestPendingRef.current = false;
      syncInvalidationRevisionsRef.current.clear();
      setSyncProblem(result.problem);
      setSyncPending(false);
      return;
    }

    joinedTruthRef.current = { usable: false, queryKey: queryKeyRef.current };
    const [refreshedPeople, refreshedStatus] = await Promise.all([
      peopleReloadRef.current(),
      contactsStatusReloadRef.current(),
    ]);
    if (
      !mountedRef.current ||
      generation !== syncGenerationRef.current ||
      !syncRequestPendingRef.current
    ) {
      return;
    }

    let verificationProblem: NativeProblem | undefined;
    if (refreshedPeople.kind === 'error') {
      verificationProblem = refreshedPeople.problem;
    } else if (refreshedStatus.kind === 'error') {
      verificationProblem = refreshedStatus.problem;
    } else if (
      refreshedPeople.envelope.revision !== result.envelope.revision ||
      refreshedStatus.envelope.revision !== result.envelope.revision
    ) {
      verificationProblem = staleRevisionProblem(
        refreshedStatus.envelope.revision !== result.envelope.revision
          ? refreshedStatus.envelope.revision
          : refreshedPeople.envelope.revision,
      );
    } else {
      const conflictingInvalidation = [
        ...syncInvalidationRevisionsRef.current,
      ].find(revision => revision !== result.envelope.revision);
      if (conflictingInvalidation !== undefined) {
        verificationProblem = staleRevisionProblem(conflictingInvalidation);
      }
    }

    syncRequestPendingRef.current = false;
    syncInvalidationRevisionsRef.current.clear();
    setSyncPending(false);
    if (result.envelope.value.kind !== 'fresh') {
      setSyncNotice({ kind: 'projection', projection: result.envelope.value });
      return;
    }
    const verifiedFresh =
      verificationProblem === undefined &&
      refreshedStatus.kind === 'ok' &&
      refreshedStatus.envelope.value.contactsSync.kind === 'fresh';
    if (verifiedFresh) {
      setSyncNotice({ kind: 'verified' });
      return;
    }
    setSyncProblem(verificationProblem);
    setSyncNotice({ kind: 'unverified' });
  };

  const projectedContactsSync =
    syncNotice?.kind === 'projection'
      ? syncNotice.projection
      : contactsStatus.state.kind === 'ready'
      ? contactsStatus.state.result.envelope.value.contactsSync
      : undefined;
  const syncPresentation: SyncPresentation | undefined = (() => {
    if (syncNotice?.kind === 'unverified') {
      return {
        title: 'live.people.syncUnverifiedTitle',
        detail: 'live.people.syncUnverifiedBody',
        tone: 'warning',
      };
    }
    if (
      syncNotice?.kind !== 'projection' &&
      (contactsStatus.state.kind === 'error' ||
        (contactsStatus.state.kind === 'ready' &&
          contactsStatus.state.refreshProblem))
    ) {
      return {
        title: 'live.people.contactsStatusUnavailableTitle',
        detail: 'live.people.contactsStatusUnavailableBody',
        tone: 'critical',
      };
    }
    if (syncNotice?.kind === 'verified') {
      return {
        title: 'live.people.syncVerifiedTitle',
        detail: 'live.people.syncVerifiedBody',
        tone: 'positive',
      };
    }
    if (projectedContactsSync) {
      switch (projectedContactsSync.kind) {
        case 'fresh':
          return undefined;
        case 'never-synced':
          return {
            title: 'live.people.contactsNeverSyncedTitle',
            detail: 'live.people.contactsNeverSyncedBody',
            tone: 'warning',
          };
        case 'syncing':
          return {
            title: 'live.people.contactsSyncingTitle',
            detail: 'live.people.contactsSyncingBody',
            tone: 'info',
          };
        case 'authorization-required':
          return {
            title: 'live.people.contactsAuthorizationRequiredTitle',
            detail: 'live.people.contactsAuthorizationRequiredBody',
            tone: 'warning',
          };
        case 'stale':
          return {
            title: 'live.people.contactsStaleTitle',
            detail: 'live.people.contactsStaleBody',
            tone: 'warning',
            reason: t(safeReasonMessageKey(projectedContactsSync.reason)),
          };
        case 'failed-retained':
          return {
            title: 'live.people.contactsFailedTitle',
            detail: 'live.people.contactsFailedBody',
            tone: 'warning',
            reason: t(safeReasonMessageKey(projectedContactsSync.reason)),
          };
      }
    }
    return undefined;
  })();
  const contactsPlatform =
    contactsStatus.state.kind === 'ready'
      ? contactsStatus.state.result.envelope.value.automation.platform
      : undefined;
  const authorizationRequired =
    projectedContactsSync?.kind === 'authorization-required';
  const syncInProgress = projectedContactsSync?.kind === 'syncing';
  const canAuthorizeContacts =
    authorizationRequired && contactsPlatform !== undefined;
  contactsActionKindRef.current = canAuthorizeContacts ? 'authorize' : 'sync';
  const currentEnrollmentReview =
    enrollmentReview &&
    joinedPeopleUsable &&
    enrollmentReview.queryKey === queryKey &&
    enrollmentReview.sourceRevision === joinedRevision
      ? enrollmentReview
      : undefined;
  const bulkFilterSelected = filter === 'all' || filter === 'ready';

  return (
    <Screen
      includeTopInset
      includeBottomInset={false}
      testID="live-people-screen"
    >
      {onBack ? (
        <Button
          label={t('live.common.back')}
          onPress={onBack}
          variant="ghost"
          testID="live-people-back"
        />
      ) : null}
      <AppText variant="title" accessibilityRole="header">
        {t('live.people.title')}
      </AppText>
      <AppText color="muted">{t('live.people.body')}</AppText>
      <LiveActionFeedback problem={syncProblem} />
      {syncPresentation ? (
        <ReadinessBanner
          title={t(syncPresentation.title)}
          detail={t(syncPresentation.detail, {
            reason: syncPresentation.reason ?? '',
          })}
          tone={syncPresentation.tone}
          testID="live-people-contacts-status"
        />
      ) : null}
      {canAuthorizeContacts ? (
        <ReadinessBanner
          title={t('live.setup.contactsPrivacyTitle')}
          detail={t(
            contactsPlatform === 'android'
              ? 'live.setup.contactsPrivacyAndroid'
              : 'live.setup.contactsPrivacyIos',
          )}
          tone="warning"
          testID="live-people-contacts-privacy"
        />
      ) : null}
      <Button
        label={
          syncPending || syncInProgress
            ? t(
                canAuthorizeContacts
                  ? 'live.setup.connecting'
                  : 'live.people.syncing',
              )
            : t(
                canAuthorizeContacts
                  ? 'live.setup.authorizeContacts'
                  : 'live.people.syncNow',
              )
        }
        disabled={
          syncPending ||
          syncInProgress ||
          enrollmentPending ||
          currentEnrollmentReview !== undefined
        }
        onPress={runContactsAction}
        variant="secondary"
        testID="live-people-sync"
      />
      <SearchField
        value={search}
        onChangeText={nextSearch => {
          invalidateProtectedWork();
          setSearch(nextSearch);
        }}
        label={t('live.people.search')}
        hint={t('live.people.searchHint')}
        clearA11yLabel={t('live.common.clearSearch')}
        testID="live-people-search"
      />
      <View
        accessibilityLabel={t('live.people.filters')}
        accessibilityRole="radiogroup"
        style={styles.filters}
      >
        {filters.map(item => (
          <ChoiceChip
            key={item.value}
            label={t(item.label)}
            selected={filter === item.value}
            onPress={() => {
              if (item.value === filter) return;
              invalidateProtectedWork();
              setFilter(item.value);
            }}
            testID={`live-people-filter-${item.value}`}
          />
        ))}
      </View>

      {people.state.kind === 'loading' ? (
        <LiveLoading label={t('live.people.loading')} />
      ) : null}
      {people.state.kind === 'error' ? (
        <LiveError
          title={t('live.people.unavailable')}
          problem={people.state.problem}
          onRetry={() => {
            invalidateProtectedWork();
            Promise.all([
              peopleReloadRef.current(),
              contactsStatusReloadRef.current(),
            ]).catch(() => undefined);
          }}
        />
      ) : null}
      {people.state.kind === 'ready' ? (
        <>
          {people.state.refreshProblem ? (
            <LiveRefreshProblem problem={people.state.refreshProblem} />
          ) : null}
          <SectionHeading
            title={t('live.common.countPeople', {
              count: people.state.result.envelope.value.totalCount,
            })}
          />
          <LiveActionFeedback
            problem={enrollmentProblem}
            message={enrollmentMessage}
          />
          {incompleteEnrollment ? (
            <ReadinessBanner
              title={t('live.people.enrollmentIncomplete')}
              detail={t('live.people.enrollmentIncompleteBody', {
                completed: incompleteEnrollment.completedCount,
                total: incompleteEnrollment.totalCount,
              })}
              tone="warning"
            />
          ) : null}
          {bulkFilterSelected &&
          joinedPeopleUsable &&
          !currentEnrollmentReview &&
          (people.state.result.envelope.value.items.some(isReadyOffContact) ||
            people.state.result.envelope.value.nextCursor !== undefined ||
            activeHistory.length > 1) ? (
            <Card>
              <AppText variant="heading">
                {t('live.people.pageEnrollmentTitle')}
              </AppText>
              <AppText color="muted">
                {t('live.people.pageEnrollmentBody', {
                  count: people.state.result.envelope.value.totalCount,
                })}
              </AppText>
              <Button
                label={
                  enrollmentPending
                    ? t('live.people.preparingEnrollment')
                    : t('live.people.selectAllReady', {
                        count: people.state.result.envelope.value.totalCount,
                      })
                }
                disabled={enrollmentPending || syncPending}
                onPress={() =>
                  prepareAllReadyEnrollment().catch(() => undefined)
                }
                testID="live-people-select-page-ready"
              />
            </Card>
          ) : null}
          {currentEnrollmentReview ? (
            <Card>
              <AppText variant="heading">
                {t('live.people.enrollmentReviewTitle')}
              </AppText>
              <AppText color="muted">
                {t('live.people.enrollmentReviewBody', {
                  count: currentEnrollmentReview.review.recipients.length,
                  completed: currentEnrollmentReview.completedCount,
                  total: currentEnrollmentReview.totalCount,
                })}
              </AppText>
              <KeyValue
                label={t('live.people.readyCount')}
                value={String(currentEnrollmentReview.review.readyCount)}
              />
              <KeyValue
                label={t('live.people.attentionCount')}
                value={String(currentEnrollmentReview.review.attentionCount)}
              />
              {currentEnrollmentReview.review.recipients.map(contact => (
                <Card key={contact.id}>
                  <AppText variant="label">{contact.displayName}</AppText>
                  <AppText color="muted">
                    {contact.birthdayLabel ??
                      t('live.people.birthdayNeedsReview')}
                  </AppText>
                  <AppText color="muted">
                    {contact.maskedPhone ?? t('live.common.unavailable')}
                  </AppText>
                  <StatusRow
                    title={t('live.people.readyReview')}
                    tone="positive"
                  />
                </Card>
              ))}
              <ReadinessBanner
                title={t('live.people.explicitConfirmation')}
                detail={t('live.people.enrollmentDoesNotSend')}
                tone="warning"
              />
              <Button
                label={
                  enrollmentPending
                    ? t('live.person.confirming')
                    : t('live.people.confirmPageEnrollment')
                }
                disabled={enrollmentPending}
                onPress={confirmPageEnrollment}
                testID="live-people-confirm-page-enrollment"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={enrollmentPending}
                onPress={() => cancelEnrollmentReview().catch(() => undefined)}
                variant="secondary"
                testID="live-people-cancel-page-enrollment"
              />
            </Card>
          ) : null}
          {people.state.result.envelope.value.items.length === 0 ? (
            <Card>
              <AppText>
                {t(emptyMessageKey(filter, normalizedSearch.length > 0))}
              </AppText>
            </Card>
          ) : (
            people.state.result.envelope.value.items.map(contact => {
              const status = enrollmentLabel(contact);
              return (
                <PersonRow
                  key={contact.id}
                  initials={initials(contact.displayName)}
                  name={contact.displayName}
                  birthday={
                    contact.birthdayLabel ??
                    t('live.people.birthdayNeedsReview')
                  }
                  {...(contact.maskedPhone === undefined
                    ? {}
                    : { phone: contact.maskedPhone })}
                  status={status}
                  statusTone={statusTone(contact)}
                  onPress={() => onOpenPerson(contact.id)}
                  accessibilityLabel={t('live.people.open', {
                    name: bidiIsolate(contact.displayName),
                    birthday:
                      contact.birthdayLabel ??
                      t('live.people.birthdayNeedsReview'),
                    phone: contact.maskedPhone ?? t('live.common.unavailable'),
                    status,
                  })}
                  testID={`live-person-${contact.id}`}
                />
              );
            })
          )}
          {people.state.result.envelope.value.nextCursor ? (
            <Button
              label={t('live.people.nextPage')}
              disabled={
                enrollmentPending ||
                syncPending ||
                currentEnrollmentReview !== undefined ||
                people.state.refreshing
              }
              onPress={() =>
                setPageState(current => ({
                  key: queryKey,
                  history: [
                    ...(current.key === queryKey ? current.history : [null]),
                    people.state.kind === 'ready'
                      ? people.state.result.envelope.value.nextCursor ?? null
                      : null,
                  ],
                }))
              }
              testID="live-people-next-page"
            />
          ) : null}
          {activeHistory.length > 1 ? (
            <Button
              label={t('live.people.previousPage')}
              disabled={
                enrollmentPending ||
                syncPending ||
                currentEnrollmentReview !== undefined ||
                people.state.refreshing
              }
              onPress={() =>
                setPageState(current => ({
                  key: queryKey,
                  history:
                    current.key === queryKey
                      ? current.history.slice(0, -1)
                      : [null],
                }))
              }
              variant="secondary"
              testID="live-people-previous-page"
            />
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  filters: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
});
