import React from 'react';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  FixtureNotice,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';

type Props = NativeStackScreenProps<RootStackParamList, 'HelpLegal'>;

/**
 * A no-I/O help/legal device fixture. It validates layout and localized copy
 * without opening a browser or pretending that launch URLs are provisioned.
 */
export function FixtureHelpLegalScreen({ navigation }: Props) {
  const { platform } = useFixture();
  const { t } = useAppLocalization();

  return (
    <Screen includeTopInset testID="fixture-help-legal-screen">
      <Button
        label={t('common.back')}
        onPress={() => navigation.goBack()}
        testID="fixture-help-legal-back"
        variant="ghost"
      />
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('live.help.title')}
      </AppText>
      <AppText color="muted">{t('live.help.body')}</AppText>
      <Card>
        <SectionHeading title={t('live.help.about')} />
        <StatusRow
          title={
            platform === 'android'
              ? t('live.common.androidEdition')
              : t('live.common.iosEdition')
          }
          detail={
            platform === 'android'
              ? t('live.help.androidLimitation')
              : t('live.help.iosLimitation')
          }
          tone="info"
        />
      </Card>
      <ReadinessBanner
        title={t('live.help.externalCopies')}
        detail={t('live.help.externalCopiesBody')}
        tone="warning"
      />
      <ReadinessBanner
        title={t('live.help.linksUnavailable')}
        detail={t('live.help.linksUnavailableBody')}
        tone="warning"
      />
    </Screen>
  );
}
