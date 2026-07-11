import type { HarnessCommand } from './commandRuntimeTypes';

/**
 * Discoverability contract for the temporary functionality-only console.
 * Keeping this list exhaustively typed makes a newly reachable command fail
 * typecheck until it is also discoverable from the only active interface.
 */
export const supportedHarnessCommandTypes = [
  'system.catalog',
  'home.inspect',
  'home.open-action',
  'contacts.query',
  'contacts.inspect',
  'contacts.add',
  'contacts.edit-preview',
  'contacts.edit-apply',
  'contacts.archive-preview',
  'contacts.archive-apply',
  'contacts.restore',
  'contacts.delete-preview',
  'contacts.delete-apply',
  'contacts.merge-preview',
  'contacts.merge-apply',
  'contacts.import',
  'contacts.import-preview',
  'contacts.import-apply',
  'contacts.preferences.inspect',
  'contacts.preferences.set-tone',
  'contacts.preferences.set-group',
  'contacts.preferences.set-channel',
  'contacts.preferences.set-vip',
  'contacts.preferences.set-dnd',
  'contacts.preferences.set-cadence',
  'contacts.preferences.set-automation',
  'contacts.preferences.set-send-time',
  'contacts.preferences.set-quiet-hours',
  'contacts.preferences.set-skip-auto',
  'contacts.preferences.use-group-defaults',
  'contacts.enrichment.inspect',
  'contacts.enrichment.answer',
  'groups.inspect',
  'groups.set-default',
  'events.query',
  'events.add',
  'events.edit-preview',
  'events.edit-apply',
  'events.delete-preview',
  'events.delete-apply',
  'events.merge-preview',
  'events.merge-apply',
  'events.preparation.inspect',
  'events.preparation.toggle',
  'events.import-text',
  'events.import-file',
  'calendar.import',
  'calendar.import-preview',
  'calendar.import-apply',
  'calendar.export',
  'reminders.reconcile',
  'messages.query',
  'messages.bulk-preview',
  'messages.bulk-apply',
  'messages.preview',
  'messages.edit',
  'messages.set-channel',
  'messages.select-variant',
  'messages.acknowledge-duplicate',
  'messages.approve',
  'messages.reject',
  'messages.revoke',
  'messages.retry',
  'messages.regenerate',
  'messages.test-route',
  'messages.schedule-follow-up',
  'composer.inspect',
  'composer.create-template',
  'templates.inspect',
  'ai.draft',
  'handoff.open',
  'handoff.confirm',
  'email.deliver',
  'email.reconcile',
  'checkins.query',
  'checkins.snooze',
  'checkins.mark-contacted',
  'memories.query',
  'memories.add',
  'memories.edit',
  'memories.set-pinned',
  'memories.delete',
  'gifts.inspect',
  'gifts.add',
  'gifts.delete',
  'gifts.set-budget',
  'timeline.query',
  'chat.query',
  'onboarding.inspect',
  'onboarding.set-goal',
  'onboarding.set-step',
  'onboarding.advance',
  'onboarding.skip',
  'onboarding.complete',
  'onboarding.reopen',
  'account.inspect',
  'account.use-local',
  'account.disconnect',
  'privacy.inspect',
  'privacy.set-whatsapp-consent',
  'settings.inspect',
  'settings.set-boolean',
  'settings.set-locale',
  'settings.set-email-sender',
  'settings.set-automation',
  'settings.set-quiet-hours',
  'settings.set-default-send-time',
  'settings.add-blackout',
  'settings.remove-blackout',
  'style.inspect',
  'style.set-enabled',
  'style.train-samples',
  'style.train-sent',
  'activity.query',
  'activity.resolve',
  'activity.open-action',
  'analytics.inspect',
  'analytics.open-action',
  'analytics.share-summary',
  'analytics.export-preview',
  'analytics.export-confirm',
  'setup.inspect',
  'setup.open-action',
  'setup.wizard.inspect',
  'setup.wizard.run-action',
  'backup.export',
  'backup.export-confirm',
  'backup.select-file',
  'backup.restore-preview',
  'backup.restore-preview-selected',
  'backup.restore-confirm',
  'data.clear',
  'data.recover',
  'permissions.refresh',
  'permissions.preflight',
  'permissions.request',
  'biometric.enable',
  'biometric.disable',
  'biometric.unlock',
  'domain.dispatch',
  'operation.cancel'
] as const satisfies readonly HarnessCommand['type'][];

type MissingCatalogCommand = Exclude<HarnessCommand['type'], (typeof supportedHarnessCommandTypes)[number]>;
export const commandCatalogCoversEveryCommand: [MissingCatalogCommand] extends [never] ? true : never = true;

export const commandCatalogWorkflows = [
  {
    id: 'daily-plan',
    purpose: 'Inspect today and execute one current Home recommendation.',
    examples: ['{"type":"home.inspect"}', '{"type":"home.open-action","actionId":"<id from Home>"}']
  },
  {
    id: 'contact-review',
    purpose: 'Find an active contact, inspect private detail after unlock, then edit through preview/apply.',
    examples: [
      '{"type":"contacts.query","sort":"Name","limit":20}',
      '{"type":"contacts.inspect","contactId":"<contact id>"}',
      '{"type":"contacts.edit-preview","contactId":"<contact id>","input":{"name":"Name","relationship":"Friend","language":"English","notesSummary":""}}'
    ]
  },
  {
    id: 'event-preparation',
    purpose: 'Find events for a month and inspect the occurrence-specific preparation plan.',
    examples: [
      '{"type":"events.query","month":"2026-07","sort":"Date"}',
      '{"type":"events.preparation.inspect","eventId":"<event id>"}',
      '{"type":"events.import-file"}'
    ]
  },
  {
    id: 'message-review',
    purpose: 'Use live counts to find review work, inspect one draft, edit or change channel, then approve.',
    examples: [
      '{"type":"messages.query","tab":"Review","channel":"All","query":"","sort":"Scheduled","limit":20}',
      '{"type":"messages.preview","messageId":"<message id>","includePriorMessages":true}',
      '{"type":"messages.set-channel","messageId":"<message id>","channel":"Manual"}',
      '{"type":"messages.approve","messageId":"<message id>","reviewNext":true}',
      '{"type":"messages.bulk-preview","action":"Approve","messageIds":["<selected message id>"]}',
      '{"type":"messages.bulk-apply","confirmationToken":"<bulk preview token>"}'
    ]
  },
  {
    id: 'manual-message',
    purpose: 'Create a local review-first message without an event or AI.',
    examples: [
      '{"type":"composer.inspect","contactId":"<contact id>","reason":"Check-in"}',
      '{"type":"composer.create-template","contactId":"<contact id>","reason":"Check-in","body":"<edited body>"}'
    ]
  },
  {
    id: 'safe-handoff',
    purpose: 'Open an approved message or its copy/share fallback, then explicitly confirm the actual send.',
    examples: [
      '{"type":"handoff.open","messageId":"<message id>","preferFallback":false}',
      '{"type":"handoff.confirm","messageId":"<message id>","sent":true}'
    ]
  },
  {
    id: 'setup-recovery',
    purpose: 'Inspect the current setup fix, refresh permissions, or resume an interrupted data operation.',
    examples: [
      '{"type":"setup.inspect"}',
      '{"type":"setup.open-action","checkId":"<check id>"}',
      '{"type":"permissions.refresh"}',
      '{"type":"data.recover"}'
    ]
  },
  {
    id: 'activity-recovery',
    purpose: 'Inspect open activity issues, execute a current recovery action, or explicitly resolve one.',
    examples: [
      '{"type":"activity.query","status":"Open"}',
      '{"type":"activity.open-action","activityId":"<open activity id>"}',
      '{"type":"activity.resolve","activityId":"<open activity id>"}'
    ]
  },
  {
    id: 'analytics-reflection',
    purpose: 'Inspect current derived metrics, open an insight, share a redacted summary, or confirm a secondary CSV.',
    examples: [
      '{"type":"analytics.inspect","range":"Last 30 days"}',
      '{"type":"analytics.open-action","range":"Last 30 days","insightId":"<current insight id>"}',
      '{"type":"analytics.share-summary","range":"Last 30 days"}',
      '{"type":"analytics.export-preview","range":"All time"}',
      '{"type":"analytics.export-confirm","confirmationToken":"<preview token>"}'
    ]
  },
  {
    id: 'app-preferences',
    purpose: 'Inspect major preferences, change the app language, or configure/clear the non-secret email sender.',
    examples: [
      '{"type":"settings.inspect"}',
      '{"type":"settings.set-locale","locale":"hi-IN"}',
      '{"type":"settings.set-email-sender","senderEmail":"sender@example.com"}',
      '{"type":"settings.set-email-sender","senderEmail":""}'
    ]
  },
  {
    id: 'encrypted-backup',
    purpose: 'Export or preview/confirm an atomic restore using the separate secure secret field.',
    examples: [
      '{"type":"backup.export","passphrase":"$SECURE_INPUT","destination":"save"}',
      '{"type":"backup.select-file"}',
      '{"type":"backup.restore-preview-selected","selectionToken":"<selection token>","passphrase":"$SECURE_INPUT"}'
    ]
  }
] as const;

export const buildCommandCatalog = () => ({
  kind: 'command-catalog' as const,
  commandCount: supportedHarnessCommandTypes.length,
  supportedTypes: [...supportedHarnessCommandTypes],
  workflows: commandCatalogWorkflows.map(workflow => ({
    ...workflow,
    examples: [...workflow.examples]
  })),
  guidance: [
    'Run query or inspect commands first to obtain current opaque ids.',
    'Preview/apply commands require the fresh confirmation token returned by preview.',
    'Backup passphrases belong only in the secure field through $SECURE_INPUT.',
    'Run data.recover when a blocking lifecycle issue reports interrupted clear or restore reconciliation.',
    'Failed non-secret input remains editable; successful input and every secret are cleared.'
  ]
});
