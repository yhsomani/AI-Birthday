import React from 'react';

import type { PrivacyInventory } from '../../domain/privacy/model';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Card,
  KeyValue,
  SectionHeading,
} from '../../design-system/components/Primitives';
import { formatLiveInstant } from '../../localization/formatLive';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { LiveCloudPrivacyBoundary } from './LiveCloudPrivacyBoundary';

export function LivePrivacyInventory({
  inventory,
  platform,
}: {
  inventory: PrivacyInventory;
  platform: PlatformCapability['platform'];
}) {
  const { language, t } = useAppLocalization();
  const consentVersions =
    inventory.consentVersions.length === 0
      ? t('live.settings.noConsentVersions')
      : inventory.consentVersions.join(', ');

  return (
    <>
      <Card>
        <KeyValue
          label={t('live.settings.localContacts')}
          value={String(inventory.localContactCount)}
        />
        <KeyValue
          label={t('live.settings.enabledRecipients')}
          value={String(inventory.enabledRecipientCount)}
        />
        <KeyValue
          label={t('live.settings.approvals')}
          value={String(inventory.approvalCount)}
        />
        <KeyValue
          label={t('live.settings.activityRecords')}
          value={String(inventory.activityCount)}
        />
        <KeyValue
          label={t('live.settings.templates')}
          value={String(inventory.templateCount)}
        />
        <KeyValue
          label={t('live.settings.localStorage')}
          value={t('live.settings.bytes', {
            count: inventory.localStorageBytes,
          })}
        />
        <KeyValue
          label={t('live.settings.lastContactsSync')}
          value={
            inventory.lastContactsSyncAt
              ? formatLiveInstant(inventory.lastContactsSyncAt, language)
              : t('live.settings.neverSynced')
          }
        />
        <KeyValue
          label={t('live.settings.consentVersions')}
          value={consentVersions}
        />
        <SectionHeading title={t('live.settings.retention')} />
        <AppText color="muted">{t('live.settings.activityRetention')}</AppText>
        <AppText color="muted">
          {t(
            platform === 'android'
              ? 'live.settings.androidSafetyRetention'
              : 'live.settings.iosSafetyRetention',
          )}
        </AppText>
        <SectionHeading title={t('live.settings.externalBoundary')} />
        <AppText color="muted">{t('live.settings.externalCopies')}</AppText>
      </Card>
      <LiveCloudPrivacyBoundary platform={platform} />
    </>
  );
}
