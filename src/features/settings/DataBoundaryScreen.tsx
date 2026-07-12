import React from 'react';

import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  FixtureNotice,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';

export function DataBoundaryScreen() {
  const { resetFixture } = useFixture();
  const { t } = useAppLocalization();
  return (
    <Screen testID="data-boundary-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('privacy.title')}
      </AppText>
      <Card>
        <SectionHeading
          title={t('privacy.localTitle')}
          supporting={t('privacy.localBody')}
        />
        <SectionHeading
          title={t('privacy.cloudTitle')}
          supporting={t('privacy.cloudBody')}
        />
      </Card>
      <StatusRow
        title={t('privacy.externalTitle')}
        detail={t('privacy.externalBody')}
        tone="warning"
      />
      <Button
        label={t('privacy.clearFixture')}
        onPress={resetFixture}
        variant="danger"
        testID="privacy-clear-fixture"
      />
    </Screen>
  );
}
