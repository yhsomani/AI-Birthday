export interface NativeInvalidationSource {
  subscribe(listener: (rawEvent: unknown) => void): () => void;
}

export const inactiveInvalidationSource: NativeInvalidationSource = {
  subscribe: () => () => undefined,
};
