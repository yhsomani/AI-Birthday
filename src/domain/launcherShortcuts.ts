import shortcutDefinitions from '../config/launcherShortcuts.json';
import { parseRelateDeepLink, resolveDeepLinkDestination, type DeepLinkDestination } from './deepLinks';
import type { AppState } from './types';

export type LauncherShortcutDefinition = {
  id: string;
  shortLabel: string;
  longLabel: string;
  disabledMessage: string;
  url: string;
  rank: number;
  effect: 'navigate';
};

export type LauncherShortcutResolution =
  | {
      ok: true;
      shortcut: LauncherShortcutDefinition;
      destination: DeepLinkDestination;
    }
  | {
      ok: false;
      message: string;
      destination: DeepLinkDestination;
    };

export const launcherShortcuts = shortcutDefinitions as LauncherShortcutDefinition[];

export const resolveLauncherShortcut = (
  state: AppState,
  shortcutId: string
): LauncherShortcutResolution => {
  const shortcut = launcherShortcuts.find(item => item.id === shortcutId);
  if (!shortcut) {
    return {
      ok: false,
      message: 'This launcher shortcut is no longer supported.',
      destination: { screen: 'home' }
    };
  }

  if (shortcut.effect !== 'navigate') {
    return {
      ok: false,
      message: 'This launcher shortcut is not a safe navigation action.',
      destination: { screen: 'home' }
    };
  }

  const parsed = parseRelateDeepLink(shortcut.url);
  if (!parsed.ok) {
    return {
      ok: false,
      message: parsed.message,
      destination: parsed.fallback
    };
  }

  const resolved = resolveDeepLinkDestination(state, parsed.destination);
  if (!resolved.ok) {
    return {
      ok: false,
      message: resolved.message,
      destination: resolved.destination
    };
  }

  return {
    ok: true,
    shortcut,
    destination: resolved.destination
  };
};

export const validateLauncherShortcutContract = (state: AppState) => {
  const errors: string[] = [];

  launcherShortcuts.forEach(shortcut => {
    if (shortcut.effect !== 'navigate') {
      errors.push(`${shortcut.id} is not navigation-only.`);
    }

    if (!shortcut.url.startsWith('relateai://')) {
      errors.push(`${shortcut.id} does not use the RelateAI link scheme.`);
    }

    const resolution = resolveLauncherShortcut(state, shortcut.id);
    if (!resolution.ok) {
      errors.push(`${shortcut.id} does not resolve safely: ${resolution.message}`);
    }

    if (resolution.ok && resolution.destination.screen === 'wishPreview') {
      errors.push(`${shortcut.id} routes directly to a message preview without a reviewed notification context.`);
    }
  });

  return {
    ok: errors.length === 0,
    errors
  };
};
