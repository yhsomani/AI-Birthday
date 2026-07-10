import { MAX_EVENT_IMPORT_BYTES } from '../domain/eventImport';

export type EventImportFilePickResult = {
  name: string;
  uri: string;
  raw: string;
  temporaryFileRemoved: boolean;
};

export interface EventImportDocumentAsset {
  name: string;
  uri: string;
  size?: number;
  mimeType?: string;
}

export interface EventImportDocumentPicker {
  getDocumentAsync(options: {
    type: string[];
    copyToCacheDirectory: true;
    multiple: false;
    base64: false;
  }): Promise<{ canceled: boolean; assets?: EventImportDocumentAsset[] | null }>;
}

export interface EventImportFileSystem {
  cacheDirectory: string | null;
  utf8Encoding: 'utf8';
  readAsStringAsync(uri: string, options: { encoding: 'utf8' }): Promise<string>;
  getInfoAsync(uri: string): Promise<{ exists: boolean; isDirectory?: boolean; size?: number }>;
  deleteAsync(uri: string, options: { idempotent: true }): Promise<void>;
}

export interface EventImportFileService {
  pickEventImportFile(): Promise<EventImportFilePickResult | undefined>;
}

const supportedExtensions = ['.csv', '.vcf', '.vcard'];
const supportedMimeTypes = new Set([
  'text/csv',
  'text/vcard',
  'text/x-vcard',
  'text/directory',
  'text/plain',
  'application/vcard'
]);
const maximumFilenameLength = 255;
const maximumUriLength = 4096;

const hasTraversalSegment = (uri: string) => {
  try {
    return decodeURIComponent(uri)
      .split('/')
      .some(segment => segment === '..');
  } catch {
    return true;
  }
};

const isOwnedCacheUri = (uri: string, cacheDirectory: string | null) =>
  Boolean(cacheDirectory) &&
  uri.length <= maximumUriLength &&
  !hasTraversalSegment(uri) &&
  uri.startsWith(cacheDirectory as string) &&
  uri.length > (cacheDirectory as string).length;

const supportedExtension = (name: string) =>
  supportedExtensions.some(extension => name.toLowerCase().endsWith(extension));

const assertSupportedAsset = async (asset: EventImportDocumentAsset, fileSystem: EventImportFileSystem) => {
  if (
    !asset.name ||
    asset.name.length > maximumFilenameLength ||
    asset.name.includes('/') ||
    asset.name.includes('\\') ||
    !supportedExtension(asset.name)
  ) {
    throw new Error('Choose a .csv, .vcf, or .vcard event import file.');
  }
  if (asset.mimeType) {
    const mimeType = asset.mimeType.split(';', 1)[0].trim().toLowerCase();
    if (mimeType !== 'application/octet-stream' && !supportedMimeTypes.has(mimeType)) {
      throw new Error('The selected file type is not a supported event import.');
    }
  }
  if (!asset.uri || asset.uri.length > maximumUriLength || hasTraversalSegment(asset.uri)) {
    throw new Error('The selected event import location is invalid.');
  }

  let size = asset.size;
  if (size === undefined) {
    const info = await fileSystem.getInfoAsync(asset.uri);
    if (info.exists && info.isDirectory !== true) size = info.size;
  }
  if (!Number.isSafeInteger(size) || (size as number) < 1) {
    throw new Error('The selected event import size could not be verified safely.');
  }
  if ((size as number) > MAX_EVENT_IMPORT_BYTES) {
    throw new Error(`Event import must be no larger than ${MAX_EVENT_IMPORT_BYTES} bytes.`);
  }
};

export const createEventImportFileService = (
  documentPicker: EventImportDocumentPicker,
  fileSystem: EventImportFileSystem
): EventImportFileService => ({
  async pickEventImportFile() {
    const result = await documentPicker.getDocumentAsync({
      type: [...supportedMimeTypes],
      copyToCacheDirectory: true,
      multiple: false,
      base64: false
    });
    if (result.canceled) return undefined;
    const asset = result.assets?.[0];
    if (!asset) return undefined;

    let temporaryFileRemoved = false;
    try {
      await assertSupportedAsset(asset, fileSystem);
      const raw = await fileSystem.readAsStringAsync(asset.uri, { encoding: fileSystem.utf8Encoding });
      if (new TextEncoder().encode(raw).byteLength > MAX_EVENT_IMPORT_BYTES) {
        throw new Error(`Event import must be no larger than ${MAX_EVENT_IMPORT_BYTES} bytes.`);
      }
      if (raw.includes('\uFFFD')) {
        throw new Error('Event import must use valid UTF-8 text.');
      }
      if (isOwnedCacheUri(asset.uri, fileSystem.cacheDirectory)) {
        await fileSystem.deleteAsync(asset.uri, { idempotent: true });
        temporaryFileRemoved = true;
      }
      return { name: asset.name, uri: asset.uri, raw: raw.replace(/^\uFEFF/, ''), temporaryFileRemoved };
    } finally {
      if (!temporaryFileRemoved && isOwnedCacheUri(asset.uri, fileSystem.cacheDirectory)) {
        await fileSystem.deleteAsync(asset.uri, { idempotent: true });
      }
    }
  }
});

let defaultServicePromise: Promise<EventImportFileService> | undefined;

const loadDefaultService = () => {
  if (!defaultServicePromise) {
    defaultServicePromise = Promise.all([import('expo-document-picker'), import('expo-file-system/legacy')]).then(
      ([documentPicker, fileSystem]) =>
        createEventImportFileService(documentPicker, {
          cacheDirectory: fileSystem.cacheDirectory,
          utf8Encoding: fileSystem.EncodingType.UTF8,
          readAsStringAsync: fileSystem.readAsStringAsync,
          getInfoAsync: fileSystem.getInfoAsync,
          deleteAsync: fileSystem.deleteAsync
        })
    );
  }
  return defaultServicePromise;
};

export const pickEventImportFile = async (): Promise<EventImportFilePickResult | undefined> =>
  (await loadDefaultService()).pickEventImportFile();
