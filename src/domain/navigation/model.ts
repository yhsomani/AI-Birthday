import type { NativeRouteId } from '../shared/brand';

export type NativeRouteProjection =
  | Readonly<{ kind: 'none' }>
  | Readonly<{
      kind: 'automation-review';
      routeId: NativeRouteId;
      source: 'birthday-reminder';
    }>
  | Readonly<{
      kind: 'attention';
      routeId: NativeRouteId;
      source: 'attention';
    }>;

export type NativeRouteAvailable = Readonly<{ kind: 'available' }>;
