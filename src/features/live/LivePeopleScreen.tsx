import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type {
  ContactReadiness,
  ContactSummary,
  EnrollmentReview,
  PeopleFilter,
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
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
  LiveActionFeedback,
} from './LiveProjectionState';
import { nativeBridgeProblem } from './nativeProblem';
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
  queryKey: string;
}>;

const invalidEnrollmentReviewProblem: NativeProblem = {
  kind: 'internal',
  supportCode: 'INVALID_ENROLLMENT_REVIEW' as SafeSupportCode,
};

export function LivePeopleScreen({
  onOpenPerson,
  port,
}: {
  onOpenPerson: (contactId: ContactId) => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const [filter, setFilter] = useState<PeopleFilter>('all');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const normalizedSearch = debouncedSearch.trim();
  const queryKey = `${filter}\u0000${normalizedSearch}`;
  const [pageState, setPageState] = useState<{
    key: string;
    history: readonly (PageCursor | null)[];
  }>({ key: queryKey, history: [null] });
  const activeHistory =
    pageState.key === queryKey ? pageState.history : ([null] as const);
  const cursor = activeHistory[activeHistory.length - 1] ?? null;
  const [syncPending, setSyncPending] = useState(false);
  const [syncProblem, setSyncProblem] = useState<NativeProblem>();
  const [syncMessage, setSyncMessage] = useState<string>();
  const [enrollmentReview, setEnrollmentReview] =
    useState<EnrollmentReviewState>();
  const [enrollmentPending, setEnrollmentPending] = useState(false);
  const [enrollmentProblem, setEnrollmentProblem] = useState<NativeProblem>();
  const [enrollmentMessage, setEnrollmentMessage] = useState<string>();

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 275);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    if (pageState.key !== queryKey) {
      setPageState({ key: queryKey, history: [null] });
      setEnrollmentReview(undefined);
      setEnrollmentProblem(undefined);
      setEnrollmentMessage(undefined);
    }
  }, [pageState.key, queryKey]);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (event.areas.includes('contacts')) {
          setEnrollmentReview(undefined);
        }
      }),
    [port],
  );

  const loadPeople = useCallback(
    () =>
      port.listPeople({
        filter,
        pageSize: 50,
        ...(normalizedSearch ? { search: normalizedSearch } : {}),
        ...(cursor ? { cursor } : {}),
      }),
    [cursor, filter, normalizedSearch, port],
  );
  const people = useLiveProjection(loadPeople, port, ['contacts']);

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
    switch (contact.enrollment.kind) {
      case 'off':
        return t('live.common.off');
      case 'enabled':
        return t('live.people.statusEnabled', {
          readiness: readinessLabel(contact.readiness),
        });
      case 'paused':
        return t('live.people.statusPaused', {
          reason: t(safeReasonMessageKey(contact.enrollment.reason)),
        });
      case 'excluded':
        return t('live.common.excluded');
    }
  };
  const statusTone = (contact: ContactSummary): StatusTone => {
    if (contact.enrollment.kind === 'excluded') {
      return 'neutral';
    }
    if (
      contact.enrollment.kind === 'paused' ||
      contact.readiness.kind === 'needs-attention'
    ) {
      return 'warning';
    }
    if (contact.readiness.kind === 'unavailable') {
      return 'critical';
    }
    return contact.enrollment.kind === 'enabled' ? 'positive' : 'info';
  };

  const syncNow = async () => {
    setSyncPending(true);
    setSyncProblem(undefined);
    setSyncMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['syncContacts']>>;
    try {
      result = await port.syncContacts('user');
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setSyncProblem(result.problem);
      setSyncPending(false);
      return;
    }
    setPageState({ key: queryKey, history: [null] });
    await people.reload();
    setSyncMessage(t('live.people.syncComplete'));
    setSyncPending(false);
  };

  const preparePageEnrollment = async (
    candidates: readonly ContactSummary[],
    revision: NativeRevision,
  ) => {
    const candidateIds = candidates.slice(0, 50).map(contact => contact.id);
    if (candidateIds.length === 0) {
      return;
    }
    setEnrollmentPending(true);
    setEnrollmentProblem(undefined);
    setEnrollmentMessage(undefined);
    setEnrollmentReview(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareEnrollmentReview']>>;
    try {
      result = await port.prepareEnrollmentReview({
        contactIds: candidateIds,
        expectedRevision: revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await people.reload();
      }
      setEnrollmentProblem(result.problem);
      setEnrollmentPending(false);
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
      setEnrollmentProblem(invalidEnrollmentReviewProblem);
      setEnrollmentPending(false);
      return;
    }

    setEnrollmentReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
      queryKey,
    });
    setEnrollmentPending(false);
  };

  const confirmPageEnrollment = async () => {
    if (!enrollmentReview || enrollmentReview.queryKey !== queryKey) {
      return;
    }
    setEnrollmentPending(true);
    setEnrollmentProblem(undefined);
    setEnrollmentMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmEnrollment']>>;
    try {
      result = await port.confirmEnrollment({
        handle: enrollmentReview.review.handle,
        expectedRevision: enrollmentReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await people.reload();
        setEnrollmentReview(undefined);
      }
      setEnrollmentProblem(result.problem);
      setEnrollmentPending(false);
      return;
    }
    setEnrollmentReview(undefined);
    const refreshed = await people.reload();
    setEnrollmentMessage(
      refreshed.kind === 'ok'
        ? t('live.people.enrollmentAccepted', {
            count: result.envelope.value.changedContactIds.length,
          })
        : t('live.people.enrollmentAcceptedUnverified', {
            count: result.envelope.value.changedContactIds.length,
          }),
    );
    setEnrollmentPending(false);
  };

  return (
    <Screen
      includeTopInset
      includeBottomInset={false}
      testID="live-people-screen"
    >
      <AppText variant="title" accessibilityRole="header">
        {t('live.people.title')}
      </AppText>
      <AppText color="muted">{t('live.people.body')}</AppText>
      <LiveActionFeedback problem={syncProblem} message={syncMessage} />
      <Button
        label={
          syncPending ? t('live.people.syncing') : t('live.people.syncNow')
        }
        disabled={syncPending}
        onPress={syncNow}
        variant="secondary"
        testID="live-people-sync"
      />
      <SearchField
        value={search}
        onChangeText={setSearch}
        label={t('live.people.search')}
        hint={t('live.people.searchHint')}
        testID="live-people-search"
      />
      <View accessibilityRole="radiogroup" style={styles.filters}>
        {filters.map(item => (
          <ChoiceChip
            key={item.value}
            label={t(item.label)}
            selected={filter === item.value}
            onPress={() => setFilter(item.value)}
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
          onRetry={() => people.reload()}
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
            supporting={t('live.people.supporting')}
          />
          {(() => {
            const candidates = people.state.result.envelope.value.items
              .filter(
                contact =>
                  contact.readiness.kind === 'ready' &&
                  contact.enrollment.kind === 'off',
              )
              .slice(0, 50);
            return candidates.length > 0 ? (
              <>
                <LiveActionFeedback
                  problem={enrollmentProblem}
                  message={enrollmentMessage}
                />
                <Card>
                  <AppText variant="heading">
                    {t('live.people.pageEnrollmentTitle')}
                  </AppText>
                  <AppText color="muted">
                    {t('live.people.pageEnrollmentBody', {
                      count: candidates.length,
                    })}
                  </AppText>
                  {!enrollmentReview ? (
                    <Button
                      label={
                        enrollmentPending
                          ? t('live.people.preparingEnrollment')
                          : t('live.people.selectAllReady', {
                              count: candidates.length,
                            })
                      }
                      disabled={enrollmentPending}
                      onPress={() => {
                        if (people.state.kind === 'ready') {
                          preparePageEnrollment(
                            candidates,
                            people.state.result.envelope.revision,
                          ).catch(() => undefined);
                        }
                      }}
                      testID="live-people-select-page-ready"
                    />
                  ) : null}
                </Card>
              </>
            ) : enrollmentMessage || enrollmentProblem ? (
              <LiveActionFeedback
                problem={enrollmentProblem}
                message={enrollmentMessage}
              />
            ) : null;
          })()}
          {enrollmentReview && enrollmentReview.queryKey === queryKey ? (
            <Card>
              <AppText variant="heading">
                {t('live.people.enrollmentReviewTitle')}
              </AppText>
              <AppText color="muted">
                {t('live.people.enrollmentReviewBody', {
                  count: enrollmentReview.review.recipients.length,
                })}
              </AppText>
              <KeyValue
                label={t('live.people.readyCount')}
                value={String(enrollmentReview.review.readyCount)}
              />
              <KeyValue
                label={t('live.people.attentionCount')}
                value={String(enrollmentReview.review.attentionCount)}
              />
              {enrollmentReview.review.recipients.map(contact => (
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
                onPress={() => setEnrollmentReview(undefined)}
                variant="secondary"
                testID="live-people-cancel-page-enrollment"
              />
            </Card>
          ) : null}
          {people.state.result.envelope.value.items.length === 0 ? (
            <Card>
              <AppText>{t('live.people.empty')}</AppText>
              <AppText color="muted">{t('live.people.emptyHelp')}</AppText>
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
                    name: contact.displayName,
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
          <Button
            label={
              people.state.refreshing
                ? t('live.common.refreshing')
                : t('live.people.refresh')
            }
            disabled={people.state.refreshing}
            onPress={() => people.reload()}
            variant="secondary"
            testID="live-people-refresh"
          />
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
