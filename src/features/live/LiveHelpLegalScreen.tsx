import React, { useCallback, useState } from 'react';
import { Linking } from 'react-native';

import type {
  PublicResourceKind,
  PublicResourcesProjection,
} from '../../domain/legal/model';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import { LiveCloudPrivacyBoundary } from './LiveCloudPrivacyBoundary';
import { LiveError, LiveLoading } from './LiveProjectionState';
import { useLiveProjection } from './useLiveProjection';

const resources: readonly Readonly<{
  kind: PublicResourceKind;
  label: TranslationKey;
  path: '/privacy' | '/terms' | '/support' | '/delete';
}>[] = [
  { kind: 'privacy', label: 'live.help.privacy', path: '/privacy' },
  { kind: 'terms', label: 'live.help.terms', path: '/terms' },
  { kind: 'support', label: 'live.help.support', path: '/support' },
  {
    kind: 'delete-account',
    label: 'live.help.deleteAccount',
    path: '/delete',
  },
];

export function LiveHelpLegalScreen({
  onBack,
  platform,
  port,
}: {
  onBack: () => void;
  platform: PlatformCapability['platform'];
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadResources = useCallback(() => port.getPublicResources(), [port]);
  const projection = useLiveProjection(loadResources, port, ['privacy']);
  const [opening, setOpening] = useState<PublicResourceKind>();
  const [openFailed, setOpenFailed] = useState(false);
  const availableResources =
    projection.state.kind === 'ready' &&
    projection.state.result.envelope.value.kind === 'available'
      ? projection.state.result.envelope.value
      : undefined;

  const openResource = async (
    value: Extract<PublicResourcesProjection, { kind: 'available' }>,
    resource: (typeof resources)[number],
  ) => {
    setOpening(resource.kind);
    setOpenFailed(false);
    try {
      await Linking.openURL(`${value.baseUrl}${resource.path}`);
    } catch {
      setOpenFailed(true);
    }
    setOpening(undefined);
  };

  return (
    <Screen includeTopInset testID="live-help-legal-screen">
      <Button label={t('live.common.back')} onPress={onBack} variant="ghost" />
      <AppText variant="title" accessibilityRole="header">
        {t('live.help.title')}
      </AppText>
      <AppText color="muted">{t('live.help.body')}</AppText>

      <Card>
        <SectionHeading title={t('live.help.about')} />
        <StatusRow
          title={t(
            platform === 'android'
              ? 'live.common.androidEdition'
              : 'live.common.iosEdition',
          )}
          detail={t(
            platform === 'android'
              ? 'live.help.androidLimitation'
              : 'live.help.iosLimitation',
          )}
          tone="info"
        />
        {projection.state.kind === 'ready' ? (
          <StatusRow
            title={t('live.help.build')}
            detail={projection.state.result.envelope.value.buildLabel}
          />
        ) : null}
      </Card>

      <ReadinessBanner
        title={t('live.help.externalCopies')}
        detail={t('live.help.externalCopiesBody')}
        tone="warning"
      />
      <LiveCloudPrivacyBoundary platform={platform} />
      {openFailed ? (
        <ReadinessBanner
          title={t('live.help.openFailed')}
          detail={t('live.help.openFailedBody')}
          tone="critical"
        />
      ) : null}

      {projection.state.kind === 'loading' ? (
        <LiveLoading label={t('live.help.loading')} />
      ) : null}
      {projection.state.kind === 'error' ? (
        <LiveError
          title={t('live.help.unavailable')}
          problem={projection.state.problem}
          onRetry={() => projection.reload()}
        />
      ) : null}
      {projection.state.kind === 'ready' &&
      projection.state.result.envelope.value.kind === 'unavailable' ? (
        <ReadinessBanner
          title={t('live.help.linksUnavailable')}
          detail={t('live.help.linksUnavailableBody')}
          tone="warning"
        />
      ) : null}
      {availableResources ? (
        <Card>
          <SectionHeading title={t('live.help.resources')} />
          {resources.map(resource => (
            <Button
              key={resource.kind}
              label={
                opening === resource.kind
                  ? t('live.help.opening')
                  : t(resource.label)
              }
              disabled={opening !== undefined}
              onPress={() => openResource(availableResources, resource)}
              variant="secondary"
              testID={`live-help-${resource.kind}`}
            />
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}
