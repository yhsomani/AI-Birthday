import { initialState } from '../data/seed';
import type { AppState } from '../domain/types';

/** Rich deterministic fixture for tests. Production initialization must never import this module. */
export const createTestState = (): AppState => structuredClone(initialState);
