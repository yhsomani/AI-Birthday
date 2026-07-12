import React from 'react';
import { StyleSheet, View } from 'react-native';

import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  FixtureNotice,
  KeyValue,
  PersonRow,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { formatFixtureDate } from '../../localization/i18n';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { fixtureMessageFor, fixturePeople } from '../fixtures/data';

const totalSteps = 4;

export function SetupJourneyScreen() {
  const {
    setupStep,
    platform,
    selectedPersonIds,
    nextSetup,
    previousSetup,
    connectFixture,
    togglePerson,
    completeSetup,
  } = useFixture();
  const { language, t } = useAppLocalization();

  return (
    <Screen includeTopInset testID={`setup-step-${setupStep + 1}`}>
      <FixtureNotice />
      <AppText variant="caption" color="muted">
        {t('setup.progress', { step: setupStep + 1, total: totalSteps })}
      </AppText>

      {setupStep === 0 ? (
        <>
          <AppText variant="display" accessibilityRole="header">
            {t('setup.welcomeTitle')}
          </AppText>
          <Card>
            <SectionHeading
              title={
                platform === 'android'
                  ? t('setup.androidEdition')
                  : t('setup.iosEdition')
              }
              supporting={
                platform === 'android'
                  ? t('setup.androidEditionBody')
                  : t('setup.iosEditionBody')
              }
            />
            <StatusRow
              title={t('common.fixtureOnly')}
              detail={t('common.notVerified')}
              tone="info"
            />
          </Card>
          <Card>
            <StatusRow
              title={t('setup.reliabilityTitle')}
              detail={t('setup.reliabilityBody')}
              tone="neutral"
            />
            {platform === 'android' ? (
              <StatusRow
                title={t('setup.costTitle')}
                detail={t('setup.costBody')}
                tone="warning"
              />
            ) : null}
          </Card>
          <Button
            label={t('common.continue')}
            onPress={nextSetup}
            testID="setup-welcome-continue"
          />
        </>
      ) : null}

      {setupStep === 1 ? (
        <>
          <AppText variant="title" accessibilityRole="header">
            {t('setup.contactsTitle')}
          </AppText>
          <Card>
            <AppText>{t('setup.contactsBody')}</AppText>
            <StatusRow title={t('setup.contactsSafety')} tone="positive" />
            <StatusRow
              title={t('common.fixtureOnly')}
              detail={t('common.notVerified')}
              tone="info"
            />
          </Card>
          <Button
            label={t('setup.syntheticConnect')}
            onPress={connectFixture}
            testID="setup-connect-fixture"
          />
          <Button
            label={t('common.back')}
            onPress={previousSetup}
            variant="ghost"
          />
        </>
      ) : null}

      {setupStep === 2 ? (
        <>
          <AppText variant="title" accessibilityRole="header">
            {t('setup.chooseTitle')}
          </AppText>
          <AppText color="muted">{t('setup.chooseBody')}</AppText>
          <View accessibilityRole="list" style={styles.list}>
            {fixturePeople
              .filter(person => person.status === 'ready')
              .map(person => {
                const selected = selectedPersonIds.includes(person.id);
                const birthday = formatFixtureDate(person.birthday, language);
                return (
                  <PersonRow
                    key={person.id}
                    initials={person.initials}
                    name={person.name}
                    birthday={birthday}
                    {...(person.maskedPhone
                      ? { phone: person.maskedPhone }
                      : {})}
                    status={
                      selected ? t('common.selected') : t('common.notSelected')
                    }
                    selected={selected}
                    role="checkbox"
                    onPress={() => togglePerson(person.id)}
                    accessibilityLabel={`${person.name}. ${birthday}. ${
                      person.maskedPhone
                    }. ${
                      selected ? t('common.selected') : t('common.notSelected')
                    }`}
                    testID={`setup-person-${person.id}`}
                  />
                );
              })}
          </View>
          {selectedPersonIds.length === 0 ? (
            <StatusRow title={t('setup.chooseRequired')} tone="warning" />
          ) : (
            <Card>
              {fixturePeople
                .filter(person => selectedPersonIds.includes(person.id))
                .map(person => (
                  <KeyValue
                    key={person.id}
                    label={`${t('setup.messageLabel')} — ${person.name}`}
                    value={fixtureMessageFor(person.givenName)}
                  />
                ))}
            </Card>
          )}
          <Button
            label={t('setup.reviewSelection')}
            onPress={nextSetup}
            disabled={selectedPersonIds.length === 0}
            testID="setup-review-selection"
          />
          <Button
            label={t('common.back')}
            onPress={previousSetup}
            variant="ghost"
          />
        </>
      ) : null}

      {setupStep === 3 ? (
        <>
          <AppText variant="title" accessibilityRole="header">
            {t('setup.reviewTitle')}
          </AppText>
          <Card>
            <KeyValue
              label={t('home.enabledCount')}
              value={String(selectedPersonIds.length)}
            />
            {fixturePeople
              .filter(person => selectedPersonIds.includes(person.id))
              .map(person => (
                <KeyValue
                  key={person.id}
                  label={`${t('setup.messageLabel')} — ${person.name}`}
                  value={fixtureMessageFor(person.givenName)}
                />
              ))}
            <KeyValue
              label={t('setup.windowLabel')}
              value={t('setup.windowValue')}
            />
          </Card>
          <StatusRow
            title={
              platform === 'android'
                ? t('setup.androidEdition')
                : t('setup.iosEdition')
            }
            detail={
              platform === 'android'
                ? t('setup.androidReview')
                : t('setup.iosReview')
            }
            tone="warning"
          />
          <Button
            label={
              platform === 'android'
                ? t('setup.finishAndroid')
                : t('setup.finishIos')
            }
            onPress={completeSetup}
            testID="setup-finish"
          />
          <Button
            label={t('common.back')}
            onPress={previousSetup}
            variant="ghost"
          />
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.sm },
});
