import type { z } from 'zod';

import type {
  ActivityPage,
  DiagnosticsPreview,
} from '../../domain/activity/model';
import type { AccountProjection } from '../../domain/account/model';
import type {
  ApprovalBatchReview,
  ApprovalProjection,
} from '../../domain/approvals/model';
import type {
  ActivationReview,
  AutomationProjection,
  BirthdayJobProjection,
  TestProjection,
  TestReview,
  TodayOccurrenceReview,
} from '../../domain/automation/model';
import type {
  PolicyEditorProjection,
  PolicyPreview,
} from '../../domain/birthdays/model';
import type {
  ContactDetail,
  EnrollmentReview,
  PeopleMutationProjection,
  PeoplePage,
  SyncProjection,
} from '../../domain/contacts/model';
import type { HomeProjection } from '../../domain/home/model';
import type { PublicResourcesProjection } from '../../domain/legal/model';
import type {
  NativeRouteAvailable,
  NativeRouteProjection,
} from '../../domain/navigation/model';
import type {
  GeminiSuggestionsProjection,
  IosComposerProposalProjection,
  MessagePreview,
  MessageEditorProjection,
} from '../../domain/messages/model';
import type {
  CurrentPrivacyOperationProjection,
  PrivacyActionReview,
  PrivacyInventory,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type {
  DeviceEligibility,
  ReadinessProjection,
} from '../../domain/readiness/model';
import type { SafeSupportCode } from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import type {
  BootstrapProjection,
  SetupProjection,
} from '../../domain/setup/model';
import type { ActivityPort } from '../../application/ports/ActivityPort';
import type {
  AppProjectionPort,
  ProjectionInvalidation,
} from '../../application/ports/AppProjectionPort';
import type { AppRoutePort } from '../../application/ports/AppRoutePort';
import type { AutomationPort } from '../../application/ports/AutomationPort';
import type { IdentityContactsPort } from '../../application/ports/IdentityContactsPort';
import type {
  MessagePort,
  SavedMessageProjection,
} from '../../application/ports/MessagePort';
import type { NativeActionPort } from '../../application/ports/NativeActionPort';
import type { DeviceLifecyclePort } from '../../application/ports/DeviceLifecyclePort';
import type { PeoplePort } from '../../application/ports/PeoplePort';
import type { PrivacyPort } from '../../application/ports/PrivacyPort';
import type { PublicResourcesPort } from '../../application/ports/PublicResourcesPort';
import type { Spec as BirthdayNativeSpec } from '../../../specs/native/NativeBirthday';
import {
  accountProjectionSchema,
  approvalBatchReviewSchema,
  approvalProjectionSchema,
  deviceEligibilitySchema,
  projectionInvalidationSchema,
  readinessIssueSchema,
  readinessProjectionSchema,
} from './coreSchemas';
import { decodeNativeResponse } from './decodeNativeResponse';
import {
  activationReviewSchema,
  activityPageSchema,
  automationProjectionSchema,
  birthdayJobProjectionSchema,
  bootstrapProjectionSchema,
  contactDetailSchema,
  currentPrivacyOperationProjectionSchema,
  diagnosticsPreviewSchema,
  enrollmentReviewSchema,
  geminiSuggestionsProjectionSchema,
  homeProjectionSchema,
  iosComposerProposalProjectionSchema,
  messageEditorProjectionSchema,
  messagePreviewSchema,
  nativeActionResultSchema,
  nativeRouteAvailableSchema,
  nativeRouteProjectionSchema,
  notificationPermissionProjectionSchema,
  notificationPermissionRequestResultSchema,
  notificationSettingsResultSchema,
  peopleMutationProjectionSchema,
  peoplePageSchema,
  policyEditorProjectionSchema,
  policyPreviewSchema,
  privacyActionReviewSchema,
  privacyInventorySchema,
  privacyOperationProjectionSchema,
  publicResourcesProjectionSchema,
  savedMessageProjectionSchema,
  senderTransferOperationProjectionSchema,
  senderTransferReviewSchema,
  setupProjectionSchema,
  sharedActionResultSchema,
  syncProjectionSchema,
  testProjectionSchema,
  testReviewSchema,
  todayOccurrenceReviewSchema,
} from './featureSchemas';
import {
  inactiveInvalidationSource,
  type NativeInvalidationSource,
} from './NativeInvalidationSource';
import {
  inactiveNativeRouteSource,
  type NativeRouteSource,
} from './NativeRouteSource';

type NativeModule = BirthdayNativeSpec;

const NATIVE_UNAVAILABLE_SUPPORT_CODE =
  'NATIVE_BRIDGE_UNAVAILABLE' as SafeSupportCode;

const unavailableResult = <Value>(): NativeResult<Value> => ({
  kind: 'error',
  problem: {
    kind: 'internal',
    supportCode: NATIVE_UNAVAILABLE_SUPPORT_CODE,
  },
});

const USER_INTENTS = {
  activate: 'activate',
  authorizeContacts: 'authorize-contacts',
  chooseBirthday: 'choose-birthday',
  choosePhone: 'choose-phone',
  confirmApprovals: 'confirm-approvals',
  confirmEnrollment: 'confirm-enrollment',
  confirmPrivacyAction: 'confirm-privacy-action',
  confirmTodayOccurrence: 'confirm-today-occurrence',
  beginSenderTransfer: 'begin-sender-transfer',
  blockRecipientDestination: 'block-recipient-destination',
  continueWithGoogle: 'continue-with-google',
  excludeRecipient: 'exclude-recipient',
  generateSuggestions: 'generate-suggestions',
  pauseAll: 'pause-all',
  pauseRecipient: 'pause-recipient',
  completeSenderTransfer: 'complete-sender-transfer',
  performNativeAction: 'perform-native-action',
  prepareActivation: 'prepare-activation',
  prepareApprovals: 'prepare-approvals',
  prepareEnrollmentReview: 'prepare-enrollment-review',
  preparePrivacyAction: 'prepare-privacy-action',
  prepareResume: 'prepare-resume',
  prepareTest: 'prepare-test',
  prepareTodayOccurrence: 'prepare-today-occurrence',
  prepareSenderTransfer: 'prepare-sender-transfer',
  previewDiagnostics: 'preview-diagnostics',
  previewMessage: 'preview-message',
  previewPolicy: 'preview-policy',
  refreshCompatibility: 'refresh-compatibility',
  restoreRecipient: 'restore-recipient',
  requestNotificationPermission: 'request-notification-permission',
  openNotificationSettings: 'open-notification-settings',
  repairLifecycleState: 'repair-lifecycle-state',
  resumeLifecycleOperation: 'resume-lifecycle-operation',
  resume: 'resume',
  saveMessage: 'save-message',
  savePolicy: 'save-policy',
  shareDiagnostics: 'share-diagnostics',
  startTest: 'start-test',
  syncContacts: 'sync-contacts',
  unblockRecipientDestination: 'unblock-recipient-destination',
} as const;

type ExpectedRevision =
  | import('../../domain/shared/brand').NativeRevision
  | null;

export class BirthdayNativeAdapter
  implements
    AppProjectionPort,
    AppRoutePort,
    IdentityContactsPort,
    PeoplePort,
    MessagePort,
    AutomationPort,
    ActivityPort,
    PrivacyPort,
    PublicResourcesPort,
    DeviceLifecyclePort,
    NativeActionPort
{
  public constructor(
    private readonly nativeModule: NativeModule | null,
    private readonly invalidationSource: NativeInvalidationSource = inactiveInvalidationSource,
    private readonly routeSource: NativeRouteSource = inactiveNativeRouteSource,
  ) {}

  public getBootstrap(): Promise<NativeResult<BootstrapProjection>> {
    return this.read('bootstrap', {}, bootstrapProjectionSchema);
  }

  public getSetup(): Promise<NativeResult<SetupProjection>> {
    return this.read('setup', {}, setupProjectionSchema);
  }

  public getHome(): Promise<NativeResult<HomeProjection>> {
    return this.read('home', {}, homeProjectionSchema);
  }

  public getEligibility(): Promise<NativeResult<DeviceEligibility>> {
    return this.read('eligibility', {}, deviceEligibilitySchema);
  }

  public getReadiness(): Promise<NativeResult<ReadinessProjection>> {
    return this.read('readiness', {}, readinessProjectionSchema);
  }

  public getAccount(): Promise<NativeResult<AccountProjection>> {
    return this.read('account', {}, accountProjectionSchema);
  }

  public getApproval(
    contactId: import('../../domain/shared/brand').ContactId,
  ): Promise<NativeResult<ApprovalProjection>> {
    return this.read(
      'automation',
      { kind: 'approval', contactId },
      approvalProjectionSchema,
    );
  }

  public getBirthdayJob(
    occurrenceId: import('../../domain/shared/brand').OccurrenceId,
  ): Promise<NativeResult<BirthdayJobProjection>> {
    return this.read(
      'automation',
      { kind: 'birthday-job', occurrenceId },
      birthdayJobProjectionSchema,
    );
  }

  public getLatestTest(): Promise<NativeResult<TestProjection>> {
    return this.read(
      'automation',
      { kind: 'latest-test' },
      testProjectionSchema,
    );
  }

  public subscribeInvalidations(
    listener: (event: ProjectionInvalidation) => void,
  ): () => void {
    return this.invalidationSource.subscribe(rawEvent => {
      const decoded = projectionInvalidationSchema.safeParse(rawEvent);
      if (decoded.success) {
        listener(decoded.data);
      }
    });
  }

  public getPendingRoute(): Promise<NativeResult<NativeRouteProjection>> {
    return this.read('route', {}, nativeRouteProjectionSchema);
  }

  public subscribeRouteAvailable(
    listener: (event: NativeRouteAvailable) => void,
  ): () => void {
    return this.routeSource.subscribe(rawEvent => {
      const decoded = nativeRouteAvailableSchema.safeParse(rawEvent);
      if (decoded.success) {
        listener(decoded.data);
      }
    });
  }

  public refreshCompatibility(): Promise<NativeResult<DeviceEligibility>> {
    return this.intent(
      USER_INTENTS.refreshCompatibility,
      null,
      {},
      deviceEligibilitySchema,
    );
  }

  public continueWithGoogle(): Promise<NativeResult<AccountProjection>> {
    return this.intent(
      USER_INTENTS.continueWithGoogle,
      null,
      {},
      accountProjectionSchema,
    );
  }

  public authorizeContacts(): Promise<NativeResult<SyncProjection>> {
    return this.intent(
      USER_INTENTS.authorizeContacts,
      null,
      {},
      syncProjectionSchema,
    );
  }

  public syncContacts(
    reason: 'setup' | 'user',
  ): Promise<NativeResult<SyncProjection>> {
    return this.intent(
      USER_INTENTS.syncContacts,
      null,
      { reason },
      syncProjectionSchema,
    );
  }

  public listPeople(
    query: import('../../domain/contacts/model').PeopleQuery,
  ): Promise<NativeResult<PeoplePage>> {
    return this.read('contacts', { kind: 'list', query }, peoplePageSchema);
  }

  public getPerson(
    contactId: import('../../domain/shared/brand').ContactId,
  ): Promise<NativeResult<ContactDetail>> {
    return this.read(
      'contacts',
      { kind: 'detail', contactId },
      contactDetailSchema,
    );
  }

  public getMessageEditor(): Promise<NativeResult<MessageEditorProjection>> {
    return this.read(
      'messages',
      { kind: 'editor' },
      messageEditorProjectionSchema,
    );
  }

  public getNextComposerProposal(): Promise<
    NativeResult<IosComposerProposalProjection>
  > {
    return this.read(
      'messages',
      { kind: 'next-composer-proposal' },
      iosComposerProposalProjectionSchema,
    );
  }

  public choosePhone(
    input: Parameters<PeoplePort['choosePhone']>[0],
  ): Promise<NativeResult<ContactDetail>> {
    return this.intent(
      USER_INTENTS.choosePhone,
      input.expectedRevision,
      input,
      contactDetailSchema,
    );
  }

  public chooseBirthday(
    input: Parameters<PeoplePort['chooseBirthday']>[0],
  ): Promise<NativeResult<ContactDetail>> {
    return this.intent(
      USER_INTENTS.chooseBirthday,
      input.expectedRevision,
      input,
      contactDetailSchema,
    );
  }

  public prepareEnrollmentReview(
    input: Parameters<PeoplePort['prepareEnrollmentReview']>[0],
  ): Promise<NativeResult<EnrollmentReview>> {
    return this.intent(
      USER_INTENTS.prepareEnrollmentReview,
      input.expectedRevision,
      input,
      enrollmentReviewSchema,
    );
  }

  public confirmEnrollment(
    input: Parameters<PeoplePort['confirmEnrollment']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.intent(
      USER_INTENTS.confirmEnrollment,
      input.expectedRevision,
      input,
      peopleMutationProjectionSchema,
    );
  }

  public pauseRecipient(
    input: Parameters<PeoplePort['pauseRecipient']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.recipientIntent(USER_INTENTS.pauseRecipient, input);
  }

  public excludeRecipient(
    input: Parameters<PeoplePort['excludeRecipient']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.recipientIntent(USER_INTENTS.excludeRecipient, input);
  }

  public blockRecipientDestination(
    input: Parameters<PeoplePort['blockRecipientDestination']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.recipientIntent(USER_INTENTS.blockRecipientDestination, input);
  }

  public unblockRecipientDestination(
    input: Parameters<PeoplePort['unblockRecipientDestination']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.recipientIntent(
      USER_INTENTS.unblockRecipientDestination,
      input,
    );
  }

  public restoreRecipient(
    input: Parameters<PeoplePort['restoreRecipient']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.recipientIntent(USER_INTENTS.restoreRecipient, input);
  }

  public previewMessage(
    input: Parameters<MessagePort['previewMessage']>[0],
  ): Promise<NativeResult<MessagePreview>> {
    return this.intent(
      USER_INTENTS.previewMessage,
      input.expectedRevision,
      input,
      messagePreviewSchema,
    );
  }

  public saveMessage(
    input: Parameters<MessagePort['saveMessage']>[0],
  ): Promise<NativeResult<SavedMessageProjection>> {
    return this.intent(
      USER_INTENTS.saveMessage,
      input.expectedRevision,
      input,
      savedMessageProjectionSchema,
    );
  }

  public generateSuggestions(
    request: Parameters<MessagePort['generateSuggestions']>[0],
  ): Promise<NativeResult<GeminiSuggestionsProjection>> {
    return this.intent(
      USER_INTENTS.generateSuggestions,
      null,
      request,
      geminiSuggestionsProjectionSchema,
    );
  }

  public getPolicyEditor(): Promise<NativeResult<PolicyEditorProjection>> {
    return this.read(
      'automation',
      { kind: 'policy-editor' },
      policyEditorProjectionSchema,
    );
  }

  public previewPolicy(
    input: Parameters<AutomationPort['previewPolicy']>[0],
  ): Promise<NativeResult<PolicyPreview>> {
    return this.intent(
      USER_INTENTS.previewPolicy,
      input.expectedRevision,
      input,
      policyPreviewSchema,
    );
  }

  public savePolicy(
    input: Parameters<AutomationPort['savePolicy']>[0],
  ): Promise<NativeResult<AutomationProjection>> {
    return this.automationIntent(USER_INTENTS.savePolicy, input);
  }

  public prepareApprovals(
    input: Parameters<AutomationPort['prepareApprovals']>[0],
  ): Promise<NativeResult<ApprovalBatchReview>> {
    return this.intent(
      USER_INTENTS.prepareApprovals,
      input.expectedRevision,
      input,
      approvalBatchReviewSchema,
    );
  }

  public confirmApprovals(
    input: Parameters<AutomationPort['confirmApprovals']>[0],
  ): Promise<NativeResult<AutomationProjection>> {
    return this.automationIntent(USER_INTENTS.confirmApprovals, input);
  }

  public prepareTest(
    input: Parameters<AutomationPort['prepareTest']>[0],
  ): Promise<NativeResult<TestReview>> {
    return this.intent(
      USER_INTENTS.prepareTest,
      input.expectedRevision,
      input,
      testReviewSchema,
    );
  }

  public startTest(
    input: Parameters<AutomationPort['startTest']>[0],
  ): Promise<NativeResult<TestProjection>> {
    return this.intent(
      USER_INTENTS.startTest,
      input.expectedRevision,
      input,
      testProjectionSchema,
    );
  }

  public prepareActivation(): Promise<NativeResult<ActivationReview>> {
    return this.intent(
      USER_INTENTS.prepareActivation,
      null,
      {},
      activationReviewSchema,
    );
  }

  public activate(
    input: Parameters<AutomationPort['activate']>[0],
  ): Promise<NativeResult<AutomationProjection>> {
    return this.automationIntent(USER_INTENTS.activate, input);
  }

  public pauseAll(
    input: Parameters<AutomationPort['pauseAll']>[0],
  ): Promise<NativeResult<AutomationProjection>> {
    return this.automationIntent(USER_INTENTS.pauseAll, input);
  }

  public prepareResume(): Promise<NativeResult<ActivationReview>> {
    return this.intent(
      USER_INTENTS.prepareResume,
      null,
      {},
      activationReviewSchema,
    );
  }

  public resume(
    input: Parameters<AutomationPort['resume']>[0],
  ): Promise<NativeResult<AutomationProjection>> {
    return this.automationIntent(USER_INTENTS.resume, input);
  }

  public prepareTodayOccurrence(
    input: Parameters<AutomationPort['prepareTodayOccurrence']>[0],
  ): Promise<NativeResult<TodayOccurrenceReview>> {
    return this.intent(
      USER_INTENTS.prepareTodayOccurrence,
      input.expectedRevision,
      input,
      todayOccurrenceReviewSchema,
    );
  }

  public confirmTodayOccurrence(
    input: Parameters<AutomationPort['confirmTodayOccurrence']>[0],
  ): Promise<NativeResult<AutomationProjection>> {
    return this.automationIntent(USER_INTENTS.confirmTodayOccurrence, input);
  }

  public listActivity(
    query: Parameters<ActivityPort['listActivity']>[0],
  ): Promise<NativeResult<ActivityPage>> {
    return this.read('activity', { kind: 'list', query }, activityPageSchema);
  }

  public listIssues(): Promise<
    NativeResult<
      readonly import('../../domain/readiness/model').ReadinessIssue[]
    >
  > {
    return this.read(
      'activity',
      { kind: 'issues' },
      readinessIssueSchema.array(),
    );
  }

  public previewDiagnostics(): Promise<NativeResult<DiagnosticsPreview>> {
    return this.intent(
      USER_INTENTS.previewDiagnostics,
      null,
      {},
      diagnosticsPreviewSchema,
    );
  }

  public shareDiagnostics(
    input: Parameters<ActivityPort['shareDiagnostics']>[0],
  ): ReturnType<ActivityPort['shareDiagnostics']> {
    return this.intent(
      USER_INTENTS.shareDiagnostics,
      input.expectedRevision,
      input,
      sharedActionResultSchema,
    );
  }

  public getInventory(): Promise<NativeResult<PrivacyInventory>> {
    return this.read('privacy', { kind: 'inventory' }, privacyInventorySchema);
  }

  public getPublicResources(): Promise<
    NativeResult<PublicResourcesProjection>
  > {
    return this.read(
      'privacy',
      { kind: 'public-resources' },
      publicResourcesProjectionSchema,
    );
  }

  public getCurrentOperation(): Promise<
    NativeResult<CurrentPrivacyOperationProjection>
  > {
    return this.read(
      'privacy',
      { kind: 'current-operation' },
      currentPrivacyOperationProjectionSchema,
    );
  }

  public prepareAction(
    input: Parameters<PrivacyPort['prepareAction']>[0],
  ): Promise<NativeResult<PrivacyActionReview>> {
    return this.intent(
      USER_INTENTS.preparePrivacyAction,
      input.expectedRevision,
      input,
      privacyActionReviewSchema,
    );
  }

  public confirmAction(
    input: Parameters<PrivacyPort['confirmAction']>[0],
  ): Promise<NativeResult<PrivacyOperationProjection>> {
    return this.intent(
      USER_INTENTS.confirmPrivacyAction,
      input.expectedRevision,
      input,
      privacyOperationProjectionSchema,
    );
  }

  public getOperation(
    operationId: Parameters<PrivacyPort['getOperation']>[0],
  ): Promise<NativeResult<PrivacyOperationProjection>> {
    return this.read(
      'privacy',
      { kind: 'operation', operationId },
      privacyOperationProjectionSchema,
    );
  }

  public resumeOperation(
    operationId: Parameters<PrivacyPort['resumeOperation']>[0],
  ): Promise<NativeResult<PrivacyOperationProjection>> {
    return this.intent(
      USER_INTENTS.resumeLifecycleOperation,
      null,
      { operationId },
      privacyOperationProjectionSchema,
    );
  }

  public getNotificationPermission(): ReturnType<
    DeviceLifecyclePort['getNotificationPermission']
  > {
    return this.read(
      'notifications',
      {},
      notificationPermissionProjectionSchema,
    );
  }

  public requestNotificationPermission(): ReturnType<
    DeviceLifecyclePort['requestNotificationPermission']
  > {
    return this.intent(
      USER_INTENTS.requestNotificationPermission,
      null,
      {},
      notificationPermissionRequestResultSchema,
    );
  }

  public openNotificationSettings(): ReturnType<
    DeviceLifecyclePort['openNotificationSettings']
  > {
    return this.intent(
      USER_INTENTS.openNotificationSettings,
      null,
      {},
      notificationSettingsResultSchema,
    );
  }

  public getSenderTransferOperation(): ReturnType<
    DeviceLifecyclePort['getSenderTransferOperation']
  > {
    return this.read(
      'automation',
      { kind: 'sender-transfer-operation' },
      senderTransferOperationProjectionSchema,
    );
  }

  public prepareSenderTransfer(
    input: Parameters<DeviceLifecyclePort['prepareSenderTransfer']>[0],
  ): ReturnType<DeviceLifecyclePort['prepareSenderTransfer']> {
    return this.intent(
      USER_INTENTS.prepareSenderTransfer,
      input.expectedRevision,
      input,
      senderTransferReviewSchema,
    );
  }

  public beginSenderTransfer(
    input: Parameters<DeviceLifecyclePort['beginSenderTransfer']>[0],
  ): ReturnType<DeviceLifecyclePort['beginSenderTransfer']> {
    return this.intent(
      USER_INTENTS.beginSenderTransfer,
      input.expectedRevision,
      input,
      senderTransferOperationProjectionSchema,
    );
  }

  public completeSenderTransfer(
    input: Parameters<DeviceLifecyclePort['completeSenderTransfer']>[0],
  ): ReturnType<DeviceLifecyclePort['completeSenderTransfer']> {
    return this.intent(
      USER_INTENTS.completeSenderTransfer,
      null,
      input,
      senderTransferOperationProjectionSchema,
    );
  }

  public resumeSenderTransfer(
    input: Parameters<DeviceLifecyclePort['resumeSenderTransfer']>[0],
  ): ReturnType<DeviceLifecyclePort['resumeSenderTransfer']> {
    return this.intent(
      USER_INTENTS.resumeLifecycleOperation,
      null,
      input,
      senderTransferOperationProjectionSchema,
    );
  }

  public repairLifecycleState(
    input: Parameters<DeviceLifecyclePort['repairLifecycleState']>[0],
  ): ReturnType<DeviceLifecyclePort['repairLifecycleState']> {
    return this.intent(
      USER_INTENTS.repairLifecycleState,
      null,
      input,
      privacyOperationProjectionSchema,
    );
  }

  public performAction(
    input: Parameters<NativeActionPort['performAction']>[0],
  ): ReturnType<NativeActionPort['performAction']> {
    return this.intent(
      USER_INTENTS.performNativeAction,
      input.expectedRevision,
      input,
      nativeActionResultSchema,
    );
  }

  private recipientIntent(
    intent:
      | typeof USER_INTENTS.pauseRecipient
      | typeof USER_INTENTS.excludeRecipient
      | typeof USER_INTENTS.blockRecipientDestination
      | typeof USER_INTENTS.unblockRecipientDestination
      | typeof USER_INTENTS.restoreRecipient,
    input: Parameters<PeoplePort['pauseRecipient']>[0],
  ): Promise<NativeResult<PeopleMutationProjection>> {
    return this.intent(
      intent,
      input.expectedRevision,
      input,
      peopleMutationProjectionSchema,
    );
  }

  private automationIntent(
    intent:
      | typeof USER_INTENTS.activate
      | typeof USER_INTENTS.confirmApprovals
      | typeof USER_INTENTS.confirmTodayOccurrence
      | typeof USER_INTENTS.pauseAll
      | typeof USER_INTENTS.resume
      | typeof USER_INTENTS.savePolicy,
    input: { expectedRevision: ExpectedRevision },
  ): Promise<NativeResult<AutomationProjection>> {
    return this.intent(
      intent,
      input.expectedRevision,
      input,
      automationProjectionSchema,
    );
  }

  private async read<Value>(
    area: string,
    request: unknown,
    schema: z.ZodType<Value>,
  ): Promise<NativeResult<Value>> {
    if (this.nativeModule === null) {
      return unavailableResult();
    }

    try {
      const raw = await this.nativeModule.getProjection(
        area,
        JSON.stringify(request),
      );
      return decodeNativeResponse(raw, schema);
    } catch {
      return unavailableResult();
    }
  }

  private async intent<Value>(
    intent: (typeof USER_INTENTS)[keyof typeof USER_INTENTS],
    expectedRevision: ExpectedRevision,
    payload: unknown,
    schema: z.ZodType<Value>,
  ): Promise<NativeResult<Value>> {
    if (this.nativeModule === null) {
      return unavailableResult();
    }

    try {
      const raw = await this.nativeModule.executeUserIntent(
        intent,
        expectedRevision,
        JSON.stringify(payload),
      );
      return decodeNativeResponse(raw, schema);
    } catch {
      return unavailableResult();
    }
  }
}
