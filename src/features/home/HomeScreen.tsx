import React from 'react';
import { StyleSheet, View } from 'react-native';
import { CompositeScreenProps } from '@react-navigation/native';
import { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { useFixture } from '../../app/providers/FixtureProvider';
import {
  MainTabParamList,
  RootStackParamList,
} from '../../app/navigation/types';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  FixtureNotice,
  KeyValue,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { formatFixtureDate } from '../../localization/i18n';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { fixtureMessageFor, fixturePeople } from '../fixtures/data';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'Home'>,
  NativeStackScreenProps<RootStackParamList>
>;

export function HomeScreen({ navigation }: Props) {
  const {
    platform,
    selectedPersonIds,
    planPaused,
    attentionReviewed,
    togglePlan,
  } = useFixture();
  const { language, t } = useAppLocalization();
  const nextPerson = fixturePeople.find(person =>
    selectedPersonIds.includes(person.id),
  );

  const openPeople = () => navigation.navigate('People');
  const openPreview = () => navigation.getParent()?.navigate('ApprovedMessage');
  const openAttention = () => navigation.getParent()?.navigate('Attention');
  const openActivity = () => navigation.getParent()?.navigate('Activity');

  const statusTitle = attentionReviewed
    ? t('home.attentionResolved')
    : platform === 'android'
    ? t('home.androidStatus')
    : t('home.iosStatus');
  const statusDetail =
    platform === 'android'
      ? t('home.androidStatusBody')
      : t('home.iosStatusBody');

  return (
    <Screen testID="home-screen">
      <FixtureNotice />
      <View style={styles.titleBlock}>
        <AppText variant="display" accessibilityRole="header">
          {t('home.title')}
        </AppText>
        <AppText color="accent" variant="label">
          {platform === 'android' ? t('home.androidMode') : t('home.iosMode')}
        </AppText>
      </View>

      <ReadinessBanner
        title={planPaused ? t('home.paused') : statusTitle}
        detail={statusDetail}
        tone={attentionReviewed ? 'info' : 'warning'}
        {...(attentionReviewed
          ? {}
          : { actionLabel: t('home.fix'), onAction: openAttention })}
      />

      <Card>
        <SectionHeading title={t('home.next')} />
        {nextPerson ? (
          <>
            <AppText variant="heading">{nextPerson.name}</AppText>
            <AppText color="muted">
              {formatFixtureDate(nextPerson.birthday, language)}
            </AppText>
            <AppText color="muted">{nextPerson.maskedPhone}</AppText>
            <AppText>{fixtureMessageFor(nextPerson.givenName)}</AppText>
            <Button
              label={
                platform === 'android'
                  ? t('home.viewApproved')
                  : t('home.reviewComposer')
              }
              onPress={openPreview}
              variant="secondary"
              testID="home-open-preview"
            />
          </>
        ) : (
          <>
            <AppText color="muted">{t('home.noUpcoming')}</AppText>
            <Button
              label={t('home.choosePeople')}
              onPress={openPeople}
              variant="secondary"
            />
          </>
        )}
      </Card>

      <View style={styles.stats}>
        <Card style={styles.statCard}>
          <KeyValue label={t('home.today')} value="0" />
        </Card>
        <Card style={styles.statCard}>
          <KeyValue
            label={t('home.nextSeven')}
            value={String(selectedPersonIds.length)}
          />
        </Card>
        <Card style={styles.statCard}>
          <KeyValue
            label={t('home.enabledCount')}
            value={String(selectedPersonIds.length)}
          />
        </Card>
      </View>

      <Card>
        <StatusRow
          title={t('home.contactsStatus')}
          detail={t('home.contactsFixture')}
          tone="info"
        />
        {platform === 'android' ? (
          <>
            <StatusRow
              title={t('home.safetyStatus')}
              detail={t('home.safetyUnverified')}
              tone="warning"
            />
            <StatusRow
              title={t('home.workerStatus')}
              detail={t('home.workerUnverified')}
              tone="warning"
            />
          </>
        ) : (
          <StatusRow
            title={t('home.reminderStatus')}
            detail={t('home.reminderUnverified')}
            tone="warning"
          />
        )}
      </Card>

      <Button
        label={planPaused ? t('home.resume') : t('home.pause')}
        onPress={togglePlan}
        variant="secondary"
        icon={planPaused ? 'play' : 'pause'}
        testID="home-toggle-plan"
      />
      <Button
        label={t('home.activity')}
        onPress={openActivity}
        variant="ghost"
        icon="activity"
        testID="home-open-activity"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  titleBlock: { gap: spacing.xs },
  stats: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  statCard: { flexGrow: 1, flexBasis: 96 },
});
