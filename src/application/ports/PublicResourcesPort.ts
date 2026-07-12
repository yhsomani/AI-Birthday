import type { PublicResourcesProjection } from '../../domain/legal/model';
import type { NativeResult } from '../../domain/shared/result';

export interface PublicResourcesPort {
  getPublicResources(): Promise<NativeResult<PublicResourcesProjection>>;
}
