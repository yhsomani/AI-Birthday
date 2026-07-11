import React, { useState } from 'react';
import { Pressable, SafeAreaView, ScrollView, Text, TextInput } from 'react-native';
import type { SupportedLocale } from '../domain/types';
import { t } from '../i18n/i18n';

export type MinimalFunctionalShellProps = {
  locale: SupportedLocale;
  phase: string;
  summary: readonly string[];
  operations: readonly string[];
  issues: readonly string[];
  initialCommand: string;
  maxCommandLength: number;
  maxSecretLength: number;
  execute(raw: string, secret: string): Promise<Readonly<{ output: string; clearInput: boolean }>>;
};

/**
 * Temporary functionality-only interface. It intentionally has no design
 * tokens, layout system, animation, iconography, or feature-specific visuals.
 */
export const MinimalFunctionalShell = ({
  locale,
  phase,
  summary,
  operations,
  issues,
  initialCommand,
  maxCommandLength,
  maxSecretLength,
  execute
}: MinimalFunctionalShellProps) => {
  const [rawCommand, setRawCommand] = useState(initialCommand);
  const [commandSecret, setCommandSecret] = useState('');
  const [result, setResult] = useState(t(locale, 'functionalConsole.result.none'));
  const [running, setRunning] = useState(false);
  const commandEnabled = phase === 'ready' || phase === 'locked' || phase === 'failed';

  const run = async () => {
    if (running) return;
    setRunning(true);
    try {
      const execution = await execute(rawCommand, commandSecret);
      setResult(execution.output);
      if (execution.clearInput) setRawCommand('');
    } catch {
      setResult(t(locale, 'functionalConsole.result.failed'));
    } finally {
      // Secrets are always ephemeral. Non-secret text remains available after
      // failures so an import, edit, or training sample can be corrected and
      // retried without re-entry.
      setCommandSecret('');
      setRunning(false);
    }
  };

  return (
    <SafeAreaView>
      <ScrollView keyboardShouldPersistTaps="handled">
        <Text accessibilityRole="header">{t(locale, 'functionalConsole.title')}</Text>
        <Text>{t(locale, 'functionalConsole.runtime', { phase })}</Text>

        <Text accessibilityRole="header">{t(locale, 'functionalConsole.stateSummary')}</Text>
        {summary.map(line => (
          <Text key={line}>{line}</Text>
        ))}

        <Text accessibilityRole="header">{t(locale, 'functionalConsole.command')}</Text>
        <TextInput
          accessibilityLabel={t(locale, 'functionalConsole.commandJson')}
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={maxCommandLength}
          multiline
          onChangeText={setRawCommand}
          value={rawCommand}
        />
        <TextInput
          accessibilityLabel={t(locale, 'functionalConsole.secret')}
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={maxSecretLength}
          onChangeText={setCommandSecret}
          secureTextEntry
          value={commandSecret}
        />
        <Pressable
          accessibilityLabel={t(locale, 'functionalConsole.execute')}
          accessibilityRole="button"
          accessibilityState={{ disabled: running || !commandEnabled }}
          disabled={running || !commandEnabled}
          onPress={() => void run()}
        >
          <Text>{t(locale, running ? 'functionalConsole.running' : 'functionalConsole.execute')}</Text>
        </Pressable>
        <Text accessibilityLiveRegion="polite">{result}</Text>

        <Text accessibilityRole="header">{t(locale, 'functionalConsole.operations')}</Text>
        {operations.length === 0 ? <Text>{t(locale, 'functionalConsole.operationsEmpty')}</Text> : null}
        {operations.map(line => (
          <Text key={line}>{line}</Text>
        ))}

        <Text accessibilityRole="header">{t(locale, 'functionalConsole.issues')}</Text>
        {issues.length === 0 ? <Text>{t(locale, 'functionalConsole.issuesEmpty')}</Text> : null}
        {issues.map(line => (
          <Text key={line}>{line}</Text>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
};
