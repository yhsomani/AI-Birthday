declare const __DEV__: boolean;

const compileTimeDevelopmentBuild = () => typeof __DEV__ !== 'undefined' && __DEV__ === true;

/** A public environment flag can narrow development behavior, never enable it in a release build. */
export const buildAllowsLocalProviderEndpoints = (
  publicFlag: string | undefined,
  developmentBuild = compileTimeDevelopmentBuild()
): boolean => developmentBuild && publicFlag?.trim().toLowerCase() === 'true';
