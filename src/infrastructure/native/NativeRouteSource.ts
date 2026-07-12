export interface NativeRouteSource {
  subscribe(listener: (rawEvent: unknown) => void): () => void;
}

export const inactiveNativeRouteSource: NativeRouteSource = {
  subscribe: () => () => undefined,
};
