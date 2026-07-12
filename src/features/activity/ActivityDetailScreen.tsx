import React from 'react';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Card,
  FixtureNotice,
  KeyValue,
  Screen,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { androidFixtureActivity, iosFixtureActivity } from '../fixtures/data';
import { activityLabel } from './ActivityScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'ActivityDetail'>;

export function ActivityDetailScreen({ route }: Props) {
  const { platform } = useFixture();
  const { t } = useAppLocalization();
  const activity = [...androidFixtureActivity, ...iosFixtureActivity].find(
    item => item.id === route.params.activityId,
  );

  return (
    <Screen testID="activity-detail-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('activity.detailTitle')}
      </AppText>
      {activity ? (
        <>
          <Card>
            <KeyValue
              label={activity.timestamp}
              value={activityLabel(activity, t)}
            />
          </Card>
          <StatusRow
            title={t('common.fixtureOnly')}
            detail={t('activity.syntheticDetail')}
            tone="info"
          />
          {platform === 'android' && activity.kind === 'android-submitted' ? (
            <StatusRow
              title={t('activity.androidSubmitted')}
              detail={t('message.androidDisclosure')}
              tone="warning"
            />
          ) : null}
        </>
      ) : (
        <StatusRow title={t('activity.empty')} tone="critical" />
      )}
    </Screen>
  );
}
