export type ProviderEndpointReadinessStatus = 'Missing' | 'Ready' | 'Development only' | 'Blocked';

export type ProviderEndpointReadinessIssue =
  | 'missing'
  | 'invalid-url'
  | 'unsupported-protocol'
  | 'insecure-protocol'
  | 'local-development'
  | 'credentials-in-url'
  | 'private-network';

export type ProviderEndpointReadiness = {
  configured: boolean;
  canUseProviderEndpoint: boolean;
  productionReady: boolean;
  status: ProviderEndpointReadinessStatus;
  issue?: ProviderEndpointReadinessIssue;
  summary: string;
};

export type ProviderEndpointReadinessOptions = {
  allowLocalDevelopment?: boolean;
};

const missingEndpointReadiness: ProviderEndpointReadiness = {
  configured: false,
  canUseProviderEndpoint: false,
  productionReady: false,
  status: 'Missing',
  issue: 'missing',
  summary: 'No endpoint configured.'
};

const blocked = (issue: ProviderEndpointReadinessIssue, summary: string): ProviderEndpointReadiness => ({
  configured: true,
  canUseProviderEndpoint: false,
  productionReady: false,
  status: 'Blocked',
  issue,
  summary
});

const localDevelopment = (): ProviderEndpointReadiness => ({
  configured: true,
  canUseProviderEndpoint: true,
  productionReady: false,
  status: 'Development only',
  issue: 'local-development',
  summary: 'Local development endpoint is allowed but is not release-ready.'
});

const productionReady = (): ProviderEndpointReadiness => ({
  configured: true,
  canUseProviderEndpoint: true,
  productionReady: true,
  status: 'Ready',
  summary: 'HTTPS endpoint without embedded credentials is configured.'
});

const isNonPublicIpv4 = (host: string) => {
  const octets = host.split('.').map(Number);
  if (octets.length !== 4 || octets.some(octet => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
    return false;
  }

  const [first, second] = octets;
  return (
    first === 0 ||
    first === 10 ||
    first === 127 ||
    (first === 100 && second >= 64 && second <= 127) ||
    (first === 169 && second === 254) ||
    (first === 172 && second >= 16 && second <= 31) ||
    (first === 192 && (second === 0 || second === 168)) ||
    (first === 198 && (second === 18 || second === 19)) ||
    first >= 224
  );
};

const isNonPublicIpv6 = (host: string) => {
  const normalized = host.toLowerCase();
  if (!normalized.includes(':')) {
    return false;
  }

  return (
    normalized === '::' ||
    normalized === '::1' ||
    normalized.startsWith('fc') ||
    normalized.startsWith('fd') ||
    /^fe[89ab]/.test(normalized) ||
    normalized.startsWith('ff') ||
    normalized.startsWith('2001:db8:') ||
    normalized.startsWith('::ffff:127.') ||
    normalized.startsWith('::ffff:10.') ||
    normalized.startsWith('::ffff:169.254.') ||
    normalized.startsWith('::ffff:192.168.') ||
    /^::ffff:172\.(1[6-9]|2\d|3[01])\./.test(normalized)
  );
};

export const hostLooksLocalOrPrivate = (host: string) => {
  const normalized = host.replace(/^\[|\]$/g, '').toLowerCase();
  if (
    normalized === 'localhost' ||
    normalized.endsWith('.localhost') ||
    normalized.endsWith('.local') ||
    normalized.endsWith('.internal')
  ) {
    return true;
  }
  return isNonPublicIpv4(normalized) || isNonPublicIpv6(normalized);
};

export const evaluateProviderEndpointReadiness = (
  endpoint: string | undefined,
  options: ProviderEndpointReadinessOptions = {}
): ProviderEndpointReadiness => {
  const trimmed = endpoint?.trim();
  if (!trimmed) {
    return missingEndpointReadiness;
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return blocked('invalid-url', 'Endpoint value is not a valid absolute URL.');
  }

  if (parsed.username || parsed.password) {
    return blocked('credentials-in-url', 'Endpoint must not include embedded credentials.');
  }

  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return blocked('unsupported-protocol', 'Endpoint must use HTTPS.');
  }

  const localOrPrivate = hostLooksLocalOrPrivate(parsed.hostname);
  if (localOrPrivate && options.allowLocalDevelopment) {
    return localDevelopment();
  }
  if (localOrPrivate) {
    return blocked(
      'private-network',
      'Local, link-local, reserved, and private-network endpoints must not be used for release.'
    );
  }

  if (parsed.protocol !== 'https:') {
    return blocked('insecure-protocol', 'Endpoint must use HTTPS outside approved local development.');
  }

  return productionReady();
};

export const providerEndpointReadinessFromConfigured = (configured: boolean | undefined): ProviderEndpointReadiness =>
  configured ? productionReady() : missingEndpointReadiness;

export const providerEndpointReadinessStatusLabel = (readiness: ProviderEndpointReadiness) => readiness.status;
