import type {
  NativeRouteAvailable,
  NativeRouteProjection,
} from '../../domain/navigation/model';
import type { NativeResult } from '../../domain/shared/result';

export interface AppRoutePort {
  getPendingRoute(): Promise<NativeResult<NativeRouteProjection>>;
  subscribeRouteAvailable(
    listener: (event: NativeRouteAvailable) => void,
  ): () => void;
}
