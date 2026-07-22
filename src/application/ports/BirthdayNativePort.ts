import type { ActivityPort } from './ActivityPort';
import type { AppProjectionPort } from './AppProjectionPort';
import type { AppRoutePort } from './AppRoutePort';
import type { AutomationPort } from './AutomationPort';
import type { DeviceLifecyclePort } from './DeviceLifecyclePort';
import type { IdentityContactsPort } from './IdentityContactsPort';
import type { MessagePort } from './MessagePort';
import type { NativeActionPort } from './NativeActionPort';
import type { PeoplePort } from './PeoplePort';
import type { PrivacyPort } from './PrivacyPort';
import type { PublicResourcesPort } from './PublicResourcesPort';

export interface BirthdayNativePort
  extends
    AppProjectionPort,
    AppRoutePort,
    IdentityContactsPort,
    PeoplePort,
    MessagePort,
    AutomationPort,
    DeviceLifecyclePort,
    ActivityPort,
    PrivacyPort,
    PublicResourcesPort,
    NativeActionPort {}
