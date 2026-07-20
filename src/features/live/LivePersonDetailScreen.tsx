import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Linking, StyleSheet, View } from 'react-native';

import type { ApprovalBatchReview } from '../../domain/approvals/model';
import type { LeapDayPolicy } from '../../domain/birthdays/model';
import type {
  ContactIssueCode,
  EnrollmentReview,
  PeopleMutationProjection,
} from '../../domain/contacts/model';
import type { PlatformCapability } from '../../domain/shared/platform';
import type {
  BirthdayChoiceId,
  ContactId,
  NativeRevision,
  PhoneChoiceId,
} from '../../domain/shared/brand';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  InlineReviewCard,
  KeyValue,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { bidiIsolate } from '../../localization/bidi';
import { formatLiveInstant } from '../../localization/formatLive';
import { useAppLocalization } from '../../localization/LocalizationProvider';
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

type TrustBinding = Readonly<{
  contactId: ContactId;
  destinationBlocked: boolean;
  destinationId?: PhoneChoiceId | undefined;
  generation: number;
  sourceRevision: NativeRevision;
}>;

type EnrollmentReviewState = TrustBinding &
  Readonly<{
    review: EnrollmentReview;
    revision: NativeRevision;
  }>;

type ApprovalReviewState = TrustBinding &
  Readonly<{
    mode: 'confirm' | 'view';
    review: ApprovalBatchReview;
    revision: NativeRevision;
  }>;

type ChoiceReview =
  | (TrustBinding &
      Readonly<{
        kind: 'phone';
        id: PhoneChoiceId;
        label: string;
      }>)
  | (TrustBinding &
      Readonly<{
        kind: 'birthday';
        id: BirthdayChoiceId;
        label: string;
        leapRequired: boolean;
        leapPolicy?: LeapDayPolicy | undefined;
      }>);

type RecipientReview = TrustBinding &
  Readonly<{
    kind: 'exclude' | 'pause';
  }>;

type DestinationReview = TrustBinding &
  Readonly<{
    kind: 'block' | 'unblock';
    maskedPhone: string;
    phoneId: PhoneChoiceId;
  }>;

type ReviewRefs = Readonly<{
  approval?: ApprovalReviewState | undefined;
  choice?: ChoiceReview | undefined;
  destination?: DestinationReview | undefined;
  enrollment?: EnrollmentReviewState | undefined;
  recipient?: RecipientReview | undefined;
}>;

type SyncNotice = Readonly<{
  detail: string;
  tone: 'info' | 'warning';
}>;

type MutationSettlement = Readonly<{
  contactId: ContactId;
  generation: number;
  request: number;
  invalidated: boolean;
}>;

const GOOGLE_CONTACTS_URL = 'https://contacts.google.com/';
const GOOGLE_CONTACTS_REPAIR_ISSUES = new Set<ContactIssueCode>([
  'birthday-missing',
  'phone-missing',
  'safe-given-name-missing',
  'source-contact-deleted',
  'stable-source-missing',
  'phone-ambiguous-region',
  'phone-invalid',
  'phone-blocked-form',
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
  const [syncNotice, setSyncNotice] = useState<SyncNotice>();
  const [manageExpanded, setManageExpanded] = useState(false);
  const [enrollmentReview, setEnrollmentReview] =
    useState<EnrollmentReviewState>();
  const [approvalReview, setApprovalReview] = useState<ApprovalReviewState>();
  const [choiceReview, setChoiceReview] = useState<ChoiceReview>();
  const [recipientReview, setRecipientReview] = useState<RecipientReview>();
  const [destinationReview, setDestinationReview] =
    useState<DestinationReview>();

  const mountedRef = useRef(true);
  const contactRef = useRef(contactId);
  const generationRef = useRef(0);
  const requestSequenceRef = useRef(0);
  const googleRequestSequenceRef = useRef(0);
  const boundRequestInFlightRef = useRef(false);
  const mutationSettlementRef = useRef<MutationSettlement | undefined>(
    undefined,
  );
  const protectedBindingRef = useRef<TrustBinding | undefined>(undefined);
  const reviewRef = useRef<ReviewRefs>({});
  const syncAfterGoogleContactsReturn = useRef(false);
  const projectionTruthRef = useRef<
    Readonly<{
      contactId?: ContactId | undefined;
      destinationBlocked: boolean;
      destinationId?: PhoneChoiceId | undefined;
      revision?: NativeRevision | undefined;
      usable: boolean;
    }>
  >({ destinationBlocked: false, usable: false });

  contactRef.current = contactId;
  reviewRef.current = {
    approval: approvalReview,
    choice: choiceReview,
    destination: destinationReview,
    enrollment: enrollmentReview,
    recipient: recipientReview,
  };

  const readyEnvelope =
    detail.state.kind === 'ready' ? detail.state.result.envelope : undefined;
  const projectionUsable =
    detail.state.kind === 'ready' &&
    !detail.state.refreshing &&
    !detail.state.refreshProblem &&
    readyEnvelope?.value.summary.id === contactId;
  const projectionRevision = readyEnvelope?.revision;
  const projectionContactId = readyEnvelope?.value.summary.id;
  const projectionDestinationId = readyEnvelope?.value.selectedPhoneId;
  const projectionDestinationBlocked =
    readyEnvelope?.value.selectedDestinationBlocked ?? false;

  projectionTruthRef.current = {
    contactId: projectionContactId,
    destinationBlocked: projectionDestinationBlocked,
    destinationId: projectionDestinationId,
    revision: projectionRevision,
    usable: projectionUsable,
  };

  const isBindingCurrent = useCallback((binding: TrustBinding) => {
    const truth = projectionTruthRef.current;
    return (
      mountedRef.current &&
      binding.generation === generationRef.current &&
      protectedBindingRef.current?.generation === binding.generation &&
      truth.usable &&
      truth.contactId === binding.contactId &&
      truth.revision === binding.sourceRevision &&
      truth.destinationId === binding.destinationId &&
      truth.destinationBlocked === binding.destinationBlocked
    );
  }, []);

  const retireProtectedState = useCallback(() => {
    generationRef.current += 1;
    requestSequenceRef.current += 1;
    boundRequestInFlightRef.current = false;
    mutationSettlementRef.current = undefined;
    protectedBindingRef.current = undefined;
    reviewRef.current = {};
    if (!mountedRef.current) return;
    setEnrollmentReview(undefined);
    setApprovalReview(undefined);
    setChoiceReview(undefined);
    setRecipientReview(undefined);
    setDestinationReview(undefined);
    setManageExpanded(false);
    setPending(false);
  }, []);

  const startBoundWork = useCallback(
    (sourceRevision: NativeRevision): TrustBinding | undefined => {
      if (boundRequestInFlightRef.current) return undefined;
      const truth = projectionTruthRef.current;
      if (
        !truth.usable ||
        truth.contactId !== contactId ||
        truth.revision !== sourceRevision
      ) {
        return undefined;
      }
      const destinationId = truth.destinationId;
      const destinationBlocked = truth.destinationBlocked;
      retireProtectedState();
      const binding: TrustBinding = {
        contactId,
        destinationBlocked,
        destinationId,
        generation: generationRef.current,
        sourceRevision,
      };
      protectedBindingRef.current = binding;
      return binding;
    },
    [contactId, retireProtectedState],
  );

  const beginBoundRequest = (binding: TrustBinding): number | undefined => {
    if (boundRequestInFlightRef.current || !isBindingCurrent(binding)) {
      return undefined;
    }
    const request = requestSequenceRef.current + 1;
    requestSequenceRef.current = request;
    boundRequestInFlightRef.current = true;
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    setSyncNotice(undefined);
    return request;
  };

  const isBoundRequestCurrent = (binding: TrustBinding, request: number) =>
    request === requestSequenceRef.current && isBindingCurrent(binding);

  const beginMutationRequest = (binding: TrustBinding): number | undefined => {
    const request = beginBoundRequest(binding);
    if (request === undefined) return undefined;
    mutationSettlementRef.current = {
      contactId: binding.contactId,
      generation: binding.generation,
      invalidated: false,
      request,
    };
    return request;
  };

  const isMutationSettlementCurrent = (
    binding: TrustBinding,
    request: number,
  ) => {
    const settlement = mutationSettlementRef.current;
    return (
      mountedRef.current &&
      contactRef.current === binding.contactId &&
      settlement?.contactId === binding.contactId &&
      settlement.generation === binding.generation &&
      settlement.request === request &&
      requestSequenceRef.current === request
    );
  };

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      generationRef.current += 1;
      requestSequenceRef.current += 1;
      googleRequestSequenceRef.current += 1;
      boundRequestInFlightRef.current = false;
      mutationSettlementRef.current = undefined;
      protectedBindingRef.current = undefined;
      reviewRef.current = {};
      syncAfterGoogleContactsReturn.current = false;
    };
  }, []);

  useEffect(() => {
    googleRequestSequenceRef.current += 1;
    syncAfterGoogleContactsReturn.current = false;
    retireProtectedState();
    setProblem(undefined);
    setMessage(undefined);
    setSyncNotice(undefined);
  }, [contactId, retireProtectedState]);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('contacts') ||
          event.areas.includes('automation')
        ) {
          const settlement = mutationSettlementRef.current;
          if (
            settlement &&
            settlement.contactId === contactRef.current &&
            settlement.request === requestSequenceRef.current
          ) {
            mutationSettlementRef.current = {
              ...settlement,
              invalidated: true,
            };
            return;
          }
          retireProtectedState();
        }
      }),
    [port, retireProtectedState],
  );

  useEffect(() => {
    const binding = protectedBindingRef.current;
    if (binding && !isBindingCurrent(binding)) {
      const settlement = mutationSettlementRef.current;
      if (
        settlement?.contactId === binding.contactId &&
        settlement.generation === binding.generation &&
        settlement.request === requestSequenceRef.current
      ) {
        return;
      }
      retireProtectedState();
    }
  }, [
    isBindingCurrent,
    projectionContactId,
    projectionDestinationBlocked,
    projectionDestinationId,
    projectionRevision,
    projectionUsable,
    retireProtectedState,
  ]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      googleRequestSequenceRef.current += 1;
      retireProtectedState();
      if (nextState !== 'active' || !syncAfterGoogleContactsReturn.current) {
        return;
      }

      syncAfterGoogleContactsReturn.current = false;
      const requestedContactId = contactRef.current;
      const request = googleRequestSequenceRef.current + 1;
      googleRequestSequenceRef.current = request;
      setPending(true);
      setProblem(undefined);
      setMessage(undefined);
      setSyncNotice(undefined);

      (async () => {
        let synced: Awaited<ReturnType<LiveAppPort['syncContacts']>>;
        try {
          synced = await port.syncContacts('user');
        } catch {
          synced = { kind: 'error', problem: nativeBridgeProblem };
        }
        if (
          !mountedRef.current ||
          request !== googleRequestSequenceRef.current ||
          requestedContactId !== contactRef.current
        ) {
          return;
        }
        if (synced.kind === 'error') {
          setProblem(synced.problem);
          setPending(false);
          return;
        }

        const syncProjection = synced.envelope.value;
        if (syncProjection.kind === 'fresh') {
          const refreshed = await detail.reload();
          if (
            !mountedRef.current ||
            request !== googleRequestSequenceRef.current ||
            requestedContactId !== contactRef.current
          ) {
            return;
          }
          if (refreshed.kind === 'error') {
            setProblem(refreshed.problem);
            setSyncNotice({
              detail: t('live.person.googleContactsReloadFailed'),
              tone: 'warning',
            });
          } else if (
            refreshed.envelope.value.summary.id !== requestedContactId ||
            refreshed.envelope.revision !== synced.envelope.revision
          ) {
            setProblem(nativeContractProblem);
            setSyncNotice({
              detail: t('live.person.googleContactsReloadFailed'),
              tone: 'warning',
            });
          } else {
            setMessage(t('live.person.googleContactsSynced'));
          }
          setPending(false);
          return;
        }

        if (syncProjection.kind === 'syncing') {
          setSyncNotice({
            detail: t('live.person.googleContactsSyncing'),
            tone: 'info',
          });
        } else {
          const reason =
            syncProjection.kind === 'stale' ||
            syncProjection.kind === 'failed-retained' ||
            syncProjection.kind === 'authorization-required'
              ? t(safeReasonMessageKey(syncProjection.reason))
              : undefined;
          setSyncNotice({
            detail: reason
              ? `${t('live.person.googleContactsSyncIncomplete')} ${reason}`
              : t('live.person.googleContactsSyncIncomplete'),
            tone: 'warning',
          });
        }
        setPending(false);
      })().catch(() => undefined);
    });
    return () => subscription.remove();
  }, [detail, port, retireProtectedState, t]);

  const handleBack = () => {
    googleRequestSequenceRef.current += 1;
    syncAfterGoogleContactsReturn.current = false;
    retireProtectedState();
    onBack();
  };

  const openGoogleContacts = async () => {
    if (!projectionTruthRef.current.usable) return;
    const requestedContactId = contactId;
    const request = googleRequestSequenceRef.current + 1;
    googleRequestSequenceRef.current = request;
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    setSyncNotice(undefined);
    syncAfterGoogleContactsReturn.current = true;
    try {
      const supported = await Linking.canOpenURL(GOOGLE_CONTACTS_URL);
      if (
        !mountedRef.current ||
        request !== googleRequestSequenceRef.current ||
        requestedContactId !== contactRef.current
      ) {
        return;
      }
      if (!supported) {
        syncAfterGoogleContactsReturn.current = false;
        setProblem(nativeBridgeProblem);
        setPending(false);
        return;
      }
      await Linking.openURL(GOOGLE_CONTACTS_URL);
      if (
        mountedRef.current &&
        request === googleRequestSequenceRef.current &&
        requestedContactId === contactRef.current
      ) {
        setMessage(t('live.person.googleContactsOpened'));
        setPending(false);
      }
    } catch {
      if (
        mountedRef.current &&
        request === googleRequestSequenceRef.current &&
        requestedContactId === contactRef.current
      ) {
        syncAfterGoogleContactsReturn.current = false;
        setProblem(nativeBridgeProblem);
        setPending(false);
      }
    }
  };

  const failBoundRequest = async (
    binding: TrustBinding,
    request: number,
    nextProblem: NativeProblem,
    reload = nextProblem.kind === 'stale-revision',
  ) => {
    if (!isBoundRequestCurrent(binding, request)) return;
    const requestedContactId = binding.contactId;
    retireProtectedState();
    if (!mountedRef.current || requestedContactId !== contactRef.current)
      return;
    setProblem(nextProblem);
    if (reload) await detail.reload();
  };

  const failMutationRequest = async (
    binding: TrustBinding,
    request: number,
    nextProblem: NativeProblem,
    reload = nextProblem.kind === 'stale-revision',
  ) => {
    if (!isMutationSettlementCurrent(binding, request)) return;
    const invalidated = mutationSettlementRef.current?.invalidated === true;
    const requestedContactId = binding.contactId;
    retireProtectedState();
    if (!mountedRef.current || requestedContactId !== contactRef.current) {
      return;
    }
    setProblem(nextProblem);
    if (reload || invalidated) await detail.reload();
  };

  const reloadAfterMutationAccepted = async (
    binding: TrustBinding,
    request: number,
    acceptedMessage: string,
  ) => {
    if (!isMutationSettlementCurrent(binding, request)) return;
    const requestedContactId = binding.contactId;
    retireProtectedState();
    const refreshed = await detail.reload();
    if (!mountedRef.current || requestedContactId !== contactRef.current) {
      return;
    }
    if (refreshed.kind === 'error') {
      setMessage(t('live.person.acceptedUnverified'));
      return;
    }
    if (refreshed.envelope.value.summary.id !== requestedContactId) {
      setProblem(nativeContractProblem);
      return;
    }
    setMessage(acceptedMessage);
  };

  const finishMutation = async (
    binding: TrustBinding,
    request: number,
    result: NativeResult<PeopleMutationProjection>,
    acceptedMessage: string,
  ) => {
    if (!isMutationSettlementCurrent(binding, request)) return;
    if (result.kind === 'error') {
      await failMutationRequest(binding, request, result.problem);
      return;
    }
    if (
      result.envelope.value.changedContactIds.length !== 1 ||
      result.envelope.value.changedContactIds[0] !== binding.contactId
    ) {
      await failMutationRequest(binding, request, nativeContractProblem, true);
      return;
    }
    await reloadAfterMutationAccepted(binding, request, acceptedMessage);
  };

  const prepareEnrollment = async (sourceRevision: NativeRevision) => {
    const binding = startBoundWork(sourceRevision);
    if (!binding) return;
    const request = beginBoundRequest(binding);
    if (request === undefined) return;
    let result: Awaited<ReturnType<LiveAppPort['prepareEnrollmentReview']>>;
    try {
      result = await port.prepareEnrollmentReview({
        contactIds: [binding.contactId],
        expectedRevision: binding.sourceRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isBoundRequestCurrent(binding, request)) return;
    if (result.kind === 'error') {
      await failBoundRequest(binding, request, result.problem);
      return;
    }
    const returnedContact = result.envelope.value.recipients[0];
    const validReview =
      result.envelope.value.explicitConfirmationRequired === true &&
      result.envelope.value.recipients.length === 1 &&
      result.envelope.value.readyCount === 1 &&
      result.envelope.value.attentionCount === 0 &&
      returnedContact?.id === binding.contactId &&
      returnedContact.readiness.kind === 'ready' &&
      returnedContact.enrollment.kind === 'off';
    if (!validReview) {
      await failBoundRequest(binding, request, nativeContractProblem, true);
      return;
    }
    const nextReview: EnrollmentReviewState = {
      ...binding,
      review: result.envelope.value,
      revision: result.envelope.revision,
    };
    reviewRef.current = { enrollment: nextReview };
    setEnrollmentReview(nextReview);
    boundRequestInFlightRef.current = false;
    setPending(false);
  };

  const confirmEnrollment = async () => {
    const review = reviewRef.current.enrollment;
    if (!review || !isBindingCurrent(review)) return;
    const request = beginMutationRequest(review);
    if (request === undefined) return;
    let result: Awaited<ReturnType<LiveAppPort['confirmEnrollment']>>;
    try {
      result = await port.confirmEnrollment({
        handle: review.review.handle,
        expectedRevision: review.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    await finishMutation(
      review,
      request,
      result,
      t('live.person.enrollmentAccepted'),
    );
  };

  const performRecipientMutation = async (
    kind: 'exclude' | 'pause' | 'restore',
    binding: TrustBinding,
  ) => {
    const request = beginMutationRequest(binding);
    if (request === undefined) return;
    const input = {
      contactId: binding.contactId,
      expectedRevision: binding.sourceRevision,
    };
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
      binding,
      request,
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

  const runDirectRestore = async (sourceRevision: NativeRevision) => {
    const binding = startBoundWork(sourceRevision);
    if (binding) await performRecipientMutation('restore', binding);
  };

  const confirmRecipientMutation = async () => {
    const review = reviewRef.current.recipient;
    if (!review || !isBindingCurrent(review)) return;
    await performRecipientMutation(review.kind, review);
  };

  const performDestinationMutation = async (review: DestinationReview) => {
    if (
      !isBindingCurrent(review) ||
      review.destinationId !== review.phoneId ||
      review.destinationBlocked !== (review.kind === 'unblock')
    ) {
      retireProtectedState();
      return;
    }
    const request = beginMutationRequest(review);
    if (request === undefined) return;
    const input = {
      contactId: review.contactId,
      expectedRevision: review.sourceRevision,
    };
    let result: NativeResult<PeopleMutationProjection>;
    try {
      result =
        review.kind === 'block'
          ? await port.blockRecipientDestination(input)
          : await port.unblockRecipientDestination(input);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    await finishMutation(
      review,
      request,
      result,
      t(
        review.kind === 'block'
          ? 'live.person.destinationBlockAccepted'
          : 'live.person.destinationUnblockAccepted',
      ),
    );
  };

  const confirmDestinationMutation = async () => {
    const review = reviewRef.current.destination;
    if (review) await performDestinationMutation(review);
  };

  const confirmChoice = async () => {
    const review = reviewRef.current.choice;
    if (!review || !isBindingCurrent(review)) return;
    if (
      review.kind === 'birthday' &&
      review.leapRequired &&
      !review.leapPolicy
    ) {
      return;
    }
    const request = beginMutationRequest(review);
    if (request === undefined) return;
    let result: Awaited<ReturnType<LiveAppPort['getPerson']>>;
    try {
      result =
        review.kind === 'phone'
          ? await port.choosePhone({
              contactId: review.contactId,
              phoneId: review.id,
              expectedRevision: review.sourceRevision,
            })
          : await port.chooseBirthday({
              contactId: review.contactId,
              birthdayId: review.id,
              ...(review.leapPolicy ? { leapPolicy: review.leapPolicy } : {}),
              expectedRevision: review.sourceRevision,
            });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isMutationSettlementCurrent(review, request)) return;
    if (result.kind === 'error') {
      await failMutationRequest(review, request, result.problem);
      return;
    }
    const selectedChoiceMatches =
      result.envelope.value.summary.id === review.contactId &&
      (review.kind === 'phone'
        ? result.envelope.value.selectedPhoneId === review.id
        : result.envelope.value.selectedBirthdayId === review.id);
    if (!selectedChoiceMatches) {
      await failMutationRequest(review, request, nativeContractProblem, true);
      return;
    }
    await reloadAfterMutationAccepted(
      review,
      request,
      t('live.person.choiceAccepted'),
    );
  };

  const prepareApproval = async (
    sourceRevision: NativeRevision,
    mode: 'confirm' | 'view',
  ) => {
    const binding = startBoundWork(sourceRevision);
    if (!binding) return;
    const request = beginBoundRequest(binding);
    if (request === undefined) return;
    let result: Awaited<ReturnType<LiveAppPort['prepareApprovals']>>;
    try {
      result = await port.prepareApprovals({
        contactIds: [binding.contactId],
        expectedRevision: binding.sourceRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isBoundRequestCurrent(binding, request)) return;
    if (result.kind === 'error') {
      await failBoundRequest(binding, request, result.problem);
      return;
    }
    const valid =
      result.envelope.value.explicitConfirmationRequired === true &&
      result.envelope.value.items.length === 1 &&
      result.envelope.value.readyCount === 1 &&
      result.envelope.value.blockedCount === 0 &&
      result.envelope.value.items.every(
        item =>
          item.platform === capability.platform &&
          item.contactId === binding.contactId,
      );
    if (!valid) {
      await failBoundRequest(binding, request, nativeContractProblem, true);
      return;
    }
    const nextReview: ApprovalReviewState = {
      ...binding,
      mode,
      review: result.envelope.value,
      revision: result.envelope.revision,
    };
    reviewRef.current = { approval: nextReview };
    setApprovalReview(nextReview);
    boundRequestInFlightRef.current = false;
    setPending(false);
  };

  const confirmApproval = async () => {
    const review = reviewRef.current.approval;
    if (!review || review.mode !== 'confirm' || !isBindingCurrent(review)) {
      return;
    }
    const request = beginMutationRequest(review);
    if (request === undefined) return;
    let result: Awaited<ReturnType<LiveAppPort['confirmApprovals']>>;
    try {
      result = await port.confirmApprovals({
        handle: review.review.handle,
        expectedRevision: review.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isMutationSettlementCurrent(review, request)) return;
    if (result.kind === 'error') {
      await failMutationRequest(review, request, result.problem);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      await failMutationRequest(
        review,
        request,
        nativePlatformMismatchProblem,
        true,
      );
      return;
    }
    await reloadAfterMutationAccepted(
      review,
      request,
      t('live.person.approvalAccepted'),
    );
  };

  const openChoiceReview = (
    choice:
      | Readonly<{
          kind: 'phone';
          id: PhoneChoiceId;
          label: string;
        }>
      | Readonly<{
          kind: 'birthday';
          id: BirthdayChoiceId;
          label: string;
          leapRequired: boolean;
        }>,
    sourceRevision: NativeRevision,
  ) => {
    const binding = startBoundWork(sourceRevision);
    if (!binding) return;
    const nextReview: ChoiceReview = { ...binding, ...choice };
    reviewRef.current = { choice: nextReview };
    setChoiceReview(nextReview);
    setProblem(undefined);
    setMessage(undefined);
  };

  const openRecipientReview = (
    kind: 'exclude' | 'pause',
    sourceRevision: NativeRevision,
  ) => {
    const binding = startBoundWork(sourceRevision);
    if (!binding) return;
    const nextReview: RecipientReview = { ...binding, kind };
    reviewRef.current = { recipient: nextReview };
    setRecipientReview(nextReview);
  };

  const openDestinationReview = (
    kind: 'block' | 'unblock',
    sourceRevision: NativeRevision,
    phoneId: PhoneChoiceId,
    maskedPhone: string,
  ) => {
    const binding = startBoundWork(sourceRevision);
    if (
      !binding ||
      binding.destinationId !== phoneId ||
      binding.destinationBlocked !== (kind === 'unblock')
    ) {
      if (binding) retireProtectedState();
      return;
    }
    const nextReview: DestinationReview = {
      ...binding,
      kind,
      maskedPhone,
      phoneId,
    };
    reviewRef.current = { destination: nextReview };
    setDestinationReview(nextReview);
  };

  if (detail.state.kind === 'loading') {
    return (
      <Screen includeTopInset testID="live-person-detail-screen">
        <Button
          label={t('live.person.back')}
          onPress={handleBack}
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
          onPress={handleBack}
          variant="ghost"
        />
        <LiveError
          title={t('live.person.unavailable')}
          problem={detail.state.problem}
          onRetry={() => {
            retireProtectedState();
            return detail.reload();
          }}
        />
      </Screen>
    );
  }

  const projection = detail.state.result.envelope.value;
  const revision = detail.state.result.envelope.revision;
  if (projection.summary.id !== contactId) {
    return (
      <Screen includeTopInset testID="live-person-detail-screen">
        <Button
          label={t('live.person.back')}
          onPress={handleBack}
          variant="ghost"
        />
        <LiveError
          title={t('live.person.unavailable')}
          problem={nativeContractProblem}
          onRetry={() => {
            retireProtectedState();
            return detail.reload();
          }}
        />
      </Screen>
    );
  }

  const enrollment = projection.summary.enrollment;
  const readinessReasons =
    projection.summary.readiness.kind === 'ready'
      ? []
      : projection.summary.readiness.reasons;
  const readinessReasonSet = new Set(readinessReasons);
  const structuralReasons = readinessReasons.filter(
    reason => reason !== 'approval-invalid',
  );
  const sourceRepairNeeded =
    readinessReasons.some(reason =>
      GOOGLE_CONTACTS_REPAIR_ISSUES.has(reason),
    ) ||
    projection.phoneChoices.some(
      choice =>
        choice.issue !== undefined &&
        GOOGLE_CONTACTS_REPAIR_ISSUES.has(choice.issue),
    );
  const ambiguousRegionRepairNeeded =
    readinessReasonSet.has('phone-ambiguous-region') ||
    projection.phoneChoices.some(
      choice => choice.issue === 'phone-ambiguous-region',
    );
  const phoneSelectionNeeded =
    !sourceRepairNeeded &&
    projection.phoneChoices.length > 0 &&
    (readinessReasonSet.has('phone-choice-required') ||
      readinessReasonSet.has('duplicate-destination') ||
      projection.selectedPhoneId === undefined ||
      projection.phoneChoices.some(
        choice =>
          choice.issue === 'phone-choice-required' ||
          choice.issue === 'duplicate-destination',
      ));
  const leapPolicyNeeded =
    readinessReasonSet.has('leap-policy-required') ||
    projection.birthdayChoices.some(
      choice => choice.issue === 'leap-policy-required',
    );
  const birthdaySelectionNeeded =
    !sourceRepairNeeded &&
    !phoneSelectionNeeded &&
    projection.birthdayChoices.length > 0 &&
    (readinessReasonSet.has('birthday-choice-required') ||
      readinessReasonSet.has('birthday-conflict') ||
      projection.birthdayChoices.some(
        choice =>
          choice.issue === 'birthday-choice-required' ||
          choice.issue === 'birthday-conflict',
      ) ||
      leapPolicyNeeded);
  const birthdayChoices = leapPolicyNeeded
    ? projection.birthdayChoices.filter(
        choice =>
          choice.id === projection.selectedBirthdayId ||
          choice.issue === 'leap-policy-required',
      )
    : projection.birthdayChoices;
  const approval =
    enrollment.kind === 'enabled' || enrollment.kind === 'paused'
      ? enrollment.approval
      : undefined;
  const structuralRepairNeeded =
    structuralReasons.length > 0 ||
    sourceRepairNeeded ||
    phoneSelectionNeeded ||
    birthdaySelectionNeeded ||
    projection.selectedDestinationBlocked;
  const job =
    enrollment.kind === 'off' || enrollment.kind === 'excluded'
      ? 'set-up'
      : approval?.kind === 'valid' && !structuralRepairNeeded
      ? 'view-approved'
      : 'review-changes';
  const canEnroll =
    projectionUsable &&
    enrollment.kind === 'off' &&
    projection.summary.readiness.kind === 'ready' &&
    !projection.selectedDestinationBlocked;
  const canReviewApproval =
    projectionUsable &&
    (enrollment.kind === 'enabled' || enrollment.kind === 'paused') &&
    structuralReasons.length === 0 &&
    !projection.selectedDestinationBlocked;

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

  const currentEnrollmentReview =
    enrollmentReview && isBindingCurrent(enrollmentReview)
      ? enrollmentReview
      : undefined;
  const currentApprovalReview =
    approvalReview && isBindingCurrent(approvalReview)
      ? approvalReview
      : undefined;
  const currentChoiceReview =
    choiceReview && isBindingCurrent(choiceReview) ? choiceReview : undefined;
  const currentRecipientReview =
    recipientReview && isBindingCurrent(recipientReview)
      ? recipientReview
      : undefined;
  const currentDestinationReview =
    destinationReview && isBindingCurrent(destinationReview)
      ? destinationReview
      : undefined;
  const reviewStateExists = Boolean(
    enrollmentReview ||
      approvalReview ||
      choiceReview ||
      recipientReview ||
      destinationReview,
  );
  const protectedFlowActive =
    reviewStateExists || (pending && protectedBindingRef.current !== undefined);

  return (
    <Screen includeTopInset testID="live-person-detail-screen">
      <Button
        label={t('live.person.back')}
        onPress={handleBack}
        variant="ghost"
        testID="live-person-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {projection.summary.displayName}
      </AppText>
      <AppText color="muted">{t('live.person.privateBody')}</AppText>

      {detail.state.refreshProblem ? (
        <>
          <LiveRefreshProblem problem={detail.state.refreshProblem} />
          <Button
            label={t('live.person.checkAgain')}
            onPress={() => {
              retireProtectedState();
              detail.reload().catch(() => undefined);
            }}
            variant="secondary"
            testID="live-person-check-again"
          />
        </>
      ) : detail.state.refreshing ? (
        <LiveLoading label={t('live.common.checkingState')} />
      ) : null}
      <LiveActionFeedback problem={problem} message={message} />
      {syncNotice ? (
        <ReadinessBanner
          title={t('live.person.googleContactsRepairTitle')}
          detail={syncNotice.detail}
          tone={syncNotice.tone}
          testID="live-person-google-sync-notice"
        />
      ) : null}

      <Card testID="live-person-summary">
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
        <StatusRow
          title={t('live.person.enrollment')}
          detail={enrollmentText}
          tone={enrollment.kind === 'enabled' ? 'positive' : 'neutral'}
          testID="live-person-enrollment"
        />
        {approval ? (
          <StatusRow
            title={t('live.person.approval')}
            detail={approvalText}
            tone={approval.kind === 'valid' ? 'positive' : 'warning'}
          />
        ) : null}
        {job === 'view-approved' ? (
          <>
            <KeyValue
              label={t('live.person.nextOccurrence')}
              value={
                projection.nextOccurrenceLabel ?? t('live.common.notPlanned')
              }
            />
            <KeyValue
              label={t('live.person.latestOutcome')}
              value={projection.lastOutcomeLabel ?? t('live.common.noOutcome')}
            />
          </>
        ) : null}
      </Card>

      {projectionUsable && !reviewStateExists ? (
        <ReadinessBanner
          title={t(
            job === 'set-up'
              ? 'live.person.jobSetupTitle'
              : job === 'review-changes'
              ? 'live.person.jobReviewChangesTitle'
              : 'live.person.jobApprovedTitle',
          )}
          detail={t(
            job === 'set-up'
              ? 'live.person.jobSetupBody'
              : job === 'review-changes'
              ? 'live.person.jobReviewChangesBody'
              : 'live.person.jobApprovedBody',
          )}
          tone={
            job === 'view-approved'
              ? 'positive'
              : job === 'review-changes'
              ? 'warning'
              : 'info'
          }
          testID={`live-person-job-${job}`}
        />
      ) : null}

      {currentChoiceReview ? (
        <Card testID="live-person-choice-review">
          <AppText variant="heading">{t('live.person.confirmChoice')}</AppText>
          <KeyValue
            label={t(
              currentChoiceReview.kind === 'phone'
                ? 'live.person.phone'
                : 'live.person.birthday',
            )}
            value={currentChoiceReview.label}
          />
          {currentChoiceReview.kind === 'birthday' &&
          currentChoiceReview.leapRequired ? (
            <>
              <SectionHeading title={t('live.person.leapPolicy')} />
              <View
                style={styles.choices}
                accessibilityLabel={t('live.person.leapPolicy')}
                accessibilityRole="radiogroup"
              >
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
                    selected={currentChoiceReview.leapPolicy === value}
                    onPress={() =>
                      setChoiceReview(current => {
                        if (!current || current.kind !== 'birthday') {
                          return current;
                        }
                        const next = { ...current, leapPolicy: value };
                        reviewRef.current = { choice: next };
                        return next;
                      })
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
              (currentChoiceReview.kind === 'birthday' &&
                currentChoiceReview.leapRequired &&
                !currentChoiceReview.leapPolicy)
            }
            onPress={() => confirmChoice().catch(() => undefined)}
            testID="live-confirm-choice"
          />
          <Button
            label={t('live.common.cancel')}
            disabled={pending}
            onPress={retireProtectedState}
            variant="secondary"
          />
        </Card>
      ) : null}

      {currentEnrollmentReview ? (
        <InlineReviewCard
          reviewKey={currentEnrollmentReview.review.handle}
          testID="live-person-enrollment-review"
          title={t('live.person.confirmEnrollment')}
        >
          <AppText>
            {t('live.person.readyAttention', {
              ready: currentEnrollmentReview.review.readyCount,
              attention: currentEnrollmentReview.review.attentionCount,
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
            onPress={() => confirmEnrollment().catch(() => undefined)}
            testID="live-person-confirm-enrollment"
          />
          <Button
            label={t('live.person.cancelReview')}
            disabled={pending}
            onPress={retireProtectedState}
            variant="secondary"
          />
        </InlineReviewCard>
      ) : null}

      {currentApprovalReview ? (
        <InlineReviewCard
          reviewKey={`${currentApprovalReview.review.handle}-${currentApprovalReview.mode}`}
          testID="live-person-approval-review"
          title={t(
            capability.platform === 'android'
              ? 'live.person.approvalTitle'
              : 'live.person.iosApprovalTitle',
          )}
        >
          {currentApprovalReview.review.items.map(item => (
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
                  <KeyValue
                    label={t('live.automation.segmentCount')}
                    value={t('live.common.parts', {
                      count: item.segmentCount,
                    })}
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
          {currentApprovalReview.mode === 'confirm' ? (
            <>
              <Button
                label={t('live.person.approvalConfirm')}
                disabled={pending}
                onPress={() => confirmApproval().catch(() => undefined)}
                testID="live-confirm-approval"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={pending}
                onPress={retireProtectedState}
                variant="secondary"
              />
            </>
          ) : (
            <Button
              label={t('live.common.close')}
              disabled={pending}
              onPress={retireProtectedState}
              variant="secondary"
              testID="live-close-approved-review"
            />
          )}
        </InlineReviewCard>
      ) : null}

      {currentRecipientReview ? (
        <Card testID={`live-person-${currentRecipientReview.kind}-review`}>
          <AppText variant="heading">
            {t(
              currentRecipientReview.kind === 'pause'
                ? 'live.person.pauseTitle'
                : 'live.person.excludeTitle',
            )}
          </AppText>
          <AppText>
            {t(
              currentRecipientReview.kind === 'pause'
                ? 'live.person.pauseBody'
                : 'live.person.excludeBody',
            )}
          </AppText>
          <Button
            label={
              pending
                ? t(
                    currentRecipientReview.kind === 'pause'
                      ? 'live.person.pausing'
                      : 'live.person.excluding',
                  )
                : t(
                    currentRecipientReview.kind === 'pause'
                      ? 'live.person.pause'
                      : 'live.person.excludeConfirm',
                  )
            }
            disabled={pending}
            onPress={() => confirmRecipientMutation().catch(() => undefined)}
            variant={
              currentRecipientReview.kind === 'exclude' ? 'danger' : 'secondary'
            }
            testID={
              currentRecipientReview.kind === 'pause'
                ? 'live-person-confirm-pause'
                : 'live-person-confirm-exclude'
            }
          />
          <Button
            label={
              currentRecipientReview.kind === 'exclude'
                ? t('live.person.excludeKeep')
                : t('live.common.cancel')
            }
            disabled={pending}
            onPress={retireProtectedState}
            variant="secondary"
          />
        </Card>
      ) : null}

      {currentDestinationReview ? (
        <Card testID="live-person-destination-review">
          <AppText variant="heading">
            {t(
              currentDestinationReview.kind === 'block'
                ? 'live.person.destinationBlockTitle'
                : 'live.person.destinationUnblockTitle',
            )}
          </AppText>
          <AppText>
            {t(
              currentDestinationReview.kind === 'block'
                ? 'live.person.destinationBlockBody'
                : 'live.person.destinationUnblockBody',
              { phone: bidiIsolate(currentDestinationReview.maskedPhone) },
            )}
          </AppText>
          <Button
            label={
              pending
                ? t('live.common.saving')
                : t(
                    currentDestinationReview.kind === 'block'
                      ? 'live.person.destinationBlockConfirm'
                      : 'live.person.destinationUnblockConfirm',
                  )
            }
            disabled={pending}
            onPress={() => confirmDestinationMutation().catch(() => undefined)}
            variant={
              currentDestinationReview.kind === 'block' ? 'danger' : 'secondary'
            }
            testID={`live-person-confirm-destination-${currentDestinationReview.kind}`}
          />
          <Button
            label={t('live.common.cancel')}
            disabled={pending}
            onPress={retireProtectedState}
            variant="secondary"
          />
        </Card>
      ) : null}

      {!protectedFlowActive && projectionUsable ? (
        <>
          {sourceRepairNeeded ? (
            <Card testID="live-person-source-repair">
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
                onPress={() => openGoogleContacts().catch(() => undefined)}
                testID="live-person-open-google-contacts"
              />
            </Card>
          ) : phoneSelectionNeeded ? (
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
                  {choice.selectable &&
                  choice.id !== projection.selectedPhoneId ? (
                    <Button
                      label={t('live.person.choosePhoneNamed', {
                        phone: bidiIsolate(choice.maskedDisplay),
                      })}
                      onPress={() =>
                        openChoiceReview(
                          {
                            kind: 'phone',
                            id: choice.id,
                            label: choice.maskedDisplay,
                          },
                          revision,
                        )
                      }
                      variant="secondary"
                      testID={`live-choose-phone-${choice.id}`}
                    />
                  ) : null}
                </Card>
              ))}
            </>
          ) : birthdaySelectionNeeded ? (
            <>
              <SectionHeading
                title={t('live.person.birthdayChoices')}
                supporting={t('live.person.birthdayChoicesBody')}
              />
              {birthdayChoices.map(choice => {
                const selected = choice.id === projection.selectedBirthdayId;
                const needsLeapPolicy =
                  choice.issue === 'leap-policy-required' ||
                  (selected && leapPolicyNeeded);
                return (
                  <Card key={choice.id}>
                    <StatusRow
                      title={choice.displayLabel}
                      detail={
                        selected && needsLeapPolicy
                          ? t(safeReasonMessageKey('leap-policy-required'))
                          : selected
                          ? t('live.common.selected')
                          : choice.issue
                          ? t(safeReasonMessageKey(choice.issue))
                          : choice.selectable
                          ? t('live.common.availableReview')
                          : t('live.common.unavailable')
                      }
                      tone={selected ? 'positive' : 'neutral'}
                    />
                    {(choice.selectable || (selected && needsLeapPolicy)) &&
                    (!selected || needsLeapPolicy) ? (
                      <Button
                        label={t('live.person.chooseBirthdayNamed', {
                          birthday: bidiIsolate(choice.displayLabel),
                        })}
                        onPress={() =>
                          openChoiceReview(
                            {
                              kind: 'birthday',
                              id: choice.id,
                              label: choice.displayLabel,
                              leapRequired: needsLeapPolicy,
                            },
                            revision,
                          )
                        }
                        variant="secondary"
                        testID={`live-choose-birthday-${choice.id}`}
                      />
                    ) : null}
                  </Card>
                );
              })}
            </>
          ) : projection.selectedDestinationBlocked ? (
            <ReadinessBanner
              title={t('live.person.destinationBlockedTitle')}
              detail={t('live.person.destinationBlockedBody')}
              tone="warning"
              testID="live-person-destination-blocked"
            />
          ) : enrollment.kind === 'off' ? (
            canEnroll ? (
              <Button
                label={
                  pending
                    ? t('live.person.preparing')
                    : t('live.person.reviewEnrollment')
                }
                disabled={pending}
                onPress={() =>
                  prepareEnrollment(revision).catch(() => undefined)
                }
                testID="live-person-review-enrollment"
              />
            ) : (
              <ReadinessBanner
                title={t('live.person.enrollmentBlocked')}
                detail={t('live.person.enrollmentBlockedBody')}
                tone="warning"
              />
            )
          ) : enrollment.kind === 'enabled' || enrollment.kind === 'paused' ? (
            canReviewApproval ? (
              <Button
                label={t(
                  approval?.kind === 'valid'
                    ? 'live.person.viewApproved'
                    : 'live.person.reviewChanges',
                )}
                disabled={pending}
                onPress={() =>
                  prepareApproval(
                    revision,
                    approval?.kind === 'valid' ? 'view' : 'confirm',
                  ).catch(() => undefined)
                }
                testID="live-review-approval"
              />
            ) : (
              <ReadinessBanner
                title={t('live.person.enrollmentBlocked')}
                detail={t('live.person.enrollmentBlockedBody')}
                tone="warning"
              />
            )
          ) : null}

          <Button
            label={t(
              manageExpanded ? 'live.person.hideManage' : 'live.person.manage',
            )}
            disabled={!projectionUsable || pending}
            expanded={manageExpanded}
            onPress={() => setManageExpanded(current => !current)}
            testID="live-person-manage-toggle"
            variant="secondary"
          />

          {manageExpanded ? (
            <Card testID="live-person-manage">
              <SectionHeading title={t('live.person.manage')} />
              {enrollment.kind === 'enabled' ? (
                <>
                  <AppText color="muted">{t('live.person.pauseBody')}</AppText>
                  <Button
                    label={t('live.person.pause')}
                    disabled={pending}
                    onPress={() => openRecipientReview('pause', revision)}
                    variant="secondary"
                    testID="live-person-pause"
                  />
                </>
              ) : enrollment.kind === 'paused' ? (
                <Button
                  label={
                    pending
                      ? t('live.person.resuming')
                      : t('live.person.resume')
                  }
                  disabled={pending}
                  onPress={() =>
                    runDirectRestore(revision).catch(() => undefined)
                  }
                  variant="secondary"
                  testID="live-person-resume"
                />
              ) : enrollment.kind === 'excluded' ? (
                <Button
                  label={
                    pending
                      ? t('live.person.includingAgain')
                      : t('live.person.includeAgain')
                  }
                  disabled={pending}
                  onPress={() =>
                    runDirectRestore(revision).catch(() => undefined)
                  }
                  variant="secondary"
                  testID="live-person-include"
                />
              ) : null}

              {enrollment.kind !== 'excluded' ? (
                <>
                  <AppText color="muted">
                    {t('live.person.excludeBody')}
                  </AppText>
                  <Button
                    label={t('live.person.exclude')}
                    disabled={pending}
                    onPress={() => openRecipientReview('exclude', revision)}
                    variant="ghost"
                    testID="live-person-exclude"
                  />
                </>
              ) : null}

              {projection.selectedPhoneId ? (
                <>
                  <AppText color="muted">
                    {t(
                      projection.selectedDestinationBlocked
                        ? 'live.person.destinationUnblockBody'
                        : 'live.person.destinationBlockBody',
                      {
                        phone: bidiIsolate(
                          projection.summary.maskedPhone ?? '',
                        ),
                      },
                    )}
                  </AppText>
                  <Button
                    label={t(
                      projection.selectedDestinationBlocked
                        ? 'live.person.destinationUnblock'
                        : 'live.person.destinationBlock',
                    )}
                    disabled={pending}
                    onPress={() =>
                      openDestinationReview(
                        projection.selectedDestinationBlocked
                          ? 'unblock'
                          : 'block',
                        revision,
                        projection.selectedPhoneId as PhoneChoiceId,
                        projection.summary.maskedPhone ?? '',
                      )
                    }
                    variant={
                      projection.selectedDestinationBlocked
                        ? 'secondary'
                        : 'ghost'
                    }
                    testID={
                      projection.selectedDestinationBlocked
                        ? 'live-person-unblock-destination'
                        : 'live-person-block-destination'
                    }
                  />
                </>
              ) : null}
            </Card>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  reviewItem: { gap: spacing.sm, paddingVertical: spacing.sm },
});
