import React, { useDeferredValue, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { CompositeScreenProps } from '@react-navigation/native';
import { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import {
  MainTabParamList,
  RootStackParamList,
} from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  ChoiceChip,
  FixtureNotice,
  PersonRow,
  Screen,
  SearchField,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { formatFixtureDate } from '../../localization/i18n';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { FixturePersonStatus, fixturePeople } from '../fixtures/data';

type Filter = 'all' | 'enabled' | FixturePersonStatus;

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'People'>,
  NativeStackScreenProps<RootStackParamList>
>;

export function PeopleScreen({ navigation }: Props) {
  const { selectedPersonIds, repairedPersonIds } = useFixture();
  const { language, t } = useAppLocalization();
  const [query, setQuery] = useState('');

  // ⚡ Bolt: Wrap search query in useDeferredValue to prevent heavy synchronous
  // list filtering from blocking the main UI thread during rapid typing.
  const deferredQuery = useDeferredValue(query);
  const [filter, setFilter] = useState<Filter>('all');

  const people = useMemo(
    () =>
      fixturePeople.filter(person => {
        const isRepaired = repairedPersonIds.includes(person.id);
        const status = isRepaired ? 'ready' : person.status;
        const matchesQuery = person.name
          .toLocaleLowerCase()
          .includes(deferredQuery.trim().toLocaleLowerCase());
        const matchesFilter =
          filter === 'all' ||
          (filter === 'enabled' && selectedPersonIds.includes(person.id)) ||
          filter === status;
        return matchesQuery && matchesFilter;
      }),
    [deferredQuery, filter, repairedPersonIds, selectedPersonIds],
  );

  const statusLabel = (personId: string, status: FixturePersonStatus) => {
    if (selectedPersonIds.includes(personId)) {
      return t('common.enabled');
    }
    if (repairedPersonIds.includes(personId) || status === 'ready') {
      return t('common.ready');
    }
    if (status === 'attention') {
      return t('common.needsAttention');
    }
    return t('common.excluded');
  };

  return (
    <Screen testID="people-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('people.title')}
      </AppText>
      <SearchField
        value={query}
        onChangeText={setQuery}
        label={t('people.search')}
        hint={t('people.searchHint')}
      />
      <View accessibilityRole="radiogroup" style={styles.filters}>
        <ChoiceChip
          label={t('people.filterAll')}
          selected={filter === 'all'}
          onPress={() => setFilter('all')}
          testID="people-filter-all"
        />
        <ChoiceChip
          label={t('people.filterEnabled')}
          selected={filter === 'enabled'}
          onPress={() => setFilter('enabled')}
          testID="people-filter-enabled"
        />
        <ChoiceChip
          label={t('people.filterReady')}
          selected={filter === 'ready'}
          onPress={() => setFilter('ready')}
          testID="people-filter-ready"
        />
        <ChoiceChip
          label={t('people.filterAttention')}
          selected={filter === 'attention'}
          onPress={() => setFilter('attention')}
          testID="people-filter-attention"
        />
        <ChoiceChip
          label={t('people.filterExcluded')}
          selected={filter === 'excluded'}
          onPress={() => setFilter('excluded')}
          testID="people-filter-excluded"
        />
      </View>
      <View accessibilityRole="list" style={styles.list}>
        {people.length === 0 ? (
          <AppText color="muted">{t('people.empty')}</AppText>
        ) : (
          people.map(person => {
            const birthday = formatFixtureDate(person.birthday, language);
            const status = statusLabel(person.id, person.status);
            return (
              <PersonRow
                key={person.id}
                initials={person.initials}
                name={person.name}
                birthday={birthday}
                {...(person.maskedPhone ? { phone: person.maskedPhone } : {})}
                status={status}
                onPress={() =>
                  navigation.getParent()?.navigate('PersonDetail', {
                    personId: person.id,
                  })
                }
                accessibilityLabel={`${person.name}. ${birthday}. ${
                  person.maskedPhone ?? ''
                }. ${status}. ${t('common.viewDetails')}`}
                testID={`people-row-${person.id}`}
              />
            );
          })
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  list: { gap: spacing.sm },
});
