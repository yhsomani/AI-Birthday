import React from 'react';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  FixtureNotice,
  KeyValue,
  Screen,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { fixtureMessageFor, fixturePeople } from '../fixtures/data';

type Props = NativeStackScreenProps<RootStackParamList, 'ApprovedMessage'>;

export function ApprovedMessageScreen({ navigation }: Props) {
  const {
    platform,
    selectedPersonIds,
    composerFixtureRecorded,
    recordComposer,
  } = useFixture();
  const { t } = useAppLocalization();
  const person =
    fixturePeople.find(item => selectedPersonIds.includes(item.id)) ??
    fixturePeople[0]!;

  return (
    <Screen testID="approved-message-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('message.title')}
      </AppText>
      <Card>
        <KeyValue label={person.name} value={person.maskedPhone ?? '—'} />
        <KeyValue
          label={t('setup.messageLabel')}
          value={fixtureMessageFor(person.givenName)}
        />
        {platform === 'android' ? (
          <KeyValue
            label={t('message.segmentTitle')}
            value={t('message.segmentValue')}
          />
        ) : null}
      </Card>
      <StatusRow
        title={
          platform === 'android' ? t('home.androidMode') : t('home.iosMode')
        }
        detail={
          platform === 'android'
            ? t('message.androidDisclosure')
            : t('message.iosDisclosure')
        }
        tone="warning"
      />
      {platform === 'ios' ? (
        <>
          {composerFixtureRecorded ? (
            <StatusRow
              title={t('activity.iosOpened')}
              detail={t('message.composerResult')}
              tone="info"
              testID="composer-fixture-result"
            />
          ) : (
            <Button
              label={t('message.composerFixture')}
              onPress={recordComposer}
              testID="record-composer-fixture"
            />
          )}
        </>
      ) : null}
      <Button
        label={t('common.close')}
        onPress={navigation.goBack}
        variant={platform === 'ios' ? 'secondary' : 'primary'}
      />
    </Screen>
  );
}
