import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type {
  ProjectionArea,
  ProjectionInvalidation,
} from '../../application/ports/AppProjectionPort';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import { nativeBridgeProblem } from './nativeProblem';

export type LiveProjectionState<Value> =
  | Readonly<{ kind: 'loading' }>
  | Readonly<{
      kind: 'ready';
      result: Extract<NativeResult<Value>, { kind: 'ok' }>;
      refreshing: boolean;
      refreshProblem?: NativeProblem | undefined;
    }>
  | Readonly<{ kind: 'error'; problem: NativeProblem }>;

type InvalidationPort = Readonly<{
  subscribeInvalidations(
    listener: (event: ProjectionInvalidation) => void,
  ): () => void;
}>;

export function useLiveProjection<Value>(
  loadProjection: () => Promise<NativeResult<Value>>,
  invalidationPort: InvalidationPort,
  areas: readonly ProjectionArea[],
) {
  const [state, setState] = useState<LiveProjectionState<Value>>({
    kind: 'loading',
  });
  const requestSequence = useRef(0);
  const mounted = useRef(true);
  const areasRef = useRef(areas);
  areasRef.current = areas;

  const load = useCallback(
    async (preserveVerifiedValue = true): Promise<NativeResult<Value>> => {
      const request = requestSequence.current + 1;
      requestSequence.current = request;
      setState(current =>
        preserveVerifiedValue && current.kind === 'ready'
          ? { ...current, refreshing: true, refreshProblem: undefined }
          : { kind: 'loading' },
      );

      let result: NativeResult<Value>;
      try {
        result = await loadProjection();
      } catch {
        result = { kind: 'error', problem: nativeBridgeProblem };
      }

      if (!mounted.current || request !== requestSequence.current) {
        return result;
      }

      setState(current => {
        if (result.kind === 'ok') {
          return { kind: 'ready', result, refreshing: false };
        }
        if (preserveVerifiedValue && current.kind === 'ready') {
          return {
            ...current,
            refreshing: false,
            refreshProblem: result.problem,
          };
        }
        return { kind: 'error', problem: result.problem };
      });
      return result;
    },
    [loadProjection],
  );

  useEffect(() => {
    mounted.current = true;
    load(false).catch(() => undefined);
    return () => {
      mounted.current = false;
      requestSequence.current += 1;
    };
  }, [load]);

  useEffect(
    () =>
      invalidationPort.subscribeInvalidations(event => {
        const shouldReload = event.areas.some(area =>
          areasRef.current.includes(area),
        );
        if (shouldReload) {
          load(true).catch(() => undefined);
        }
      }),
    [invalidationPort, load],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        load(true).catch(() => undefined);
      }
    });
    return () => subscription.remove();
  }, [load]);

  return {
    state,
    reload: () => load(true),
  } as const;
}
