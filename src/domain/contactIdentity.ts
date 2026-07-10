import type { Contact, ContactRoute, ContactSourceIdentity, ImportedContactRecord } from './types';

export const normalizePhoneRoute = (phone: string | undefined) => {
  const trimmed = phone?.trim();
  if (!trimmed) return undefined;
  const international = trimmed.startsWith('+') || trimmed.startsWith('00');
  const digits = (trimmed.startsWith('00') ? trimmed.slice(2) : trimmed).replace(/\D/g, '');
  return digits.length >= 7 && digits.length <= 15 ? `${international ? '+' : ''}${digits}` : undefined;
};

export const normalizeEmailRoute = (email: string | undefined) => {
  const normalized = email?.trim().toLowerCase();
  return normalized && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized) ? normalized : undefined;
};

const fingerprint = (value: string) => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
};

const createRoute = (type: ContactRoute['type'], value: string, primary: boolean): ContactRoute => ({
  id: `route-${type.toLowerCase()}-${fingerprint(value)}`,
  type,
  value,
  primary,
  verified: false
});

export const importedContactRoutes = (record: ImportedContactRecord): ContactRoute[] => {
  const phones = [...(record.phones ?? []), ...(record.phone ? [record.phone] : [])]
    .map(normalizePhoneRoute)
    .filter((value): value is string => Boolean(value));
  const emails = [...(record.emails ?? []), ...(record.email ? [record.email] : [])]
    .map(normalizeEmailRoute)
    .filter((value): value is string => Boolean(value));
  return [
    ...[...new Set(phones)].map((value, index) => createRoute('Phone', value, index === 0)),
    ...[...new Set(emails)].map((value, index) => createRoute('Email', value, index === 0))
  ];
};

export const allContactRoutes = (contact: Contact): ContactRoute[] => {
  const phone = normalizePhoneRoute(contact.phone);
  const email = normalizeEmailRoute(contact.email);
  const candidates = [
    ...(phone ? [createRoute('Phone', phone, true)] : []),
    ...(email ? [createRoute('Email', email, true)] : []),
    ...(contact.routes ?? [])
  ];
  const byIdentity = new Map<string, ContactRoute>();
  for (const candidate of candidates) {
    const value =
      candidate.type === 'Phone' ? normalizePhoneRoute(candidate.value) : normalizeEmailRoute(candidate.value);
    if (!value) continue;
    const key = `${candidate.type}:${value}`;
    const existing = byIdentity.get(key);
    byIdentity.set(key, {
      ...candidate,
      id: createRoute(candidate.type, value, candidate.primary).id,
      value,
      primary: candidate.primary || existing?.primary === true,
      verified: candidate.verified || existing?.verified === true
    });
  }
  return [...byIdentity.values()];
};

export const importedSourceIdentity = (record: ImportedContactRecord): ContactSourceIdentity => ({
  provider: 'Device contacts',
  sourceId: record.sourceId.trim()
});

export const contactMatchesImportedSource = (contact: Contact, record: ImportedContactRecord) =>
  (contact.sourceIdentities ?? []).some(
    identity => identity.provider === 'Device contacts' && identity.sourceId === record.sourceId.trim()
  );

export const contactMatchesImportedRoute = (contact: Contact, record: ImportedContactRecord) => {
  const contactKeys = new Set(allContactRoutes(contact).map(item => `${item.type}:${item.value}`));
  return importedContactRoutes(record).some(item => contactKeys.has(`${item.type}:${item.value}`));
};

export const mergeImportedIdentity = (contact: Contact, record: ImportedContactRecord): Contact => {
  const routes = allContactRoutes({
    ...contact,
    routes: [...allContactRoutes(contact), ...importedContactRoutes(record)]
  });
  const identity = importedSourceIdentity(record);
  const sourceIdentities = [...(contact.sourceIdentities ?? [])];
  if (!sourceIdentities.some(item => item.provider === identity.provider && item.sourceId === identity.sourceId)) {
    sourceIdentities.push(identity);
  }
  return {
    ...contact,
    phone: contact.phone ?? routes.find(item => item.type === 'Phone' && item.primary)?.value,
    email: contact.email ?? routes.find(item => item.type === 'Email' && item.primary)?.value,
    routes,
    sourceIdentities
  };
};

export const normalizedContactName = (name: string) => name.trim().toLocaleLowerCase('en-IN').replace(/\s+/g, ' ');
