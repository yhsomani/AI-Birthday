import type { AppState, ReminderPlan, RelationshipEvent } from './types';
import { eventOccurrenceLocalDateKey } from './occasionDates';
import {
  adjustTriggerForSchedulingPolicy,
  buildSchedulingPolicySummary,
  type ReminderPlanningResult,
  type SchedulingPolicyIssue
} from './schedulingPolicy';

const daysBefore = (dateKey: string, days: number) => {
  const [year, month, day] = dateKey.split('-').map(Number);
  const date = new Date(year, month - 1, day, 9, 0, 0, 0);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return undefined;
  }
  date.setDate(date.getDate() - days);
  return date;
};

const buildPlan = (
  state: AppState,
  event: RelationshipEvent,
  days: number,
  now: Date,
  issues: SchedulingPolicyIssue[]
): { plan?: ReminderPlan; adjusted: boolean; skipped: boolean } => {
  const contact = state.contacts.find(item => item.id === event.contactId);
  if (!contact || contact.archivedAt) {
    return { skipped: true, adjusted: false };
  }
  const occurrenceDate = eventOccurrenceLocalDateKey(event, now);
  if (!occurrenceDate) {
    return { skipped: true, adjusted: false };
  }
  const originalTriggerAt = daysBefore(occurrenceDate, days);
  if (!originalTriggerAt) return { skipped: true, adjusted: false };
  const adjustedTrigger = adjustTriggerForSchedulingPolicy(originalTriggerAt, state.settings);
  if (adjustedTrigger.blockedBy) {
    issues.push({
      id: `blocked-${event.id}-${days}`,
      severity: 'Warning',
      title: 'Reminder blocked',
      detail: `${event.label} is blocked by blackout: ${adjustedTrigger.blockedBy}.`
    });
    return { skipped: true, adjusted: adjustedTrigger.adjustments.length > 0 };
  }
  const triggerAt = adjustedTrigger.triggerAt;
  if (triggerAt.getTime() <= now.getTime()) {
    return { skipped: true, adjusted: adjustedTrigger.adjustments.length > 0 };
  }
  if (adjustedTrigger.adjustments.length > 0) {
    issues.push({
      id: `adjusted-${event.id}-${days}`,
      severity: 'Info',
      title: 'Reminder moved',
      detail: `${event.label} was ${adjustedTrigger.adjustments.join(' ')}`
    });
  }
  return {
    plan: {
      id: `reminder-${event.id}-${days}`,
      eventId: event.id,
      contactId: event.contactId,
      title: 'RelateAI reminder',
      body: 'Open RelateAI to review the event checklist before preparing any message.',
      triggerAt: triggerAt.toISOString()
    },
    adjusted: adjustedTrigger.adjustments.length > 0,
    skipped: false
  };
};

export const buildReminderPlanningResult = (
  state: AppState,
  daysBeforeEvent = [7, 1, 0],
  now = new Date()
): ReminderPlanningResult => {
  const policy = buildSchedulingPolicySummary(state);
  if (!policy.canScheduleNotifications) {
    return {
      plans: [],
      issues: policy.issues,
      adjustedCount: 0,
      skippedCount: state.events.length * daysBeforeEvent.length
    };
  }

  const issues = [...policy.issues];
  const planned = state.events.flatMap(event =>
    daysBeforeEvent.map(days => buildPlan(state, event, days, now, issues))
  );
  const plans = planned
    .flatMap(result => (result.plan ? [result.plan] : []))
    .sort((a, b) => new Date(a.triggerAt).getTime() - new Date(b.triggerAt).getTime());

  return {
    plans,
    issues,
    adjustedCount: planned.filter(result => result.adjusted).length,
    skippedCount: planned.filter(result => result.skipped).length
  };
};

export const buildReminderPlans = (state: AppState, daysBeforeEvent = [7, 1, 0]): ReminderPlan[] =>
  buildReminderPlanningResult(state, daysBeforeEvent).plans;
