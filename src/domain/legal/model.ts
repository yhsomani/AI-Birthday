export const PUBLIC_RESOURCE_KINDS = [
  'privacy',
  'terms',
  'support',
  'delete-account',
] as const;

export type PublicResourceKind = (typeof PUBLIC_RESOURCE_KINDS)[number];

export type PublicResourcesProjection =
  | Readonly<{
      kind: 'available';
      buildLabel: string;
      baseUrl: string;
    }>
  | Readonly<{
      kind: 'unavailable';
      buildLabel: string;
    }>;
