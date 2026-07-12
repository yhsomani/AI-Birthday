import type {
  ContactDetail,
  EnrollmentReview,
  PeopleMutationProjection,
  PeoplePage,
  PeopleQuery,
} from '../../domain/contacts/model';
import type { LeapDayPolicy } from '../../domain/birthdays/model';
import type {
  BirthdayChoiceId,
  ContactId,
  EnrollmentReviewHandle,
  NativeRevision,
  PhoneChoiceId,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export interface PeoplePort {
  listPeople(query: PeopleQuery): Promise<NativeResult<PeoplePage>>;
  getPerson(contactId: ContactId): Promise<NativeResult<ContactDetail>>;
  choosePhone(input: {
    contactId: ContactId;
    phoneId: PhoneChoiceId;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<ContactDetail>>;
  chooseBirthday(input: {
    contactId: ContactId;
    birthdayId: BirthdayChoiceId;
    leapPolicy?: LeapDayPolicy | undefined;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<ContactDetail>>;
  prepareEnrollmentReview(input: {
    contactIds: readonly ContactId[];
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<EnrollmentReview>>;
  confirmEnrollment(input: {
    handle: EnrollmentReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<PeopleMutationProjection>>;
  pauseRecipient(
    input: RevisionedContactCommand,
  ): Promise<NativeResult<PeopleMutationProjection>>;
  excludeRecipient(
    input: RevisionedContactCommand,
  ): Promise<NativeResult<PeopleMutationProjection>>;
  restoreRecipient(
    input: RevisionedContactCommand,
  ): Promise<NativeResult<PeopleMutationProjection>>;
}

export type RevisionedContactCommand = Readonly<{
  contactId: ContactId;
  expectedRevision: NativeRevision;
}>;
