import React from 'react';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  FixtureNotice,
  Screen,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';

type Props = NativeStackScreenProps<RootStackParamList, 'Attention'>;

export function AttentionScreen({ navigation }: Props) {
  const { platform, attentionReviewed, reviewAttention } = useFixture();
  const { t } = useAppLocalization();

  return (
    <Screen testID="attention-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('attention.title')}
      </AppText>
      <StatusRow
        title={
          platform === 'android'
            ? t('attention.androidIssue')
            : t('attention.iosIssue')
        }
        detail={
          platform === 'android'
            ? t('attention.androidIssueBody')
            : t('attention.iosIssueBody')
        }
        tone="warning"
      />
      {attentionReviewed ? (
        <>
          <StatusRow
            title={t('attention.recheckResult')}
            tone="info"
            testID="attention-recheck-result"
          />
          <Button
            label={t('attention.returnHome')}
            onPress={() => navigation.navigate('Main')}
            testID="attention-return-home"
          />
        </>
      ) : (
        <Button
          label={t('attention.recheck')}
          onPress={reviewAttention}
          testID="attention-recheck"
        />
      )}
    </Screen>
  );
}
