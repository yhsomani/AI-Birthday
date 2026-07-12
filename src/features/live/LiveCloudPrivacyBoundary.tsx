import React from 'react';

import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Card,
  SectionHeading,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';

export function LiveCloudPrivacyBoundary({
  platform,
}: {
  platform: PlatformCapability['platform'];
}) {
  const { t } = useAppLocalization();

  return (
    <Card testID="live-cloud-privacy-boundary">
      <SectionHeading title={t('live.privacy.cloudMetadataTitle')} />
      <AppText color="muted">{t('live.privacy.cloudMetadataBody')}</AppText>
      <AppText color="muted">
        {t(
          platform === 'android'
            ? 'live.privacy.androidCoordinationBoundary'
            : 'live.privacy.iosCoordinationBoundary',
        )}
      </AppText>
      <AppText color="muted">{t('live.privacy.geminiBoundary')}</AppText>
      <SectionHeading title={t('live.privacy.providerRetentionTitle')} />
      <AppText color="muted">{t('live.privacy.providerRetentionBody')}</AppText>
    </Card>
  );
}
