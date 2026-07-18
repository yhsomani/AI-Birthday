import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Linking, StyleSheet, View } from 'react-native';

import type { ApprovalBatchReview } from '../../domain/approvals/model';
import type { LeapDayPolicy } from '../../domain/birthdays/model';
import type {
  ContactIssueCode,
  EnrollmentReview,
  PeopleMutationProjection,
} from '../../domain/contacts/model';
import type {
  BirthdayChoiceId,
  ContactId,
  NativeRevision,
  PhoneChoiceId,
} from '../../domain/shared/brand';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  KeyValue,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { bidiIsolate } from '../../localization/bidi';
import { formatLiveInstant } from '../../localization/formatLive';
import {
  approvalInvalidationMessageKey,
  safeReasonMessageKey,
} from '../../localization/reasonCopy';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import {
  nativeBridgeProblem,
  nativeContractProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

type EnrollmentReviewState = Readonly<{
  review: EnrollmentReview;
  revision: NativeRevision;
}>;

type ApprovalReviewState = Readonly<{
  review: ApprovalBatchReview;
  revision: NativeRevision;
}>;

type ChoiceReview =
  | Readonly<{ kind: 'phone'; id: PhoneChoiceId }>
  | Readonly<{
      kind: 'birthday';
      id: BirthdayChoiceId;
      leapRequired: boolean;
      leapPolicy?: LeapDayPolicy | undefined;
    }>;

const GOOGLE_CONTACTS_URL = 'https://contacts.google.com/';
const GOOGLE_CONTACTS_REPAIR_ISSUES = new Set<ContactIssueCode>([
  'birthday-missing',
  'phone-missing',
  'safe-given-name-missing',
  'source-contact-deleted',
  'stable-source-missing',
  'phone-ambiguous-region',
]);

export function LivePersonDetailScreen({
  capability,
  contactId,
  onBack,
  port,
}: {
  capability: PlatformCapability;
  contactId: ContactId;
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const loadDetail = useCallback(
    () => port.getPerson(contactId),
    [contactId, port],
  );
  const detail = useLiveProjection(loadDetail, port, [
    'contacts',
    'automation',
  ]);
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [enrollmentReview, setEnrollmentReview] =
    useState<EnrollmentReviewState>();
  const [approvalReview, setApprovalReview] = useState<ApprovalReviewState>();
  const [choiceReview, setChoiceReview] = useState<ChoiceReview>();
  const [confirmExclude, setConfirmExclude] = useState(false);
  const [destinationBlockReview, setDestinationBlockReview] = useState<
    'block' | 'unblock'
  >();
  const syncAfterGoogleContactsReturn = useRef(false);

  useEffect(() => {
    syncAfterGoogleContactsReturn.current = false;
    setEnrollmentReview(undefined);
    setApprovalReview(undefined);
    setChoiceReview(undefined);
    setConfirmExclude(false);
    setDestinationBlockReview(undefined);
    setProblem(undefined);
    setMessage(undefined);
  }, [contactId]);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('contacts') ||
          event.areas.includes('automation')
        ) {
          setEnrollmentReview(undefined);
          setApprovalReview(undefined);
          setChoiceReview(undefined);
          setConfirmExclude(false);
          setDestinationBlockReview(undefined);
        }
      }),
    [port],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState !== 'active' || !syncAfterGoogleContactsReturn.current) {
        return;
      }
      syncAfterGoogleContactsReturn.current = false;
      setPending(true);
      setProblem(undefined);
      setMessage(undefined);
      (async () => {
        try {
          const synced = await port.syncContacts('user');
          if (synced.kind === 'error') {
            setProblem(synced.problem);
          } else {
            await detail.reload();
            setMessage(t('live.person.googleContactsSynced'));
          }
        } catch {
          setProblem(nativeBridgeProblem);
        }
        setPending(false);
      })().catch(() => undefined);
    });
    return () => subscription.remove();
  }, [detail, port, t]);

  const openGoogleContacts = async () => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    syncAfterGoogleContactsReturn.current = true;
    try {
      const supported = await Linking.canOpenURL(GOOGLE_CONTACTS_URL);
      if (!supported) {
        syncAfterGoogleContactsReturn.current = false;
        setProblem(nativeBridgeProblem);
        setPending(false);
        return;
      }
      await Linking.openURL(GOOGLE_CONTACTS_URL);
      setMessage(t('live.person.googleContactsOpened'));
    } catch {
      syncAfterGoogleContactsReturn.current = false;
      setProblem(nativeBridgeProblem);
    }
    setPending(false);
  };

  const fail = async (
    actionProblem: NativeProblem,
    refreshAfterAcceptedContractFailure = false,
  ) => {
    if (
      actionProblem.kind === 'stale-revision' ||
      refreshAfterAcceptedContractFailure
    ) {
      await detail.reload();
      setEnrollmentReview(undefined);
      setApprovalReview(undefined);
      setChoiceReview(undefined);
      setConfirmExclude(false);
      setDestinationBlockReview(undefined);
    }
    setProblem(actionProblem);
    setPending(false);
  };

  const reloadAfterAccepted = async (acceptedMessage: string) => {
    const refreshed = await detail.reload();
    setEnrollmentReview(undefined);
    setApprovalReview(undefined);
    setChoiceReview(undefined);
    setConfirmExclude(false);
    setDestinationBlockReview(undefined);
    setMessage(
      refreshed.kind === 'ok'
        ? acceptedMessage
        : t('live.person.acceptedUnverified'),
    );
    setPending(false);
  };

  const finishMutation = async (
    result: NativeResult<PeopleMutationProjection>,
    acceptedMessage: string,
  ) => {
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    if (
      result.envelope.value.changedContactIds.length !== 1 ||
      result.envelope.value.changedContactIds[0] !== contactId
    ) {
      await fail(nativeContractProblem, true);
      return;
    }
    await reloadAfterAccepted(acceptedMessage);
  };

  const prepareEnrollment = async (revision: NativeRevision) => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareEnrollmentReview']>>;
    try {
      result = await port.prepareEnrollmentReview({
        contactIds: [contactId],
        expectedRevision: revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    const returnedContact = result.envelope.value.recipients[0];
    const validReview =
      result.envelope.value.recipients.length === 1 &&
      result.envelope.value.readyCount === 1 &&
      result.envelope.value.attentionCount === 0 &&
      returnedContact?.id === contactId &&
      returnedContact.readiness.kind === 'ready' &&
      returnedContact.enrollment.kind === 'off';
    if (!validReview) {
      await fail(nativeContractProblem);
      return;
    }
    setEnrollmentReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(false);
  };

  const confirmEnrollment = async () => {
    if (!enrollmentReview) {
      return;
    }
    setPending(true);
    let result: Awaited<ReturnType<LiveAppPort['confirmEnrollment']>>;
    try {
      result = await port.confirmEnrollment({
        handle: enrollmentReview.review.handle,
        expectedRevision: enrollmentReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    await finishMutation(result, t('live.person.enrollmentAccepted'));
  };

  const runRecipientMutation = async (
    kind: 'pause' | 'restore' | 'exclude',
    revision: NativeRevision,
  ) => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    const input = { contactId, expectedRevision: revision };
    let result: NativeResult<PeopleMutationProjection>;
    try {
      result =
        kind === 'pause'
          ? await port.pauseRecipient(input)
          : kind === 'restore'
          ? await port.restoreRecipient(input)
          : await port.excludeRecipient(input);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    await finishMutation(
      result,
      t(
        kind === 'pause'
          ? 'live.person.pauseAccepted'
          : kind === 'restore'
          ? 'live.person.restoreAccepted'
          : 'live.person.excludeAccepted',
      ),
    );
  };

  const runDestinationBlockMutation = async (
    kind: 'block' | 'unblock',
    revision: NativeRevision,
  ) => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    const input = { contactId, expectedRevision: revision };
    let result: NativeResult<PeopleMutationProjection>;
    try {
      result =
        kind === 'block'
          ? await port.blockRecipientDestination(input)
          : await port.unblockRecipientDestination(input);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    await finishMutation(
      result,
      t(
        kind === 'block'
          ? 'live.person.destinationBlockAccepted'
          : 'live.person.destinationUnblockAccepted',
      ),
    );
  };

  const confirmChoice = async (revision: NativeRevision) => {
    if (!choiceReview) {
      return;
    }
    if (
      choiceReview.kind === 'birthday' &&
      choiceReview.leapRequired &&
      !choiceReview.leapPolicy
    ) {
      return;
    }
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['getPerson']>>;
    try {
      result =
        choiceReview.kind === 'phone'
          ? await port.choosePhone({
              contactId,
              phoneId: choiceReview.id,
              expectedRevision: revision,
            })
          : await port.chooseBirthday({
              contactId,
              birthdayId: choiceReview.id,
              ...(choiceReview.leapPolicy
                ? { leapPolicy: choiceReview.leapPolicy }
                : {}),
              expectedRevision: revision,
            });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    const selectedChoiceMatches =
      result.envelope.value.summary.id === contactId &&
      (choiceReview.kind === 'phone'
        ? result.envelope.value.selectedPhoneId === choiceReview.id
        : result.envelope.value.selectedBirthdayId === choiceReview.id);
    if (!selectedChoiceMatches) {
      await fail(nativeContractProblem, true);
      return;
    }
    await reloadAfterAccepted(t('live.person.choiceAccepted'));
  };

  const prepareApproval = async (revision: NativeRevision) => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareApprovals']>>;
    try {
      result = await port.prepareApprovals({
        contactIds: [contactId],
        expectedRevision: revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    const valid =
      result.envelope.value.items.length === 1 &&
      result.envelope.value.readyCount === 1 &&
      result.envelope.value.blockedCount === 0 &&
      result.envelope.value.items.every(
        item =>
          item.platform === capability.platform && item.contactId === contactId,
      );
    if (!valid) {
      await fail(nativeBridgeProblem);
      return;
    }
    setApprovalReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(false);
  };

  const confirmApproval = async () => {
    if (!approvalReview) {
      return;
    }
    setPending(true);
    let result: Awaited<ReturnType<LiveAppPort['confirmApprovals']>>;
    try {
      result = await port.confirmApprovals({
        handle: approvalReview.review.handle,
        expectedRevision: approvalReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      await fail(nativePlatformMismatchProblem, true);
      return;
    }
    await reloadAfterAccepted(t('live.person.approvalAccepted'));
  };

  if (detail.state.kind === 'loading') {
    return (
      <Screen includeTopInset testID="live-person-detail-screen">
        <Button
          label={t('live.person.back')}
          onPress={onBack}
          variant="ghost"
        />
        <LiveLoading label={t('live.person.loading')} />
      </Screen>
    );
  }
  if (detail.state.kind === 'error') {
    return (
      <Screen includeTopInset testID="live-person-detail-screen">
        <Button
          label={t('live.person.back')}
          onPress={onBack}
          variant="ghost"
        />
        <LiveError
          title={t('live.person.unavailable')}
          problem={detail.state.problem}
          onRetry={() => detail.reload()}
        />
      </Screen>
    );
  }

  const projection = detail.state.result.envelope.value;
  const revision = detail.state.result.envelope.revision;
  const enrollment = projection.summary.enrollment;
  const canEnroll =
    enrollment.kind === 'off' && projection.summary.readiness.kind === 'ready';
  const sourceRepairNeeded =
    (projection.summary.readiness.kind !== 'ready' &&
      projection.summary.readiness.reasons.some(reason =>
        GOOGLE_CONTACTS_REPAIR_ISSUES.has(reason),
      )) ||
    projection.phoneChoices.some(
      choice => choice.issue === 'phone-ambiguous-region',
    );
  const ambiguousRegionRepairNeeded = projection.phoneChoices.some(
    choice => choice.issue === 'phone-ambiguous-region',
  );
  const approval =
    enrollment.kind === 'enabled' || enrollment.kind === 'paused'
      ? enrollment.approval
      : undefined;
  const approvalText = !approval
    ? t('live.person.approvalNone')
    : approval.kind === 'missing'
    ? t('live.person.approvalMissing')
    : approval.kind === 'valid'
    ? t('live.person.approvalValid', {
        time: formatLiveInstant(approval.approvedAt, language),
      })
    : t('live.person.approvalInvalid', {
        reasons: [
          ...new Set(
            approval.reasons.map(reason =>
              t(approvalInvalidationMessageKey(reason)),
            ),
          ),
        ].join(' '),
      });
  const enrollmentText =
    enrollment.kind === 'paused'
      ? t('live.people.statusPaused', {
          reason: t(safeReasonMessageKey(enrollment.reason)),
        })
      : t(`live.common.${enrollment.kind}`);

  return (
    <Screen includeTopInset testID="live-person-detail-screen">
      <Button
        label={t('live.person.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-person-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {projection.summary.displayName}
      </AppText>
      <AppText color="muted">{t('live.person.privateBody')}</AppText>
      {detail.state.refreshProblem ? (
        <LiveRefreshProblem problem={detail.state.refreshProblem} />
      ) : null}
      <LiveActionFeedback problem={problem} message={message} />

      <Card>
        <KeyValue
          label={t('live.person.birthday')}
          value={
            projection.summary.birthdayLabel ?? t('live.common.needsReview')
          }
        />
        <KeyValue
          label={t('live.person.phone')}
          value={projection.summary.maskedPhone ?? t('live.common.needsReview')}
        />
        <KeyValue
          label={t('live.person.nextOccurrence')}
          value={projection.nextOccurrenceLabel ?? t('live.common.notPlanned')}
        />
        <KeyValue
          label={t('live.person.latestOutcome')}
          value={projection.lastOutcomeLabel ?? t('live.common.noOutcome')}
        />
        <StatusRow
          title={t('live.person.enrollment')}
          detail={enrollmentText}
          tone={enrollment.kind === 'enabled' ? 'positive' : 'neutral'}
          testID="live-person-enrollment"
        />
        <StatusRow
          title={t('live.person.approval')}
          detail={approvalText}
          tone={approval?.kind === 'valid' ? 'positive' : 'warning'}
        />
      </Card>

      <ReadinessBanner
        title={t(
          capability.platform === 'android'
            ? 'live.person.androidSafety'
            : 'live.person.iosSafety',
        )}
        detail={t(
          capability.platform === 'android'
            ? 'live.person.androidSafetyBody'
            : 'live.person.iosSafetyBody',
        )}
        tone="info"
      />

      {projection.selectedDestinationBlocked ? (
        <ReadinessBanner
          title={t('live.person.destinationBlockedTitle')}
          detail={t('live.person.destinationBlockedBody')}
          tone="warning"
          testID="live-person-destination-blocked"
        />
      ) : null}

      {sourceRepairNeeded ? (
        <Card>
          <ReadinessBanner
            title={t('live.person.googleContactsRepairTitle')}
            detail={t(
              ambiguousRegionRepairNeeded
                ? 'live.person.googleContactsRegionRepairBody'
                : 'live.person.googleContactsRepairBody',
            )}
            tone="warning"
          />
          <Button
            label={
              pending
                ? t('live.common.checking')
                : t('live.person.openGoogleContacts')
            }
            disabled={pending}
            onPress={openGoogleContacts}
            testID="live-person-open-google-contacts"
          />
        </Card>
      ) : null}

      {projection.phoneChoices.length > 0 ? (
        <>
          <SectionHeading
            title={t('live.person.phoneChoices')}
            supporting={t('live.person.phoneChoicesBody')}
          />
          {projection.phoneChoices.map(choice => (
            <Card key={choice.id}>
              <StatusRow
                title={`${choice.maskedDisplay} · ${choice.sourceLabel}`}
                detail={
                  choice.id === projection.selectedPhoneId
                    ? t('live.common.selected')
                    : choice.issue
                    ? t(safeReasonMessageKey(choice.issue))
                    : choice.selectable
                    ? t('live.common.availableReview')
                    : t('live.common.unavailable')
                }
                tone={
                  choice.id === projection.selectedPhoneId
                    ? 'positive'
                    : 'neutral'
                }
              />
              {choice.selectable && choice.id !== projection.selectedPhoneId ? (
                <Button
                  label={t('live.person.choosePhone')}
                  onPress={() =>
                    setChoiceReview({ kind: 'phone', id: choice.id })
                  }
                  variant="secondary"
                  testID={`live-choose-phone-${choice.id}`}
                />
              ) : null}
            </Card>
          ))}
        </>
      ) : null}

      {projection.birthdayChoices.length > 0 ? (
        <>
          <SectionHeading
            title={t('live.person.birthdayChoices')}
            supporting={t('live.person.birthdayChoicesBody')}
          />
          {projection.birthdayChoices.map(choice => (
            <Card key={choice.id}>
              <StatusRow
                title={choice.displayLabel}
                detail={
                  choice.id === projection.selectedBirthdayId
                    ? t('live.common.selected')
                    : choice.issue
                    ? t(safeReasonMessageKey(choice.issue))
                    : choice.selectable
                    ? t('live.common.availableReview')
                    : t('live.common.unavailable')
                }
                tone={
                  choice.id === projection.selectedBirthdayId
                    ? 'positive'
                    : 'neutral'
                }
              />
              {choice.selectable &&
              choice.id !== projection.selectedBirthdayId ? (
                <Button
                  label={t('live.person.chooseBirthday')}
                  onPress={() =>
                    setChoiceReview({
                      kind: 'birthday',
                      id: choice.id,
                      leapRequired: choice.issue === 'leap-policy-required',
                    })
                  }
                  variant="secondary"
                  testID={`live-choose-birthday-${choice.id}`}
                />
              ) : null}
            </Card>
          ))}
        </>
      ) : null}

      {choiceReview ? (
        <Card>
          <AppText variant="heading">{t('live.person.confirmChoice')}</AppText>
          {choiceReview.kind === 'birthday' && choiceReview.leapRequired ? (
            <>
              <SectionHeading title={t('live.person.leapPolicy')} />
              <View style={styles.choices} accessibilityRole="radiogroup">
                {(
                  [
                    ['feb-28', 'live.person.leapFeb28'],
                    ['mar-01', 'live.person.leapMar01'],
                    ['skip', 'live.person.leapSkip'],
                  ] as const
                ).map(([value, label]) => (
                  <ChoiceChip
                    key={value}
                    label={t(label)}
                    selected={choiceReview.leapPolicy === value}
                    onPress={() =>
                      setChoiceReview({ ...choiceReview, leapPolicy: value })
                    }
                  />
                ))}
              </View>
            </>
          ) : null}
          <Button
            label={t('live.person.confirmChoice')}
            disabled={
              pending ||
              (choiceReview.kind === 'birthday' &&
                choiceReview.leapRequired &&
                !choiceReview.leapPolicy)
            }
            onPress={() => confirmChoice(revision)}
            testID="live-confirm-choice"
          />
          <Button
            label={t('live.common.cancel')}
            onPress={() => setChoiceReview(undefined)}
            variant="secondary"
          />
        </Card>
      ) : null}

      {enrollmentReview ? (
        <Card>
          <AppText variant="heading">
            {t('live.person.confirmEnrollment')}
          </AppText>
          <AppText>
            {t('live.person.readyAttention', {
              ready: enrollmentReview.review.readyCount,
              attention: enrollmentReview.review.attentionCount,
            })}
          </AppText>
          <AppText color="muted">
            {t('live.person.confirmEnrollmentBody')}
          </AppText>
          <Button
            label={
              pending
                ? t('live.person.confirming')
                : t('live.person.confirmEnrollment')
            }
            disabled={pending}
            onPress={confirmEnrollment}
            testID="live-person-confirm-enrollment"
          />
          <Button
            label={t('live.person.cancelReview')}
            disabled={pending}
            onPress={() => setEnrollmentReview(undefined)}
            variant="secondary"
          />
        </Card>
      ) : null}

      {approvalReview ? (
        <Card>
          <AppText variant="heading">
            {t(
              capability.platform === 'android'
                ? 'live.person.approvalTitle'
                : 'live.person.iosApprovalTitle',
            )}
          </AppText>
          {approvalReview.review.items.map(item => (
            <View key={item.contactId} style={styles.reviewItem}>
              <AppText variant="label">{item.recipient}</AppText>
              <KeyValue
                label={t('live.person.phone')}
                value={item.maskedPhone}
              />
              <KeyValue
                label={t('live.person.birthday')}
                value={item.birthdayLabel}
              />
              <KeyValue
                label={t('live.common.message')}
                value={item.exactText}
              />
              {item.platform === 'android' ? (
                <>
                  <KeyValue
                    label={t('live.common.sim')}
                    value={item.simLabel}
                  />
                  <KeyValue
                    label={t('live.home.window')}
                    value={item.windowLabel}
                  />
                  <AppText color="muted">
                    {t('live.person.androidChargeDisclosure')}
                  </AppText>
                </>
              ) : (
                <ReadinessBanner
                  title={t('live.person.iosApprovalTitle')}
                  detail={t('live.person.iosApprovalBody')}
                  tone="info"
                />
              )}
              <AppText color="muted">
                {t(
                  item.platform === 'android'
                    ? 'live.person.androidConsentDisclosure'
                    : 'live.person.iosConsentDisclosure',
                )}
              </AppText>
            </View>
          ))}
          <Button
            label={t('live.person.approvalConfirm')}
            disabled={pending}
            onPress={confirmApproval}
            testID="live-confirm-approval"
          />
          <Button
            label={t('live.common.cancel')}
            onPress={() => setApprovalReview(undefined)}
            variant="secondary"
          />
        </Card>
      ) : null}

      {!enrollmentReview && canEnroll ? (
        <Button
          label={
            pending
              ? t('live.person.preparing')
              : t('live.person.reviewEnrollment')
          }
          disabled={pending}
          onPress={() => prepareEnrollment(revision)}
          testID="live-person-review-enrollment"
        />
      ) : null}
      {enrollment.kind === 'off' && !canEnroll ? (
        <ReadinessBanner
          title={t('live.person.enrollmentBlocked')}
          detail={t('live.person.enrollmentBlockedBody')}
          tone="warning"
        />
      ) : null}
      {(enrollment.kind === 'enabled' || enrollment.kind === 'paused') &&
      !projection.selectedDestinationBlocked ? (
        <Button
          label={t(
            capability.platform === 'android'
              ? 'live.person.approvalReview'
              : 'live.person.iosApprovalReview',
          )}
          disabled={pending}
          onPress={() => prepareApproval(revision)}
          testID="live-review-approval"
        />
      ) : null}
      {enrollment.kind === 'enabled' ? (
        <Button
          label={pending ? t('live.person.pausing') : t('live.person.pause')}
          disabled={pending}
          onPress={() => runRecipientMutation('pause', revision)}
          variant="secondary"
          testID="live-person-pause"
        />
      ) : null}
      {enrollment.kind === 'paused' || enrollment.kind === 'excluded' ? (
        <Button
          label={
            pending ? t('live.person.restoring') : t('live.person.restore')
          }
          disabled={pending}
          onPress={() => runRecipientMutation('restore', revision)}
          variant="secondary"
          testID="live-person-restore"
        />
      ) : null}
      {enrollment.kind !== 'excluded' && confirmExclude ? (
        <Card>
          <AppText variant="heading">{t('live.person.excludeTitle')}</AppText>
          <AppText>{t('live.person.excludeBody')}</AppText>
          <Button
            label={
              pending
                ? t('live.person.excluding')
                : t('live.person.excludeConfirm')
            }
            disabled={pending}
            onPress={() => runRecipientMutation('exclude', revision)}
            variant="danger"
            testID="live-person-confirm-exclude"
          />
          <Button
            label={t('live.person.excludeKeep')}
            disabled={pending}
            onPress={() => setConfirmExclude(false)}
            variant="secondary"
          />
        </Card>
      ) : null}
      {enrollment.kind !== 'excluded' && !confirmExclude ? (
        <Button
          label={t('live.person.exclude')}
          disabled={pending}
          onPress={() => setConfirmExclude(true)}
          variant="ghost"
          testID="live-person-exclude"
        />
      ) : null}
      {projection.selectedPhoneId && destinationBlockReview ? (
        <Card>
          <AppText variant="heading">
            {t(
              destinationBlockReview === 'block'
                ? 'live.person.destinationBlockTitle'
                : 'live.person.destinationUnblockTitle',
            )}
          </AppText>
          <AppText>
            {t(
              destinationBlockReview === 'block'
                ? 'live.person.destinationBlockBody'
                : 'live.person.destinationUnblockBody',
              {
                phone: bidiIsolate(projection.summary.maskedPhone ?? ''),
              },
            )}
          </AppText>
          <Button
            label={
              pending
                ? t('live.common.saving')
                : t(
                    destinationBlockReview === 'block'
                      ? 'live.person.destinationBlockConfirm'
                      : 'live.person.destinationUnblockConfirm',
                  )
            }
            disabled={pending}
            onPress={() =>
              runDestinationBlockMutation(destinationBlockReview, revision)
            }
            variant={
              destinationBlockReview === 'block' ? 'danger' : 'secondary'
            }
            testID={`live-person-confirm-destination-${destinationBlockReview}`}
          />
          <Button
            label={t('live.common.cancel')}
            disabled={pending}
            onPress={() => setDestinationBlockReview(undefined)}
            variant="secondary"
          />
        </Card>
      ) : null}
      {projection.selectedPhoneId && !destinationBlockReview ? (
        <Button
          label={t(
            projection.selectedDestinationBlocked
              ? 'live.person.destinationUnblock'
              : 'live.person.destinationBlock',
          )}
          disabled={pending}
          onPress={() =>
            setDestinationBlockReview(
              projection.selectedDestinationBlocked ? 'unblock' : 'block',
            )
          }
          variant={
            projection.selectedDestinationBlocked ? 'secondary' : 'ghost'
          }
          testID={
            projection.selectedDestinationBlocked
              ? 'live-person-unblock-destination'
              : 'live-person-block-destination'
          }
        />
      ) : null}
      <Button
        label={
          detail.state.refreshing
            ? t('live.common.refreshing')
            : t('live.person.refresh')
        }
        disabled={detail.state.refreshing || pending}
        onPress={() => detail.reload()}
        variant="secondary"
        testID="live-person-refresh"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  reviewItem: { gap: spacing.sm, paddingVertical: spacing.sm },
});
