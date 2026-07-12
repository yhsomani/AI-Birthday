import React from 'react';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  FixtureNotice,
  Screen,
  SettingRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import {
  FixtureActivity,
  androidFixtureActivity,
  iosFixtureActivity,
} from '../fixtures/data';

type Props = NativeStackScreenProps<RootStackParamList, 'Activity'>;

export const activityLabel = (
  activity: FixtureActivity,
  t: ReturnType<typeof useAppLocalization>['t'],
) => {
  switch (activity.kind) {
    case 'android-submitted':
      return t('activity.androidSubmitted');
    case 'android-sent':
      return t('activity.androidSent');
    case 'delivery-unknown':
      return t('activity.deliveryUnknown');
    case 'ios-opened':
      return t('activity.iosOpened');
    case 'ios-reported':
      return t('activity.iosReported');
  }
};

export function ActivityScreen({ navigation }: Props) {
  const { platform } = useFixture();
  const { t } = useAppLocalization();
  const activity =
    platform === 'android' ? androidFixtureActivity : iosFixtureActivity;

  return (
    <Screen testID="activity-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('activity.title')}
      </AppText>
      {activity.map(item => (
        <SettingRow
          key={item.id}
          title={activityLabel(item, t)}
          detail={item.timestamp}
          onPress={() =>
            navigation.navigate('ActivityDetail', { activityId: item.id })
          }
          testID={`activity-row-${item.id}`}
        />
      ))}
    </Screen>
  );
}
