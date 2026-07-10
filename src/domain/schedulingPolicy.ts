import { availableAutomationModes, productAvailability } from '../config/productAvailability';
import type { AppState, AutomationMode, ReminderPlan, ScheduleBlackout, SettingsState } from './types';

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
}

const timePattern = /^([01]\d|2[0-3]):([0-5]\d)$/;
const datePattern = /^\d{4}-\d{2}-\d{2}$/;

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
  return {
    ok: true as const,
    value: {
      label,
      startDate,
      endDate
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

const blackoutFor = (date: Date, blackouts: ScheduleBlackout[]) => {
  const key = localDateKey(date);
  return blackouts.find(blackout => blackout.startDate <= key && key <= blackout.endDate);
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
  settings: SettingsState
): { triggerAt: Date; adjustments: string[] } => {
  let candidate = new Date(triggerAt);
  const adjustments: string[] = [];
  for (let attempt = 0; attempt < 10; attempt += 1) {
    if (isWithinQuietHours(candidate, settings.quietHours)) {
      candidate = quietHoursEndFor(candidate, settings.quietHours);
      adjustments.push(`Moved outside quiet hours to ${settings.quietHours.end}.`);
      continue;
    }
    const blackout = blackoutFor(candidate, settings.blackouts);
    if (blackout) {
      candidate = moveAfterBlackout(candidate, blackout);
      adjustments.push(`Moved after blackout: ${blackout.label}.`);
      continue;
    }
    break;
  }
  return { triggerAt: candidate, adjustments };
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
