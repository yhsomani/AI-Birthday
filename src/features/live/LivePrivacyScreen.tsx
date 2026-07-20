import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type { AccountProjection } from '../../domain/account/model';
import type {
  CurrentPrivacyOperationProjection,
  LatestDeletionReceiptProjection,
  PrivacyActionKind,
  PrivacyActionReview,
  PrivacyInventory,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type { LifecycleRepairKind } from '../../domain/device/model';
import type {
  NativeRevision,
  PrivacyOperationId,
} from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  InlineReviewCard,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
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
  nativeProblemReference,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';
import { LiveCloudPrivacyBoundary } from './LiveCloudPrivacyBoundary';
import { LivePrivacyInventory } from './LivePrivacyInventory';

type PrivacyScreenActionKind = Exclude<PrivacyActionKind, 'clear-activity'>;

const privacyActions: readonly Readonly<{
  kind: PrivacyScreenActionKind;
  label: TranslationKey;
  detail: TranslationKey;
}>[] = [
  {
    kind: 'clear-gemini-templates',
    label: 'live.privacy.clearTemplates',
    detail: 'live.privacy.consequence.templatesRemoved',
  },
  {
    kind: 'disconnect-contacts',
    label: 'live.privacy.disconnect',
    detail: 'live.privacy.consequence.googleDataRemoved',
  },
  {
    kind: 'revoke-google-access',
    label: 'live.privacy.revoke',
    detail: 'live.privacy.consequence.googleScopesRevoked',
  },
  {
    kind: 'sign-out-retain',
    label: 'live.privacy.signOutKeep',
    detail: 'live.privacy.consequence.sameAccountRetained',
  },
  {
    kind: 'sign-out-wipe',
    label: 'live.privacy.signOutWipe',
    detail: 'live.privacy.consequence.localDataErased',
  },
  {
    kind: 'wipe-local-data',
    label: 'live.privacy.wipeLocal',
    detail: 'live.privacy.consequence.localDataErased',
  },
  {
    kind: 'delete-account',
    label: 'live.privacy.deleteAccount',
    detail: 'live.privacy.consequence.remoteDeletionStarted',
  },
];

const actionFor = (kind: PrivacyActionKind) =>
  privacyActions.find(action => action.kind === kind);

const lifecycleRepairActions: readonly Readonly<{
  kind: LifecycleRepairKind;
  label: TranslationKey;
}>[] = [
  { kind: 'disconnect-contacts', label: 'live.privacy.disconnect' },
  { kind: 'revoke-google-access', label: 'live.privacy.revoke' },
  { kind: 'sign-out-wipe', label: 'live.privacy.signOutWipe' },
  { kind: 'wipe-local-data', label: 'live.privacy.wipeLocal' },
];

const lifecycleChangingActions = new Set<PrivacyActionKind>([
  'disconnect-contacts',
  'revoke-google-access',
  'sign-out-retain',
  'sign-out-wipe',
  'delete-account',
  'wipe-local-data',
]);

const privacyConsequenceKeys: Readonly<Record<string, TranslationKey>> = {
  'privacy.consequence.activity-hidden':
    'live.privacy.consequence.activityHidden',
  'privacy.consequence.safety-retained':
    'live.privacy.consequence.safetyRetained',
  'privacy.consequence.gemini-templates-removed':
    'live.privacy.consequence.templatesRemoved',
  'privacy.consequence.reapproval-required':
    'live.privacy.consequence.reapprovalRequired',
  'privacy.consequence.automation-paused':
    'live.privacy.consequence.automationPaused',
  'privacy.consequence.same-account-setup-retained':
    'live.privacy.consequence.sameAccountRetained',
  'privacy.consequence.google-working-data-removed':
    'live.privacy.consequence.googleDataRemoved',
  'privacy.consequence.all-google-scopes-revoked':
    'live.privacy.consequence.googleScopesRevoked',
  'privacy.consequence.remote-deletion-drain-started':
    'live.privacy.consequence.remoteDeletionStarted',
  'privacy.consequence.local-data-erased-after-drain':
    'live.privacy.consequence.localEraseAfterDrain',
  'privacy.consequence.local-data-erased':
    'live.privacy.consequence.localDataErased',
  'privacy.consequence.local-data': 'live.privacy.consequence.localDataErased',
  'privacy.consequence.external-sms':
    'live.privacy.consequence.externalCopiesRemain',
  'privacy.consequence.android-reset-paused':
    'live.privacy.consequence.androidResetPaused',
  'privacy.consequence.android-test-required':
    'live.privacy.consequence.androidTestRequired',
};

const operationStateKeys: Record<
  PrivacyOperationProjection['kind'],
  TranslationKey
> = {
  queued: 'live.privacy.state.queued',
  pausing: 'live.privacy.state.pausing',
  'remote-draining': 'live.privacy.state.remoteDraining',
  'remote-unknown': 'live.privacy.state.remoteUnknown',
  'local-wiping': 'live.privacy.state.localWiping',
  'remote-pending': 'live.privacy.state.remotePending',
  verifying: 'live.privacy.state.verifying',
  complete: 'live.privacy.state.complete',
  failed: 'live.privacy.state.failed',
};

type ReviewState = Readonly<{
  review: PrivacyActionReview;
  revision: NativeRevision;
  sourceRevision: NativeRevision;
  source: 'action-list' | 'pending-deletion-local-wipe';
  deletionOperationId: PrivacyOperationId | undefined;
}>;

type PrivacyTruth = Readonly<{
  usable: boolean;
  revision?: NativeRevision | undefined;
  canStartNewAction: boolean;
  canPreparePendingDeletionWipe: boolean;
  pendingDeletionOperationId: PrivacyOperationId | undefined;
}>;

type AuthoritativePrivacyTruth = Readonly<{
  revision: NativeRevision;
  account: AccountProjection;
  currentOperation: CurrentPrivacyOperationProjection;
  deletionReceipt: LatestDeletionReceiptProjection;
  inventory: PrivacyInventory;
}>;

type AuthoritativeTruthReload =
  | Readonly<{ kind: 'ok'; truth: AuthoritativePrivacyTruth }>
  | Readonly<{ kind: 'error'; problem: NativeProblem }>;

const sameProjectionValue = (left: unknown, right: unknown): boolean => {
  if (Object.is(left, right)) return true;
  if (
    left === null ||
    right === null ||
    typeof left !== 'object' ||
    typeof right !== 'object'
  ) {
    return false;
  }
  if (Array.isArray(left) || Array.isArray(right)) {
    return (
      Array.isArray(left) &&
      Array.isArray(right) &&
      left.length === right.length &&
      left.every((value, index) => sameProjectionValue(value, right[index]))
    );
  }
  const leftRecord = left as Readonly<Record<string, unknown>>;
  const rightRecord = right as Readonly<Record<string, unknown>>;
  const leftKeys = Object.keys(leftRecord).sort();
  const rightKeys = Object.keys(rightRecord).sort();
  return (
    leftKeys.length === rightKeys.length &&
    leftKeys.every(
      (key, index) =>
        key === rightKeys[index] &&
        sameProjectionValue(leftRecord[key], rightRecord[key]),
    )
  );
};

const corroboratesOperation = (
  truth: AuthoritativePrivacyTruth,
  revision: NativeRevision,
  operation: PrivacyOperationProjection,
): boolean => {
  if (revision !== truth.revision) return false;
  if (truth.deletionReceipt.kind !== 'none') {
    return (
      truth.deletionReceipt.kind !== 'unavailable' &&
      operation.action === 'delete-account' &&
      sameProjectionValue(operation, truth.deletionReceipt)
    );
  }
  return (
    truth.currentOperation.kind !== 'none' &&
    truth.currentOperation.kind !== 'unavailable' &&
    sameProjectionValue(operation, truth.currentOperation)
  );
};

export function LivePrivacyScreen({
  onBack,
  onLifecycleStateChange,
  onOpenHelpLegal,
  platform,
  port,
}: {
  onBack: () => void;
  onLifecycleStateChange: () => Promise<unknown>;
  onOpenHelpLegal: () => void;
  platform: PlatformCapability['platform'];
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadInventory = useCallback(() => port.getInventory(), [port]);
  const inventory = useLiveProjection(loadInventory, port, [
    'account',
    'privacy',
  ]);
  const loadAccount = useCallback(() => port.getAccount(), [port]);
  const account = useLiveProjection(loadAccount, port, ['account', 'privacy']);
  const loadDeletionReceipt = useCallback(
    () => port.getLatestDeletionReceipt(),
    [port],
  );
  const deletionReceipt = useLiveProjection(loadDeletionReceipt, port, [
    'account',
    'privacy',
  ]);
  const loadCurrentOperation = useCallback(
    () => port.getCurrentOperation(),
    [port],
  );
  const currentOperation = useLiveProjection(loadCurrentOperation, port, [
    'account',
    'privacy',
  ]);
  const [review, setReview] = useState<ReviewState>();
  const [operation, setOperation] = useState<PrivacyOperationProjection>();
  const [dataDetailsExpanded, setDataDetailsExpanded] = useState(false);
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const nativeContractInvalid =
    problem !== undefined &&
    problem.kind === 'internal' &&
    problem.supportCode === nativeProblemReference(nativeContractProblem);
  const mountedRef = useRef(true);
  const protectedGenerationRef = useRef(0);
  const protectedRequestPendingRef = useRef(false);
  const protectedSourceRevisionRef = useRef<NativeRevision | undefined>(
    undefined,
  );
  const protectedPhaseRef = useRef<'idle' | 'prepare' | 'review' | 'confirm'>(
    'idle',
  );
  const confirmInFlightRef = useRef(false);
  const reviewRef = useRef<ReviewState | undefined>(undefined);
  const truthRef = useRef<PrivacyTruth>({
    usable: false,
    canStartNewAction: false,
    canPreparePendingDeletionWipe: false,
    pendingDeletionOperationId: undefined,
  });
  const operationRequestPendingRef = useRef(false);

  const inventoryUsable =
    inventory.state.kind === 'ready' &&
    !inventory.state.refreshing &&
    !inventory.state.refreshProblem;
  const accountUsable =
    account.state.kind === 'ready' &&
    !account.state.refreshing &&
    !account.state.refreshProblem;
  const currentOperationUsable =
    currentOperation.state.kind === 'ready' &&
    !currentOperation.state.refreshing &&
    !currentOperation.state.refreshProblem;
  const deletionReceiptUsable =
    deletionReceipt.state.kind === 'ready' &&
    !deletionReceipt.state.refreshing &&
    !deletionReceipt.state.refreshProblem;
  const inventoryRevision =
    inventory.state.kind === 'ready'
      ? inventory.state.result.envelope.revision
      : undefined;
  const accountRevision =
    account.state.kind === 'ready'
      ? account.state.result.envelope.revision
      : undefined;
  const currentOperationRevision =
    currentOperation.state.kind === 'ready'
      ? currentOperation.state.result.envelope.revision
      : undefined;
  const deletionReceiptRevision =
    deletionReceipt.state.kind === 'ready'
      ? deletionReceipt.state.result.envelope.revision
      : undefined;
  const truthUsable =
    inventoryUsable &&
    accountUsable &&
    currentOperationUsable &&
    deletionReceiptUsable &&
    inventoryRevision !== undefined &&
    accountRevision === inventoryRevision &&
    currentOperationRevision === inventoryRevision &&
    deletionReceiptRevision === inventoryRevision;
  const stableCurrentOperation =
    currentOperationUsable && currentOperation.state.kind === 'ready'
      ? currentOperation.state.result.envelope.value
      : undefined;
  const stableDeletionReceipt =
    deletionReceiptUsable && deletionReceipt.state.kind === 'ready'
      ? deletionReceipt.state.result.envelope.value
      : undefined;
  const projectedOperation = currentOperationUsable
    ? stableCurrentOperation?.kind !== 'none' &&
      stableCurrentOperation?.kind !== 'unavailable'
      ? stableCurrentOperation
      : undefined
    : operation;
  const deletionReceiptAllowsCurrentOperation =
    stableDeletionReceipt?.kind === 'none';
  const visibleDeletionOperation: PrivacyOperationProjection | undefined =
    stableDeletionReceipt?.kind === 'remote-draining' ||
    stableDeletionReceipt?.kind === 'remote-unknown' ||
    stableDeletionReceipt?.kind === 'complete'
      ? stableDeletionReceipt
      : stableDeletionReceipt?.kind === 'none' &&
        projectedOperation?.action === 'delete-account'
      ? projectedOperation
      : undefined;
  const visibleNonDeletionOperation =
    deletionReceiptAllowsCurrentOperation &&
    projectedOperation?.action !== 'delete-account'
      ? projectedOperation
      : undefined;
  const currentAllowsNewAction =
    stableCurrentOperation?.kind === 'none' ||
    ((stableCurrentOperation?.kind === 'complete' ||
      stableCurrentOperation?.kind === 'failed') &&
      stableCurrentOperation.action !== 'delete-account');
  const localAllowsNewAction =
    operation === undefined ||
    operation.kind === 'complete' ||
    operation.kind === 'failed';
  const deletionAllowsNewAction = stableDeletionReceipt?.kind === 'none';
  const pendingDeletionNeedsLocalWipe =
    truthUsable &&
    !nativeContractInvalid &&
    visibleDeletionOperation?.action === 'delete-account' &&
    (visibleDeletionOperation.kind === 'remote-pending' ||
      visibleDeletionOperation.kind === 'remote-draining') &&
    !(
      visibleDeletionOperation.kind === 'remote-draining' &&
      'localDataErased' in visibleDeletionOperation
    ) &&
    stableDeletionReceipt?.kind === 'none';
  const sameAccountDeletionRetryAvailable =
    truthUsable &&
    !nativeContractInvalid &&
    stableDeletionReceipt?.kind === 'remote-unknown' &&
    stableDeletionReceipt.sameAccountRetryAvailable;
  const canStartNewAction =
    truthUsable &&
    !nativeContractInvalid &&
    currentAllowsNewAction &&
    localAllowsNewAction &&
    deletionAllowsNewAction &&
    !pending &&
    review === undefined;
  const lifecycleRepairRequired =
    platform === 'android' &&
    accountUsable &&
    account.state.kind === 'ready' &&
    account.state.result.envelope.value.kind === 'cleanup-pending' &&
    account.state.result.envelope.value.operation === 'repair';
  const currentReview =
    review && truthUsable && review.sourceRevision === inventoryRevision
      ? review
      : undefined;
  truthRef.current = {
    usable: truthUsable,
    revision: inventoryRevision,
    canStartNewAction:
      !nativeContractInvalid &&
      currentAllowsNewAction &&
      localAllowsNewAction &&
      deletionAllowsNewAction,
    canPreparePendingDeletionWipe: pendingDeletionNeedsLocalWipe,
    pendingDeletionOperationId: pendingDeletionNeedsLocalWipe
      ? visibleDeletionOperation.id
      : undefined,
  };
  reviewRef.current = currentReview;

  const clearProtectedRefs = useCallback(() => {
    protectedRequestPendingRef.current = false;
    protectedSourceRevisionRef.current = undefined;
    protectedPhaseRef.current = 'idle';
    confirmInFlightRef.current = false;
    reviewRef.current = undefined;
  }, []);

  const retireProtectedWork = useCallback(() => {
    protectedGenerationRef.current += 1;
    protectedRequestPendingRef.current = false;
    protectedSourceRevisionRef.current = undefined;
    protectedPhaseRef.current = 'idle';
    confirmInFlightRef.current = false;
    reviewRef.current = undefined;
    if (!mountedRef.current) return;
    setReview(undefined);
    setPending(false);
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      protectedGenerationRef.current += 1;
      clearProtectedRefs();
      operationRequestPendingRef.current = false;
    };
  }, [clearProtectedRefs]);

  useEffect(
    () =>
      port.subscribeInvalidations(() => {
        if (confirmInFlightRef.current) return;
        if (
          protectedPhaseRef.current !== 'idle' ||
          reviewRef.current !== undefined
        ) {
          retireProtectedWork();
        }
      }),
    [port, retireProtectedWork],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (
        nextState === 'active' &&
        !confirmInFlightRef.current &&
        (protectedPhaseRef.current !== 'idle' ||
          reviewRef.current !== undefined)
      ) {
        retireProtectedWork();
      }
    });
    return () => subscription.remove();
  }, [retireProtectedWork]);

  useEffect(() => {
    const sourceRevision = protectedSourceRevisionRef.current;
    if (sourceRevision === undefined || confirmInFlightRef.current) return;
    if (!truthUsable || inventoryRevision !== sourceRevision) {
      retireProtectedWork();
    }
  }, [inventoryRevision, retireProtectedWork, truthUsable]);

  const reloadAuthoritativeTruth =
    async (): Promise<AuthoritativeTruthReload> => {
      const [
        accountResult,
        currentOperationResult,
        deletionReceiptResult,
        inventoryResult,
      ] = await Promise.all([
        account.reload(),
        currentOperation.reload(),
        deletionReceipt.reload(),
        inventory.reload(),
      ]);
      if (accountResult.kind === 'error') return accountResult;
      if (currentOperationResult.kind === 'error')
        return currentOperationResult;
      if (deletionReceiptResult.kind === 'error') return deletionReceiptResult;
      if (inventoryResult.kind === 'error') return inventoryResult;
      const revision = accountResult.envelope.revision;
      if (
        currentOperationResult.envelope.revision !== revision ||
        deletionReceiptResult.envelope.revision !== revision ||
        inventoryResult.envelope.revision !== revision
      ) {
        return { kind: 'error', problem: nativeContractProblem };
      }
      if (mountedRef.current) {
        setOperation(undefined);
      }
      return {
        kind: 'ok',
        truth: {
          revision,
          account: accountResult.envelope.value,
          currentOperation: currentOperationResult.envelope.value,
          deletionReceipt: deletionReceiptResult.envelope.value,
          inventory: inventoryResult.envelope.value,
        },
      };
    };

  const beginOperationRequest = () => {
    if (
      operationRequestPendingRef.current ||
      protectedRequestPendingRef.current
    )
      return false;
    operationRequestPendingRef.current = true;
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    return true;
  };

  const finishOperationRequest = () => {
    operationRequestPendingRef.current = false;
    if (mountedRef.current) setPending(false);
  };

  const prepareActionReview = async (
    kind: PrivacyScreenActionKind,
    source: ReviewState['source'],
  ) => {
    const truth = truthRef.current;
    const allowed =
      source === 'action-list'
        ? truth.canStartNewAction
        : truth.canPreparePendingDeletionWipe;
    if (
      !truth.usable ||
      truth.revision === undefined ||
      !allowed ||
      (source === 'pending-deletion-local-wipe' &&
        truth.pendingDeletionOperationId === undefined) ||
      protectedRequestPendingRef.current ||
      operationRequestPendingRef.current
    ) {
      return;
    }
    const sourceRevision = truth.revision;
    const generation = protectedGenerationRef.current + 1;
    protectedGenerationRef.current = generation;
    protectedRequestPendingRef.current = true;
    protectedSourceRevisionRef.current = sourceRevision;
    protectedPhaseRef.current = 'prepare';
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    setReview(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareAction']>>;
    try {
      result = await port.prepareAction({
        kind,
        expectedRevision: sourceRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !mountedRef.current ||
      generation !== protectedGenerationRef.current ||
      protectedPhaseRef.current !== 'prepare' ||
      protectedSourceRevisionRef.current !== sourceRevision
    ) {
      return;
    }
    const preparedReview =
      result.kind === 'ok' ? result.envelope.value : undefined;
    const consequenceKeys = preparedReview?.consequenceKeys ?? [];
    const validReview =
      preparedReview !== undefined &&
      preparedReview.kind === kind &&
      preparedReview.externalSmsCopiesNotErased === true &&
      consequenceKeys.length > 0 &&
      consequenceKeys.every(
        (value, index) =>
          value.length > 0 &&
          consequenceKeys.indexOf(value) === index &&
          Object.prototype.hasOwnProperty.call(privacyConsequenceKeys, value),
      );
    if (result.kind === 'error' || !validReview) {
      clearProtectedRefs();
      if (
        result.kind === 'ok' ||
        (result.kind === 'error' && result.problem.kind === 'stale-revision')
      ) {
        await reloadAuthoritativeTruth();
      }
      if (!mountedRef.current) return;
      setProblem(
        result.kind === 'error' ? result.problem : nativeContractProblem,
      );
      setPending(false);
      return;
    }
    const nextReview: ReviewState = {
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision,
      source,
      deletionOperationId:
        source === 'pending-deletion-local-wipe'
          ? truth.pendingDeletionOperationId
          : undefined,
    };
    protectedRequestPendingRef.current = false;
    protectedPhaseRef.current = 'review';
    reviewRef.current = nextReview;
    setReview(nextReview);
    setPending(false);
  };

  const confirm = async () => {
    const activeReview = reviewRef.current;
    const truth = truthRef.current;
    if (
      !activeReview ||
      !truth.usable ||
      truth.revision !== activeReview.sourceRevision ||
      protectedRequestPendingRef.current ||
      operationRequestPendingRef.current
    ) {
      retireProtectedWork();
      return;
    }
    const generation = protectedGenerationRef.current + 1;
    protectedGenerationRef.current = generation;
    protectedRequestPendingRef.current = true;
    protectedPhaseRef.current = 'confirm';
    confirmInFlightRef.current = true;
    reviewRef.current = undefined;
    setReview(undefined);
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmAction']>>;
    try {
      result = await port.confirmAction({
        handle: activeReview.review.handle,
        expectedRevision: activeReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !mountedRef.current ||
      generation !== protectedGenerationRef.current ||
      protectedPhaseRef.current !== 'confirm'
    ) {
      return;
    }
    clearProtectedRefs();
    // Android and iOS both preserve the existing durable delete operation when
    // a pending-deletion review authorizes only its remaining local wipe.
    const preservesPendingDeletionOperation =
      result.kind === 'ok' &&
      activeReview.source === 'pending-deletion-local-wipe' &&
      activeReview.review.kind === 'wipe-local-data' &&
      activeReview.deletionOperationId !== undefined &&
      result.envelope.value.action === 'delete-account' &&
      result.envelope.value.id === activeReview.deletionOperationId;
    const confirmedOperation =
      result.kind === 'ok' &&
      (result.envelope.value.action === activeReview.review.kind ||
        preservesPendingDeletionOperation)
        ? result.envelope.value
        : undefined;
    const authoritativeReload = await reloadAuthoritativeTruth();
    if (!mountedRef.current) return;
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else if (!confirmedOperation) {
      setProblem(nativeContractProblem);
    } else if (authoritativeReload.kind === 'error') {
      setProblem(authoritativeReload.problem);
    } else if (
      !corroboratesOperation(
        authoritativeReload.truth,
        result.envelope.revision,
        confirmedOperation,
      )
    ) {
      setProblem(nativeContractProblem);
    } else {
      if (lifecycleChangingActions.has(activeReview.review.kind)) {
        await onLifecycleStateChange().catch(() => undefined);
      }
      if (!mountedRef.current) return;
      if (confirmedOperation.kind === 'complete') {
        setMessage(t('live.privacy.operationComplete'));
      } else if (confirmedOperation.kind === 'failed') {
        setMessage(t('live.privacy.operationFailed'));
      } else {
        setMessage(t('live.privacy.operationPending'));
      }
    }
    setPending(false);
  };

  const refreshOperation = async () => {
    const current = visibleNonDeletionOperation;
    if (!current || !beginOperationRequest()) return;
    let result: Awaited<ReturnType<LiveAppPort['getOperation']>>;
    try {
      result = await port.getOperation(current.id);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (mountedRef.current) setProblem(result.problem);
    } else {
      const authoritativeReload = await reloadAuthoritativeTruth();
      if (!mountedRef.current) {
        finishOperationRequest();
        return;
      }
      if (authoritativeReload.kind === 'error') {
        setProblem(authoritativeReload.problem);
      } else if (
        !corroboratesOperation(
          authoritativeReload.truth,
          result.envelope.revision,
          result.envelope.value,
        )
      ) {
        setProblem(nativeContractProblem);
      } else if (result.envelope.value.kind === 'complete') {
        setMessage(t('live.privacy.operationComplete'));
      }
    }
    finishOperationRequest();
  };

  const resumeOperation = async () => {
    const current = visibleNonDeletionOperation;
    if (
      !current ||
      current.kind === 'complete' ||
      current.kind === 'failed' ||
      !beginOperationRequest()
    ) {
      return;
    }
    let result: Awaited<ReturnType<LiveAppPort['resumeOperation']>>;
    try {
      result = await port.resumeOperation(current.id);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (mountedRef.current) setProblem(result.problem);
    } else {
      const authoritativeReload = await reloadAuthoritativeTruth();
      if (!mountedRef.current) {
        finishOperationRequest();
        return;
      }
      if (authoritativeReload.kind === 'error') {
        setProblem(authoritativeReload.problem);
      } else if (
        !corroboratesOperation(
          authoritativeReload.truth,
          result.envelope.revision,
          result.envelope.value,
        )
      ) {
        setProblem(nativeContractProblem);
      } else {
        setMessage(
          result.envelope.value.kind === 'complete'
            ? t('live.privacy.operationComplete')
            : t('live.privacy.operationResumed'),
        );
      }
    }
    finishOperationRequest();
  };

  const repairLifecycleState = async (kind: LifecycleRepairKind) => {
    if (!beginOperationRequest()) return;
    let result: Awaited<ReturnType<LiveAppPort['repairLifecycleState']>>;
    try {
      result = await port.repairLifecycleState({ kind });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (mountedRef.current) setProblem(result.problem);
      finishOperationRequest();
      return;
    }
    const validOperation = result.envelope.value.action === kind;
    const authoritativeReload = await reloadAuthoritativeTruth();
    if (!mountedRef.current) {
      finishOperationRequest();
      return;
    }
    if (!validOperation) {
      setProblem(nativeContractProblem);
    } else if (authoritativeReload.kind === 'error') {
      setProblem(authoritativeReload.problem);
    } else if (
      !corroboratesOperation(
        authoritativeReload.truth,
        result.envelope.revision,
        result.envelope.value,
      )
    ) {
      setProblem(nativeContractProblem);
    } else {
      await onLifecycleStateChange().catch(() => undefined);
      if (mountedRef.current) {
        setMessage(
          result.envelope.value.kind === 'complete'
            ? t('live.privacy.operationComplete')
            : t('live.privacy.operationResumed'),
        );
      }
    }
    finishOperationRequest();
  };

  const checkAccountDeletionStatus = async () => {
    if (!beginOperationRequest()) return;
    let result: Awaited<ReturnType<LiveAppPort['checkAccountDeletionStatus']>>;
    try {
      result = await port.checkAccountDeletionStatus();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (mountedRef.current) setProblem(result.problem);
      finishOperationRequest();
      return;
    }
    const authoritativeReload = await reloadAuthoritativeTruth();
    if (!mountedRef.current) {
      finishOperationRequest();
      return;
    }
    const status = result.envelope.value;
    if (authoritativeReload.kind === 'error') {
      setProblem(authoritativeReload.problem);
    } else if (
      result.envelope.revision !== authoritativeReload.truth.revision ||
      !sameProjectionValue(status, authoritativeReload.truth.deletionReceipt)
    ) {
      setProblem(nativeContractProblem);
    } else {
      setMessage(
        status.kind === 'complete'
          ? t('live.privacy.deletionCompleteBody')
          : status.kind === 'remote-draining'
          ? t('live.privacy.deletionStillRunning')
          : t('live.privacy.deletionProofUnavailable'),
      );
    }
    finishOperationRequest();
  };

  const retryPendingDeletionWithGoogle = async () => {
    if (!sameAccountDeletionRetryAvailable || !beginOperationRequest()) return;
    let result: Awaited<ReturnType<LiveAppPort['continueWithGoogle']>>;
    try {
      result = await port.continueWithGoogle();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (mountedRef.current) setProblem(result.problem);
      finishOperationRequest();
      return;
    }
    const authoritativeReload = await reloadAuthoritativeTruth();
    if (!mountedRef.current) {
      finishOperationRequest();
      return;
    }
    if (authoritativeReload.kind === 'error') {
      setProblem(authoritativeReload.problem);
    } else if (
      result.envelope.revision !== authoritativeReload.truth.revision ||
      !sameProjectionValue(
        result.envelope.value,
        authoritativeReload.truth.account,
      )
    ) {
      setProblem(nativeContractProblem);
    } else {
      await onLifecycleStateChange().catch(() => undefined);
      if (mountedRef.current) {
        setMessage(t('live.privacy.deletionRetrySubmitted'));
      }
    }
    finishOperationRequest();
  };

  const deletionRecoveryUnavailable =
    stableDeletionReceipt?.kind === 'unavailable' ||
    (stableDeletionReceipt?.kind === 'none' &&
      stableCurrentOperation?.kind === 'unavailable');
  const operationLoadProblem =
    deletionReceipt.state.kind === 'error'
      ? deletionReceipt.state.problem
      : deletionReceiptAllowsCurrentOperation &&
        currentOperation.state.kind === 'error'
      ? currentOperation.state.problem
      : undefined;
  const operationRefreshProblem =
    deletionReceipt.state.kind === 'ready' &&
    deletionReceipt.state.refreshProblem
      ? deletionReceipt.state.refreshProblem
      : deletionReceiptAllowsCurrentOperation &&
        currentOperation.state.kind === 'ready' &&
        currentOperation.state.refreshProblem
      ? currentOperation.state.refreshProblem
      : account.state.kind === 'ready' && account.state.refreshProblem
      ? account.state.refreshProblem
      : undefined;

  const renderAction = (
    kind: PrivacyScreenActionKind,
    variant: 'secondary' | 'danger' = 'secondary',
  ) => {
    const action = actionFor(kind);
    if (!action) return null;
    return (
      <React.Fragment key={kind}>
        <AppText color="muted">{t(action.detail)}</AppText>
        <Button
          label={t(action.label)}
          disabled={pending}
          onPress={() => prepareActionReview(kind, 'action-list')}
          variant={variant}
          testID={`live-privacy-${kind}`}
        />
      </React.Fragment>
    );
  };

  return (
    <Screen includeTopInset testID="live-privacy-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-privacy-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.privacy.title')}
      </AppText>
      <AppText color="muted">{t('live.privacy.body')}</AppText>
      <Card>
        <AppText variant="heading">
          {t('live.privacy.screenCaptureTitle')}
        </AppText>
        <AppText color="muted">{t('live.privacy.screenCaptureBody')}</AppText>
      </Card>
      <LiveActionFeedback problem={problem} message={message} />
      {operationRefreshProblem ? (
        <LiveRefreshProblem problem={operationRefreshProblem} />
      ) : null}
      {deletionRecoveryUnavailable ? (
        <ReadinessBanner
          title={t('live.privacy.recoveryUnavailable')}
          detail={t('live.privacy.recoveryUnavailableBody')}
          tone="critical"
        />
      ) : null}
      {operationLoadProblem ? (
        <LiveError
          title={t('live.privacy.operationUnavailable')}
          problem={operationLoadProblem}
          onRetry={() => reloadAuthoritativeTruth().then(() => undefined)}
        />
      ) : null}
      {account.state.kind === 'error' ? (
        <LiveError
          title={t('live.settings.accountUnavailable')}
          problem={account.state.problem}
          onRetry={() => reloadAuthoritativeTruth().then(() => undefined)}
        />
      ) : null}
      {visibleDeletionOperation ? (
        <Card testID="live-privacy-deletion-status">
          <AppText variant="heading">
            {t(
              visibleDeletionOperation.kind === 'remote-draining'
                ? 'live.privacy.deletionDrainingTitle'
                : visibleDeletionOperation.kind === 'remote-unknown'
                ? 'live.privacy.deletionUnknownTitle'
                : visibleDeletionOperation.kind === 'complete'
                ? 'live.privacy.deletionCompleteTitle'
                : 'live.privacy.deletionPendingTitle',
            )}
          </AppText>
          <StatusRow
            title={t('live.privacy.operationState', {
              kind: t(operationStateKeys[visibleDeletionOperation.kind]),
            })}
            tone={
              visibleDeletionOperation.kind === 'remote-unknown' ||
              visibleDeletionOperation.kind === 'failed'
                ? 'critical'
                : visibleDeletionOperation.kind === 'complete'
                ? 'positive'
                : 'warning'
            }
          />
          {'reason' in visibleDeletionOperation ? (
            <StatusRow
              title={t(safeReasonMessageKey(visibleDeletionOperation.reason))}
              tone="warning"
            />
          ) : null}
          <AppText color="muted">
            {t(
              visibleDeletionOperation.kind === 'remote-draining'
                ? 'live.privacy.deletionDrainingBody'
                : visibleDeletionOperation.kind === 'remote-unknown'
                ? 'live.privacy.deletionUnknownBody'
                : visibleDeletionOperation.kind === 'complete'
                ? 'live.privacy.deletionCompleteBody'
                : 'live.privacy.deletionPendingBody',
            )}
          </AppText>
          {visibleDeletionOperation.kind === 'remote-unknown' ||
          (visibleDeletionOperation.kind !== 'remote-draining' &&
            visibleDeletionOperation.kind !== 'complete') ? (
            <AppText color="muted">{t('live.privacy.external')}</AppText>
          ) : null}
          {visibleDeletionOperation.kind !== 'complete' ? (
            <Button
              label={
                pending
                  ? t('live.privacy.checkingDeletion')
                  : t('live.privacy.checkDeletion')
              }
              disabled={pending}
              onPress={checkAccountDeletionStatus}
              testID="live-privacy-check-deletion"
            />
          ) : null}
          {sameAccountDeletionRetryAvailable ? (
            <Button
              label={
                pending
                  ? t('live.privacy.deletionRetrying')
                  : t('live.privacy.deletionRetryWithGoogle')
              }
              disabled={pending}
              onPress={retryPendingDeletionWithGoogle}
              variant="secondary"
              testID="live-privacy-retry-deletion-google"
            />
          ) : null}
          <Button
            label={t('live.privacy.openDeletionHelp')}
            disabled={pending}
            onPress={onOpenHelpLegal}
            variant="secondary"
            testID="live-privacy-deletion-help"
          />
        </Card>
      ) : null}
      {lifecycleRepairRequired ? (
        <Card>
          <AppText variant="heading">{t('live.privacy.repairTitle')}</AppText>
          <StatusRow title={t('live.privacy.repairBody')} tone="critical" />
          {lifecycleRepairActions.map(action => (
            <Button
              key={action.kind}
              label={t(action.label)}
              disabled={pending}
              onPress={() => repairLifecycleState(action.kind)}
              variant="secondary"
              testID={`live-privacy-repair-${action.kind}`}
            />
          ))}
        </Card>
      ) : null}

      {inventory.state.kind === 'loading' ? (
        <LiveLoading label={t('live.privacy.loading')} />
      ) : null}
      {inventory.state.kind === 'error' ? (
        <LiveError
          title={t('live.privacy.unavailable')}
          problem={inventory.state.problem}
          onRetry={() => inventory.reload()}
        />
      ) : null}
      {inventory.state.kind === 'ready' ? (
        <>
          {inventory.state.refreshProblem ? (
            <LiveRefreshProblem problem={inventory.state.refreshProblem} />
          ) : null}
          <SectionHeading title={t('live.privacy.groupDataOnPhone')} />
          <Button
            label={t(
              dataDetailsExpanded
                ? 'live.privacy.hideDataDetails'
                : 'live.privacy.showDataDetails',
            )}
            expanded={dataDetailsExpanded}
            onPress={() => setDataDetailsExpanded(expanded => !expanded)}
            variant="secondary"
            testID="live-privacy-data-details-toggle"
          />
          {dataDetailsExpanded ? (
            <>
              <LivePrivacyInventory
                inventory={inventory.state.result.envelope.value}
                platform={platform}
              />
              <LiveCloudPrivacyBoundary platform={platform} />
            </>
          ) : null}
          {canStartNewAction ? (
            <>
              <Card testID="live-privacy-group-data-on-phone">
                {renderAction('clear-gemini-templates')}
              </Card>
              <SectionHeading title={t('live.privacy.groupContactsGoogle')} />
              <Card testID="live-privacy-group-contacts-google">
                {renderAction('disconnect-contacts')}
                {renderAction('revoke-google-access')}
              </Card>
              <SectionHeading title={t('live.privacy.groupSignOut')} />
              <Card testID="live-privacy-group-sign-out">
                {renderAction('sign-out-retain')}
                {renderAction('sign-out-wipe', 'danger')}
              </Card>
              <SectionHeading title={t('live.privacy.wipeLocal')} />
              <Card testID="live-privacy-group-wipe-local">
                {renderAction('wipe-local-data', 'danger')}
              </Card>
              <SectionHeading title={t('live.privacy.deleteAccount')} />
              <Card testID="live-privacy-group-delete-account">
                {renderAction('delete-account', 'danger')}
                <AppText color="muted">{t('live.privacy.external')}</AppText>
              </Card>
            </>
          ) : null}
          {pendingDeletionNeedsLocalWipe ? (
            <Card>
              <AppText variant="heading">
                {t('live.privacy.pendingWipeTitle')}
              </AppText>
              <ReadinessBanner
                title={t('live.privacy.pendingWipeWarningTitle')}
                detail={t('live.privacy.pendingWipeBody')}
                tone="critical"
              />
              <Button
                label={
                  pending
                    ? t('live.privacy.preparing')
                    : t('live.privacy.pendingWipeAction')
                }
                disabled={pending || currentReview !== undefined}
                onPress={() =>
                  prepareActionReview(
                    'wipe-local-data',
                    'pending-deletion-local-wipe',
                  )
                }
                variant="danger"
                testID="live-privacy-pending-deletion-wipe"
              />
            </Card>
          ) : null}
          {currentReview ? (
            <InlineReviewCard
              reviewKey={currentReview.review.handle}
              testID="live-privacy-review"
              title={
                currentReview.source === 'pending-deletion-local-wipe'
                  ? t('live.privacy.pendingWipeReviewTitle')
                  : t('live.privacy.reviewTitle')
              }
            >
              <StatusRow
                title={t(
                  actionFor(currentReview.review.kind)?.label ??
                    'live.privacy.reviewTitle',
                )}
                tone="warning"
              />
              {currentReview.review.consequenceKeys.map(value => (
                <StatusRow
                  key={value}
                  title={t(privacyConsequenceKeys[value]!)}
                  tone="warning"
                />
              ))}
              {currentReview.review.preissuedPermitMayFinish ? (
                <ReadinessBanner
                  title={t('live.privacy.preissued')}
                  detail={t('live.privacy.external')}
                  tone="critical"
                />
              ) : null}
              {currentReview.review.remoteConnectionRequired ? (
                <StatusRow
                  title={t('live.privacy.remoteRequired')}
                  tone="warning"
                />
              ) : null}
              {currentReview.source === 'pending-deletion-local-wipe' ? (
                <AppText color="muted">
                  {t('live.privacy.pendingWipeReviewBody')}
                </AppText>
              ) : null}
              {!currentReview.review.preissuedPermitMayFinish ? (
                <AppText color="muted">{t('live.privacy.external')}</AppText>
              ) : null}
              <Button
                label={
                  pending
                    ? t('live.privacy.confirming')
                    : t('live.privacy.confirm')
                }
                disabled={pending}
                onPress={confirm}
                variant="danger"
                testID="live-privacy-confirm"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={pending}
                onPress={retireProtectedWork}
                variant="secondary"
              />
            </InlineReviewCard>
          ) : null}
          {visibleNonDeletionOperation ? (
            <Card>
              <AppText variant="heading">{t('live.privacy.operation')}</AppText>
              <StatusRow
                title={t('live.privacy.operationState', {
                  kind: t(operationStateKeys[visibleNonDeletionOperation.kind]),
                })}
                tone={
                  visibleNonDeletionOperation.kind === 'failed'
                    ? 'critical'
                    : 'info'
                }
              />
              {'reason' in visibleNonDeletionOperation ? (
                <StatusRow
                  title={t(
                    safeReasonMessageKey(visibleNonDeletionOperation.reason),
                  )}
                  tone="warning"
                />
              ) : null}
              <AppText color="muted">
                {visibleNonDeletionOperation.kind === 'complete'
                  ? t('live.privacy.operationComplete')
                  : visibleNonDeletionOperation.kind === 'failed'
                  ? t('live.privacy.operationFailed')
                  : t('live.privacy.operationPending')}
              </AppText>
              {visibleNonDeletionOperation.kind !== 'complete' &&
              visibleNonDeletionOperation.kind !== 'failed' ? (
                <>
                  <Button
                    label={t('live.privacy.resumeOperation')}
                    disabled={pending}
                    onPress={resumeOperation}
                    testID="live-privacy-resume-operation"
                  />
                  <Button
                    label={t('live.privacy.refreshOperation')}
                    disabled={pending}
                    onPress={refreshOperation}
                    variant="secondary"
                    testID="live-privacy-refresh-operation"
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
