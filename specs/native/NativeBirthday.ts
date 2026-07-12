import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type RawNativeResponse = Readonly<{
  contractVersion: number;
  revision: string;
  generatedAt: string;
  kind: string;
  payloadJson: string;
}>;

export interface Spec extends TurboModule {
  getProjection(area: string, requestJson: string): Promise<RawNativeResponse>;
  executeUserIntent(
    intent: string,
    expectedRevision: string | null,
    payloadJson: string,
  ): Promise<RawNativeResponse>;
  addListener(eventType: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.get<Spec>('BirthdayNative');
