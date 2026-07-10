import { availableAutomationModes, productAvailability } from '../config/productAvailability';
import { eventOccurrenceLocalDateKey } from './occasionDates';
import type {
  AppState,
  AutomationMode,
  ContactQuietHoursBehavior,
  MessageChannel,
  MessageDraft,
  RelationshipEvent,
  ReminderPlan,
  ScheduleBlackout,
  SettingsState
} from './types';

/** Modes that are genuinely selectable in this release. */
export const automationModes: AutomationMode[] = [...availableAutomationModes];

export interface SchedulingPolicyIssue {
  id: string;
  severity: 'Info' | 'Warning' | 'Error';
  title: string;
  detail: string;
}

export interface SchedulingPolicySummary {
  canScheduleNotifications: boolean;
  issues: SchedulingPolicyIssue[];
}

export interface ReminderPlanningResult {
  plans: ReminderPlan[];
  issues: SchedulingPolicyIssue[];
  adjustedCount: number;
  skippedCount: number;
}

export interface BlackoutInput {
  label: string;
  startDate: string;
  endDate: string;
  behavior?: 'Block' | 'Defer';
  channels?: MessageChannel[];
}

export interface ContactSchedulingPreferences {
  customSendTime?: string;
  quietHoursBehavior?: ContactQuietHoursBehavior;
}

const timePattern = /^([01]\d|2[0-3]):([0-5]\d)$/;
const datePattern = /^\d{4}-\d{2}-\d{2}$/;
const messageChannels = new Set<MessageChannel>(['SMS', 'WhatsApp', 'Email', 'Manual']);
const MAX_SCHEDULE_TIME_ZONE_LENGTH = 128;

/** Returns a canonical IANA time-zone identity when the runtime supports it. */
export const normalizeScheduleTimeZone = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const candidate = value.trim();
  if (candidate.length === 0 || candidate.length > MAX_SCHEDULE_TIME_ZONE_LENGTH) return undefined;
  try {
    return new Intl.DateTimeFormat('en-US', { timeZone: candidate }).resolvedOptions().timeZone;
  } catch {
    return undefined;
  }
};

/** The current device zone, with UTC as a fail-closed, deterministic platform fallback. */
export const currentScheduleTimeZone = (): string => {
  try {
    return normalizeScheduleTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone) ?? 'UTC';
  } catch {
    return 'UTC';
  }
};

export const scheduleTimeZonesMatch = (left: unknown, right: unknown): boolean => {
  const normalizedLeft = normalizeScheduleTimeZone(left);
  const normalizedRight = normalizeScheduleTimeZone(right);
  return normalizedLeft !== undefined && normalizedLeft === normalizedRight;
};

const parseTime = (value: string) => {
  const match = timePattern.exec(value.trim());
  if (!match) {
    return undefined;
  }
  return Number(match[1]) * 60 + Number(match[2]);
};

const normalizeDateKey = (value: string) => value.trim();

const isRealDateKey = (value: string) => {
  if (!datePattern.test(value)) {
    return false;
  }
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
};

const localDateKey = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const minutesOfDay = (date: Date) => date.getHours() * 60 + date.getMinutes();

const setMinutesOfDay = (date: Date, minutes: number) => {
  const next = new Date(date);
  next.setHours(Math.floor(minutes / 60), minutes % 60, 0, 0);
  return next;
};

const addDays = (date: Date, days: number) => {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
};

export const validateQuietHours = (quietHours: SettingsState['quietHours']) => {
  if (parseTime(quietHours.start) === undefined || parseTime(quietHours.end) === undefined) {
    return 'Quiet hours must use 24-hour HH:mm times.';
  }
  if (quietHours.start === quietHours.end) {
    return 'Quiet hours need different start and end times.';
  }
  return undefined;
};

export const validateDefaultSendTime = (value: string) =>
  parseTime(value) === undefined ? 'Default send time must use a 24-hour HH:mm time.' : undefined;

export const validateBlackoutInput = (input: BlackoutInput) => {
  const label = input.label.trim().replace(/\s+/g, ' ');
  const startDate = normalizeDateKey(input.startDate);
  const endDate = normalizeDateKey(input.endDate);
  if (label.length < 2) {
    return { ok: false as const, message: 'Blackout label is required.' };
  }
  if (!isRealDateKey(startDate) || !isRealDateKey(endDate)) {
    return { ok: false as const, message: 'Blackout dates must use YYYY-MM-DD.' };
  }
  if (startDate > endDate) {
    return { ok: false as const, message: 'Blackout start date must be before or equal to the end date.' };
  }
  if (label.length > 80) {
    return { ok: false as const, message: 'Blackout label is too long.' };
  }
  const behavior = input.behavior ?? 'Defer';
  if (behavior !== 'Block' && behavior !== 'Defer') {
    return { ok: false as const, message: 'Blackout behavior must block or defer.' };
  }
  const channels = input.channels ? [...new Set(input.channels)] : undefined;
  if (
    channels &&
    (channels.length === 0 ||
      channels.length !== input.channels?.length ||
      channels.some(channel => !messageChannels.has(channel)))
  ) {
    return { ok: false as const, message: 'Blackout channels contain an unsupported or duplicate value.' };
  }
  return {
    ok: true as const,
    value: {
      label,
      startDate,
      endDate,
      behavior,
      ...(channels ? { channels } : {})
    }
  };
};

const isWithinQuietHours = (date: Date, quietHours: SettingsState['quietHours']) => {
  const start = parseTime(quietHours.start);
  const end = parseTime(quietHours.end);
  if (start === undefined || end === undefined || start === end) {
    return false;
  }
  const current = minutesOfDay(date);
  return start < end ? current >= start && current < end : current >= start || current < end;
};

const quietHoursEndFor = (date: Date, quietHours: SettingsState['quietHours']) => {
  const start = parseTime(quietHours.start);
  const end = parseTime(quietHours.end);
  if (start === undefined || end === undefined) {
    return date;
  }
  const current = minutesOfDay(date);
  const endDate = setMinutesOfDay(date, end);
  if (start > end && current >= start) {
    return addDays(endDate, 1);
  }
  return endDate;
};

const blackoutFor = (date: Date, blackouts: ScheduleBlackout[], channel?: MessageChannel) => {
  const key = localDateKey(date);
  return blackouts.find(
    blackout =>
      blackout.startDate <= key &&
      key <= blackout.endDate &&
      (!blackout.channels || (channel !== undefined && blackout.channels.includes(channel)))
  );
};

const moveAfterBlackout = (date: Date, blackout: ScheduleBlackout) => {
  const [year, month, day] = blackout.endDate.split('-').map(Number);
  const next = new Date(year, month - 1, day);
  next.setDate(next.getDate() + 1);
  next.setHours(9, 0, 0, 0);
  return next;
};

export const adjustTriggerForSchedulingPolicy = (
  triggerAt: Date,
  settings: SettingsState,
  channel?: MessageChannel
): { triggerAt: Date; adjustments: string[]; blockedBy?: string } => {
  let candidate = new Date(triggerAt);
  const adjustments: string[] = [];
  // Every defer advances beyond at least one blackout or quiet-hours window.
  // The bound scales with persisted policy size so long, valid chains cannot
  // silently fall through into a prohibited window.
  const maximumAdjustments = settings.blackouts.length * 2 + 4;
  for (let attempt = 0; attempt < maximumAdjustments; attempt += 1) {
    if (isWithinQuietHours(candidate, settings.quietHours)) {
      candidate = quietHoursEndFor(candidate, settings.quietHours);
      adjustments.push(`Moved outside quiet hours to ${settings.quietHours.end}.`);
      continue;
    }
    const blackout = blackoutFor(candidate, settings.blackouts, channel);
    if (blackout) {
      if ((blackout.behavior ?? 'Defer') === 'Block') {
        return { triggerAt: candidate, adjustments, blockedBy: blackout.label };
      }
      candidate = moveAfterBlackout(candidate, blackout);
      adjustments.push(`Moved after blackout: ${blackout.label}.`);
      continue;
    }
    return { triggerAt: candidate, adjustments };
  }
  const unresolvedBlackout = blackoutFor(candidate, settings.blackouts, channel);
  return {
    triggerAt: candidate,
    adjustments,
    blockedBy: unresolvedBlackout?.label ?? 'the scheduling policy could not find an allowed window'
  };
};

const localDateAtTime = (dateKey: string, time: string): Date | undefined => {
  if (!isRealDateKey(dateKey)) return undefined;
  const minutes = parseTime(time);
  if (minutes === undefined) return undefined;
  const [year, month, day] = dateKey.split('-').map(Number);
  const value = new Date(year, month - 1, day, Math.floor(minutes / 60), minutes % 60, 0, 0);
  return value.getFullYear() === year && value.getMonth() === month - 1 && value.getDate() === day ? value : undefined;
};

export const scheduleMessageForEvent = (
  event: RelationshipEvent,
  settings: SettingsState,
  channel: MessageChannel,
  reference: Date = new Date(),
  contactPreferences: ContactSchedulingPreferences = {}
): { scheduledFor?: string; scheduledTimeZone?: string; adjustments: string[]; issue?: string } => {
  const scheduledTimeZone = currentScheduleTimeZone();
  const dateKey = eventOccurrenceLocalDateKey(event, reference);
  const sendTime = contactPreferences.customSendTime ?? settings.defaultSendTime;
  const candidate = dateKey ? localDateAtTime(dateKey, sendTime) : undefined;
  if (!candidate) {
    return {
      adjustments: [],
      issue: contactPreferences.customSendTime
        ? 'The event date or contact custom send time is invalid.'
        : 'The event date or default send time is invalid.'
    };
  }
  if (contactPreferences.quietHoursBehavior === 'Block' && isWithinQuietHours(candidate, settings.quietHours)) {
    return {
      scheduledFor: candidate.toISOString(),
      scheduledTimeZone,
      adjustments: [],
      issue:
        'The contact quiet-hours preference blocks this intended send time. Choose a time outside global quiet hours.'
    };
  }
  const adjusted = adjustTriggerForSchedulingPolicy(candidate, settings, channel);
  if (adjusted.blockedBy) {
    return {
      scheduledFor: candidate.toISOString(),
      scheduledTimeZone,
      adjustments: adjusted.adjustments,
      issue: `Sending is blocked by blackout: ${adjusted.blockedBy}.`
    };
  }
  return {
    scheduledFor: adjusted.triggerAt.toISOString(),
    scheduledTimeZone,
    adjustments: adjusted.adjustments
  };
};

/** Rechecks the actual dispatch moment; opening a destination is a dispatch attempt. */
export const messageDispatchTimingIssue = (
  state: AppState,
  message: MessageDraft,
  now: Date = new Date()
): string | undefined => {
  if (message.scheduledFor) {
    const currentTimeZone = currentScheduleTimeZone();
    if (message.scheduledTimeZone && !scheduleTimeZonesMatch(message.scheduledTimeZone, currentTimeZone)) {
      return `The device time zone changed from ${message.scheduledTimeZone} to ${currentTimeZone}. Return the message to review before sending.`;
    }
    const scheduled = new Date(message.scheduledFor);
    if (Number.isNaN(scheduled.getTime()))
      return 'The approved send schedule is invalid. Return the message to review.';
    if (scheduled.getTime() > now.getTime()) return 'This message is not due yet. Wait for its approved send time.';
  }
  const contact = state.contacts.find(item => item.id === message.contactId);
  if (contact?.quietHoursBehavior === 'Block' && isWithinQuietHours(now, state.settings.quietHours)) {
    return 'The contact quiet-hours preference blocks dispatch during global quiet hours.';
  }
  const currentWindow = adjustTriggerForSchedulingPolicy(now, state.settings, message.channel);
  if (currentWindow.blockedBy)
    return `Sending is blocked by the current scheduling policy: ${currentWindow.blockedBy}.`;
  if (currentWindow.triggerAt.getTime() !== now.getTime()) {
    return `Sending is deferred until ${currentWindow.triggerAt.toISOString()} by the current schedule policy.`;
  }
  return undefined;
};

export const buildSchedulingPolicySummary = (state: AppState): SchedulingPolicySummary => {
  const issues: SchedulingPolicyIssue[] = [];
  if (!state.settings.notificationsEnabled) {
    issues.push({
      id: 'notifications-disabled',
      severity: 'Error',
      title: 'Notifications are off',
      detail: 'Reminder plans stay in-app until notifications are enabled.'
    });
  }

  const quietHoursProblem = validateQuietHours(state.settings.quietHours);
  if (quietHoursProblem) {
    issues.push({
      id: 'quiet-hours-invalid',
      severity: 'Error',
      title: 'Quiet hours need review',
      detail: quietHoursProblem
    });
  }

  const sendTimeProblem = validateDefaultSendTime(state.settings.defaultSendTime);
  if (sendTimeProblem) {
    issues.push({
      id: 'default-send-time-invalid',
      severity: 'Error',
      title: 'Default send time needs review',
      detail: sendTimeProblem
    });
  }

  state.settings.blackouts.forEach(blackout => {
    const validation = validateBlackoutInput(blackout);
    if (!validation.ok) {
      issues.push({
        id: `blackout-invalid-${blackout.id}`,
        severity: 'Error',
        title: 'Blackout needs review',
        detail: validation.message
      });
    }
  });

  if (state.settings.automationMode === 'Fully auto') {
    issues.push({
      id: 'fully-auto-unavailable',
      severity: 'Warning',
      title: 'Unattended automation is unavailable',
      detail: productAvailability.durableUnattendedAutomation.reason
    });
  }

  if (state.settings.automationMode === 'Always ask') {
    issues.push({
      id: 'always-ask',
      severity: 'Info',
      title: 'Always ask is active',
      detail: 'The app can prepare reminder plans, but the user decides when to schedule or send.'
    });
  }

  return {
    canScheduleNotifications: state.settings.notificationsEnabled && !issues.some(issue => issue.severity === 'Error'),
    issues
  };
};
