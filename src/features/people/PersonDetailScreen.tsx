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
import { formatFixtureDate } from '../../localization/i18n';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { fixtureMessageFor, fixturePeople } from '../fixtures/data';

type Props = NativeStackScreenProps<RootStackParamList, 'PersonDetail'>;

export function PersonDetailScreen({ route, navigation }: Props) {
  const { selectedPersonIds, repairedPersonIds, togglePerson, repairPerson } =
    useFixture();
  const { language, t } = useAppLocalization();
  const person = fixturePeople.find(item => item.id === route.params.personId);

  if (!person) {
    return (
      <Screen>
        <FixtureNotice />
        <StatusRow title={t('people.empty')} tone="critical" />
      </Screen>
    );
  }

  const isRepaired = repairedPersonIds.includes(person.id);
  const needsAttention = person.status === 'attention' && !isRepaired;
  const isExcluded = person.status === 'excluded';
  const isEnabled = selectedPersonIds.includes(person.id);

  return (
    <Screen testID="person-detail-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {person.name}
      </AppText>
      <Card>
        <KeyValue label={t('person.source')} value={t('person.sourceValue')} />
        <KeyValue
          label={t('person.birthday')}
          value={formatFixtureDate(person.birthday, language)}
        />
        <KeyValue
          label={t('person.phone')}
          value={person.maskedPhone ?? t('common.notVerified')}
        />
        {!needsAttention && !isExcluded ? (
          <KeyValue
            label={t('person.message')}
            value={fixtureMessageFor(person.givenName)}
          />
        ) : null}
      </Card>

      {isExcluded ? (
        <StatusRow
          title={t('common.excluded')}
          detail={t('person.attentionBody')}
          tone="warning"
        />
      ) : needsAttention ? (
        <>
          <StatusRow
            title={t('common.needsAttention')}
            detail={t('person.attentionBody')}
            tone="critical"
          />
          <Button
            label={t('person.repair')}
            onPress={() => repairPerson(person.id)}
            testID="person-repair-fixture"
          />
        </>
      ) : (
        <>
          {isRepaired ? (
            <StatusRow
              title={t('person.repaired')}
              tone="info"
              testID="person-repaired-result"
            />
          ) : null}
          <Button
            label={isEnabled ? t('person.pause') : t('person.enable')}
            onPress={() => togglePerson(person.id)}
            testID="person-toggle-enabled"
          />
          <Button
            label={t('home.viewApproved')}
            onPress={() => navigation.navigate('ApprovedMessage')}
            variant="secondary"
          />
        </>
      )}
    </Screen>
  );
}
