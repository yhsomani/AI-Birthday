import React from 'react';

import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import { Button, Screen } from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort } from './LiveAppPort';
import { LivePolicyEditor } from './LivePolicyEditor';

export function LiveScheduleScreen({
  onBack,
  platform,
  port,
}: {
  onBack: () => void;
  platform: PlatformCapability['platform'];
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();

  return (
    <Screen includeTopInset testID="live-schedule-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-schedule-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.settings.schedule')}
      </AppText>
      <LivePolicyEditor platform={platform} port={port} />
    </Screen>
  );
}
