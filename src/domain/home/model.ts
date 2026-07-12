import type {
  AutomationProjection,
  UpcomingGreeting,
} from '../automation/model';
import type { SyncProjection } from '../contacts/model';
import type { UtcInstant } from '../shared/temporal';

export type HomeProjection = Readonly<{
  automation: AutomationProjection;
  next?: UpcomingGreeting | undefined;
  counts: Readonly<{
    enabled: number;
    needsAttention: number;
    unavailable: number;
    today: number;
    nextSevenDays: number;
  }>;
  contactsSync: SyncProjection;
  schedulerHeartbeatAt?: UtcInstant | undefined;
  lastCoordinationSuccessAt?: UtcInstant | undefined;
}>;
