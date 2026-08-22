import './styles.css';

import { initializeApp, type FirebaseOptions } from 'firebase/app';
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
} from 'firebase/app-check';
import {
  getAuth,
  GoogleAuthProvider,
  inMemoryPersistence,
  reauthenticateWithPopup,
  setPersistence,
  signInWithPopup,
  signOut,
  type Auth,
  type User,
} from 'firebase/auth';
import { getFunctions, httpsCallable } from 'firebase/functions';

import {
  deletionStartProjection,
  isDeletionReceiptId,
  receiptProjection,
} from './deletion-contract';

type Language = 'en' | 'hi';

interface RuntimeConfig {
  readonly schemaVersion: 1;
  readonly publicBaseUrl: string;
  readonly developerDisplayName: string;
  readonly supportUrl: string;
  readonly recaptchaEnterpriseSiteKey: string;
  readonly privacyEffectiveDate: string;
  readonly termsEffectiveDate: string;
  readonly functionsRegion: 'asia-south1';
}

interface FirebaseServices {
  readonly auth: Auth;
  readonly deleteAccount: (request: {
    readonly contractVersion: 1;
    readonly requestId: string;
  }) => Promise<{ readonly data: unknown }>;
  readonly readDeletionReceipt: (request: {
    readonly contractVersion: 1;
    readonly receiptId: string;
  }) => Promise<{ readonly data: unknown }>;
}

const messages = {
  en: {
    languageButton: 'हिन्दी',
    languageLabel: 'Read this page in Hindi',
    configUnavailable:
      'This release is not configured for account deletion. Do not send identity documents or account details through an unverified channel.',
    loading: 'Preparing the secure deletion service…',
    ready:
      'Secure sign-in is ready. Your Google password is never shared with WishWell.',
    signingIn: 'Opening Google sign-in…',
    signedIn: 'Signed in as',
    wrongProvider:
      'This account is not linked exactly once through Google. Use the Google account connected to WishWell.',
    popupClosed: 'Google sign-in was cancelled. No deletion request was sent.',
    signInFailed:
      'Google sign-in could not be confirmed. No deletion request was sent. Please try again.',
    reauthenticating:
      'Confirm your Google account to authorize permanent deletion…',
    submitting:
      'Starting the deletion safety fence. Keep this page open until a request receipt appears.',
    requestAccepted:
      'Deletion requested. New Android automation permits are blocked. A previously issued permit may still finish before its short frozen deadline.',
    requestAcceptedSignedOut:
      'Deletion requested, and sign-out from this tab was verified. Keep the private receipt until exact completion is shown.',
    receiptMeaning:
      'This receipt confirms that the server accepted the request; it is not a claim that deletion has already finished. Save the reference until completion.',
    operationInProgress:
      'Another protected account operation is still running. No deletion request was accepted. Wait for that operation to finish, then retry here.',
    requestMismatch:
      'Account deletion is already running under its original private receipt. This new reference was not accepted. Use the original saved receipt or verified support; do not treat this as completion.',
    recentAuthRequired:
      'Fresh Google confirmation was not accepted. No deletion request was confirmed. Sign in again and retry.',
    unavailable:
      'The server response was unavailable, so the result is unknown. Do not assume deletion finished. This tab keeps the same private receipt for a safe retry after reload.',
    invalidResponse:
      'The server returned an unrecognized response. Do not assume deletion started or finished. Retry later or use verified support.',
    copied: 'Reference copied.',
    copyFailed: 'Could not copy. Select and save the reference manually.',
    signedOut: 'Signed out. No deletion request was sent.',
    signedOutPending:
      'Signed out. The pending private receipt remains in this tab because the request result is not yet verified.',
    signOutFailed:
      'Sign-out could not be verified. This browser may still be signed in as the account shown. No new deletion request was sent. Retry sign-out.',
    postAcceptanceSignOutFailed:
      'Deletion was accepted, but sign-out could not be verified. The account shown may remain signed in in this tab. Keep the receipt and retry sign-out; this does not change deletion status.',
    receiptInvalid:
      'Enter the exact lowercase version-4 deletion receipt UUID.',
    receiptRestored:
      'Recovered a pending private receipt from this tab session. Check its status; recovery does not itself prove acceptance or completion.',
    receiptStorageUnavailable:
      'This tab cannot safely keep a private receipt across reload. No deletion request was sent. Allow session storage for this site and retry, or use the mobile app.',
    acceptedReceiptStorageFailed:
      'This tab could not verify recovery storage after acceptance. Copy the visible private receipt now; reloading may lose the browser copy.',
    receiptCleared:
      'The private receipt was removed from this tab session. This does not cancel a deletion request.',
    receiptClearFailed:
      'The browser could not verify removal of the private receipt from this tab. Keep this tab private and retry Clear or close the tab session.',
    receiptPrompt: 'Enter a receipt to check its status.',
    receiptChecking: 'Checking the content-free deletion receipt…',
    receiptSignOutRequired:
      'Sign out from this tab before checking a private deletion receipt. No receipt request was sent.',
    receiptInProgress:
      'Deletion is still in progress. New Android permits remain blocked. Check again later.',
    receiptCompleted:
      'Deletion completed: the Firebase app account and app-operated server data associated with this deletion request were removed. External message, contact, carrier, recipient, and backup copies were not removed.',
    receiptNotFound:
      'No live receipt matched this reference. This is not proof that deletion failed, never started, or completed. Check the reference or use verified support.',
  },
  hi: {
    languageButton: 'English',
    languageLabel: 'Read this page in English',
    configUnavailable:
      'इस रिलीज़ में खाता हटाने की सेवा कॉन्फ़िगर नहीं है। किसी असत्यापित माध्यम से पहचान दस्तावेज़ या खाते की जानकारी न भेजें।',
    loading: 'सुरक्षित खाता हटाने की सेवा तैयार हो रही है…',
    ready:
      'सुरक्षित साइन-इन तैयार है। आपका Google पासवर्ड WishWell के साथ साझा नहीं होता।',
    signingIn: 'Google साइन-इन खोला जा रहा है…',
    signedIn: 'साइन-इन किया गया खाता',
    wrongProvider:
      'यह खाता Google के माध्यम से ठीक एक बार लिंक नहीं है। WishWell से जुड़ा Google खाता इस्तेमाल करें।',
    popupClosed:
      'Google साइन-इन रद्द हुआ। खाता हटाने का कोई अनुरोध नहीं भेजा गया।',
    signInFailed:
      'Google साइन-इन की पुष्टि नहीं हो सकी। कोई अनुरोध नहीं भेजा गया। फिर से कोशिश करें।',
    reauthenticating:
      'स्थायी रूप से खाता हटाने के लिए अपने Google खाते की पुष्टि करें…',
    submitting:
      'खाता हटाने की सुरक्षा प्रक्रिया शुरू हो रही है। अनुरोध रसीद दिखने तक यह पेज खुला रखें।',
    requestAccepted:
      'खाता हटाने का अनुरोध मिल गया। नए Android ऑटोमेशन परमिट रोक दिए गए हैं। पहले से जारी छोटा परमिट अपनी तय समय-सीमा तक पूरा हो सकता है।',
    requestAcceptedSignedOut:
      'खाता हटाने का अनुरोध मिल गया और इस टैब से साइन-आउट की पुष्टि हो गई। सही completion दिखने तक निजी रसीद संभालकर रखें।',
    receiptMeaning:
      'यह रसीद केवल अनुरोध स्वीकार होने की पुष्टि करती है; इसका अर्थ यह नहीं कि हटाने की प्रक्रिया पूरी हो चुकी है। पूरा होने तक संदर्भ संख्या संभालकर रखें।',
    operationInProgress:
      'खाते पर दूसरी सुरक्षित प्रक्रिया चल रही है। हटाने का अनुरोध स्वीकार नहीं हुआ। उसके पूरा होने के बाद फिर कोशिश करें।',
    requestMismatch:
      'खाता हटाने की प्रक्रिया उसकी मूल निजी रसीद के साथ पहले से चल रही है। यह नया संदर्भ स्वीकार नहीं हुआ। मूल सुरक्षित रसीद या सत्यापित सहायता इस्तेमाल करें; इसे प्रक्रिया पूरी होने का प्रमाण न मानें।',
    recentAuthRequired:
      'नई Google पुष्टि स्वीकार नहीं हुई। हटाने के अनुरोध की पुष्टि नहीं हुई। फिर से साइन-इन करके कोशिश करें।',
    unavailable:
      'सर्वर का उत्तर नहीं मिला, इसलिए परिणाम अज्ञात है। यह न मानें कि खाता हट गया है। यह टैब reload के बाद सुरक्षित retry के लिए वही निजी रसीद रखता है।',
    invalidResponse:
      'सर्वर से अनजान उत्तर मिला। यह न मानें कि प्रक्रिया शुरू या पूरी हो गई है। बाद में कोशिश करें या सत्यापित सहायता लें।',
    copied: 'संदर्भ संख्या कॉपी हो गई।',
    copyFailed: 'कॉपी नहीं हो सकी। संदर्भ संख्या चुनकर स्वयं सुरक्षित करें।',
    signedOut: 'साइन-आउट हो गया। हटाने का कोई अनुरोध नहीं भेजा गया।',
    signedOutPending:
      'साइन-आउट हो गया। अनुरोध का परिणाम अभी सत्यापित नहीं है, इसलिए pending निजी रसीद इस टैब में रखी गई है।',
    signOutFailed:
      'साइन-आउट की पुष्टि नहीं हो सकी। इस browser में दिखाया गया खाता अभी भी साइन-इन हो सकता है। कोई नया deletion अनुरोध नहीं भेजा गया। फिर साइन-आउट करें।',
    postAcceptanceSignOutFailed:
      'Deletion अनुरोध स्वीकार हुआ, लेकिन साइन-आउट की पुष्टि नहीं हो सकी। दिखाया गया खाता इस टैब में साइन-इन रह सकता है। रसीद रखें और फिर साइन-आउट करें; इससे deletion status नहीं बदलता।',
    receiptInvalid:
      'खाता हटाने की सही lowercase version-4 UUID रसीद दर्ज करें।',
    receiptRestored:
      'इस टैब session से pending निजी रसीद वापस मिली। इसकी स्थिति जाँचें; recovery अपने-आप acceptance या completion का प्रमाण नहीं है।',
    receiptStorageUnavailable:
      'यह टैब reload के लिए निजी रसीद सुरक्षित नहीं रख सकता। कोई deletion अनुरोध नहीं भेजा गया। इस site के लिए session storage की अनुमति देकर फिर कोशिश करें या mobile app इस्तेमाल करें।',
    acceptedReceiptStorageFailed:
      'Acceptance के बाद यह टैब recovery storage की पुष्टि नहीं कर सका। दिख रही निजी रसीद अभी copy करें; reload पर browser copy खो सकती है।',
    receiptCleared:
      'निजी रसीद इस टैब session से हटा दी गई। इससे deletion अनुरोध रद्द नहीं होता।',
    receiptClearFailed:
      'Browser इस टैब से निजी रसीद हटने की पुष्टि नहीं कर सका। इस टैब को private रखें और Clear फिर करें या tab session बंद करें।',
    receiptPrompt: 'स्थिति जाँचने के लिए रसीद दर्ज करें।',
    receiptChecking: 'बिना सामग्री वाली deletion receipt की जाँच हो रही है…',
    receiptSignOutRequired:
      'निजी deletion receipt जाँचने से पहले इस टैब से साइन-आउट करें। कोई receipt अनुरोध नहीं भेजा गया।',
    receiptInProgress:
      'खाता हटाने की प्रक्रिया अभी चल रही है। नए Android परमिट बंद हैं। बाद में फिर जाँचें।',
    receiptCompleted:
      'Deletion पूरा हुआ: इस deletion अनुरोध से जुड़ा Firebase ऐप खाता और ऐप-संचालित सर्वर डेटा हटा दिया गया। बाहरी संदेश, संपर्क, मोबाइल नेटवर्क, प्राप्तकर्ता या बैकअप प्रतियाँ नहीं हटाई गईं।',
    receiptNotFound:
      'इस संदर्भ से कोई सक्रिय रसीद नहीं मिली। यह इस बात का प्रमाण नहीं है कि प्रक्रिया विफल हुई, शुरू नहीं हुई या पूरी हो गई। संदर्भ जाँचें या सत्यापित सहायता लें।',
  },
} as const;

let language: Language = preferredLanguage();

function element<T extends HTMLElement>(selector: string): T | null {
  return document.querySelector<T>(selector);
}

function requiredElement<T extends HTMLElement>(selector: string): T {
  const value = element<T>(selector);
  if (value === null) {
    throw new Error('Required page element is missing');
  }
  return value;
}

function preferredLanguage(): Language {
  const requested = new URLSearchParams(window.location.search).get('lang');
  if (requested === 'hi' || requested === 'en') {
    return requested;
  }
  return navigator.languages.some(value => value.toLowerCase().startsWith('hi'))
    ? 'hi'
    : 'en';
}

function applyLanguage(next: Language): void {
  language = next;
  document.documentElement.lang = next;
  for (const node of document.querySelectorAll<HTMLElement>(
    '[data-en][data-hi]',
  )) {
    node.textContent = node.dataset[next] ?? '';
  }
  for (const node of document.querySelectorAll<HTMLElement>(
    '[data-aria-en][data-aria-hi]',
  )) {
    node.setAttribute(
      'aria-label',
      node.dataset[`aria${next === 'en' ? 'En' : 'Hi'}`] ?? '',
    );
  }
  const toggle = element<HTMLButtonElement>('#language-toggle');
  if (toggle !== null) {
    toggle.textContent = messages[next].languageButton;
    toggle.setAttribute('aria-label', messages[next].languageLabel);
  }
  const url = new URL(window.location.href);
  url.searchParams.set('lang', next);
  window.history.replaceState(null, '', url);
}

function setupLanguage(): void {
  element<HTMLButtonElement>('#language-toggle')?.addEventListener(
    'click',
    () => {
      applyLanguage(language === 'en' ? 'hi' : 'en');
    },
  );
  applyLanguage(language);
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function publicHttpsUrl(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  try {
    const url = new URL(value);
    return url.protocol === 'https:' &&
      url.username === '' &&
      url.password === ''
      ? url.toString()
      : null;
  } catch {
    return null;
  }
}

function parseRuntimeConfig(input: unknown): RuntimeConfig | null {
  if (!isRecord(input)) {
    return null;
  }
  const keys = [
    'developerDisplayName',
    'functionsRegion',
    'privacyEffectiveDate',
    'publicBaseUrl',
    'recaptchaEnterpriseSiteKey',
    'schemaVersion',
    'supportUrl',
    'termsEffectiveDate',
  ];
  if (Object.keys(input).sort().join('|') !== keys.sort().join('|')) {
    return null;
  }
  const publicBaseUrl = publicHttpsUrl(input.publicBaseUrl);
  const supportUrl = publicHttpsUrl(input.supportUrl);
  if (
    input.schemaVersion !== 1 ||
    input.functionsRegion !== 'asia-south1' ||
    typeof input.developerDisplayName !== 'string' ||
    input.developerDisplayName.trim().length === 0 ||
    typeof input.recaptchaEnterpriseSiteKey !== 'string' ||
    input.recaptchaEnterpriseSiteKey.length < 20 ||
    typeof input.privacyEffectiveDate !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/u.test(input.privacyEffectiveDate) ||
    typeof input.termsEffectiveDate !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/u.test(input.termsEffectiveDate) ||
    publicBaseUrl === null ||
    supportUrl === null
  ) {
    return null;
  }
  return {
    schemaVersion: 1,
    publicBaseUrl,
    developerDisplayName: input.developerDisplayName.trim(),
    supportUrl,
    recaptchaEnterpriseSiteKey: input.recaptchaEnterpriseSiteKey,
    privacyEffectiveDate: input.privacyEffectiveDate,
    termsEffectiveDate: input.termsEffectiveDate,
    functionsRegion: 'asia-south1',
  };
}

async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  const response = await fetch('/runtime-config.json', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error('Runtime configuration is unavailable');
  }
  const config = parseRuntimeConfig(await response.json());
  if (config === null) {
    throw new Error('Runtime configuration is invalid');
  }
  return config;
}

function bindRuntimeConfig(config: RuntimeConfig): void {
  for (const node of document.querySelectorAll<HTMLElement>(
    '[data-developer]',
  )) {
    node.textContent = config.developerDisplayName;
  }
  for (const node of document.querySelectorAll<HTMLElement>(
    '[data-privacy-date]',
  )) {
    node.textContent = config.privacyEffectiveDate;
  }
  for (const node of document.querySelectorAll<HTMLElement>(
    '[data-terms-date]',
  )) {
    node.textContent = config.termsEffectiveDate;
  }
  for (const link of document.querySelectorAll<HTMLAnchorElement>(
    '[data-support-link]',
  )) {
    link.href = config.supportUrl;
    link.rel = 'noreferrer noopener';
  }
  const canonical = element<HTMLLinkElement>('link[rel="canonical"]');
  if (canonical !== null) {
    canonical.href = new URL(
      window.location.pathname,
      config.publicBaseUrl,
    ).toString();
  }
}

function showConfigurationFailure(): void {
  const banner = element<HTMLElement>('[data-config-banner]');
  if (banner !== null) {
    banner.hidden = false;
  }
  for (const node of document.querySelectorAll<HTMLElement>(
    '[data-developer]',
  )) {
    node.textContent =
      language === 'hi'
        ? 'डेवलपर की सार्वजनिक पहचान कॉन्फ़िगर नहीं है'
        : 'Public developer identity is not configured';
  }
  for (const selector of [
    '#continue-google',
    '#delete-confirm',
    '#delete-account',
    '#sign-out',
    '#copy-reference',
    '#retry-sign-out',
    '#receipt-check-id',
    '#check-receipt',
    '#clear-receipt',
  ] as const) {
    const control = element<HTMLInputElement | HTMLButtonElement>(selector);
    if (control !== null) {
      control.disabled = true;
    }
  }
}

function firebaseOptions(input: unknown): FirebaseOptions | null {
  if (!isRecord(input)) {
    return null;
  }
  const required = ['apiKey', 'appId', 'authDomain', 'projectId'] as const;
  if (
    required.some(
      key => typeof input[key] !== 'string' || input[key].trim().length === 0,
    )
  ) {
    return null;
  }
  return {
    apiKey: input.apiKey as string,
    appId: input.appId as string,
    authDomain: input.authDomain as string,
    projectId: input.projectId as string,
    ...(typeof input.messagingSenderId === 'string'
      ? { messagingSenderId: input.messagingSenderId }
      : {}),
  };
}

async function initializeDeletionServices(
  config: RuntimeConfig,
): Promise<FirebaseServices> {
  const response = await fetch('/__/firebase/init.json', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error('Firebase Hosting configuration is unavailable');
  }
  const options = firebaseOptions(await response.json());
  if (options === null) {
    throw new Error('Firebase Hosting configuration is invalid');
  }
  const app = initializeApp(options);
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(
      config.recaptchaEnterpriseSiteKey,
    ),
    isTokenAutoRefreshEnabled: true,
  });
  const auth = getAuth(app);
  await setPersistence(auth, inMemoryPersistence);
  auth.useDeviceLanguage();
  const callable = httpsCallable<
    { readonly contractVersion: 1; readonly requestId: string },
    unknown
  >(getFunctions(app, config.functionsRegion), 'requestAccountDeletion', {
    limitedUseAppCheckTokens: true,
  });
  const receiptCallable = httpsCallable<
    { readonly contractVersion: 1; readonly receiptId: string },
    unknown
  >(getFunctions(app, config.functionsRegion), 'accountDeletionReceipt', {
    limitedUseAppCheckTokens: true,
  });
  return {
    auth,
    deleteAccount: callable,
    readDeletionReceipt: receiptCallable,
  };
}

function googleProvider(): GoogleAuthProvider {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  return provider;
}

function isExactGoogleUser(user: User): boolean {
  return (
    user.providerData.length === 1 &&
    user.providerData[0]?.providerId === GoogleAuthProvider.PROVIDER_ID
  );
}

function errorCode(error: unknown): string | null {
  if (!isRecord(error) || typeof error.code !== 'string') {
    return null;
  }
  return error.code;
}

function setStatus(
  text: string,
  tone: 'neutral' | 'success' | 'warning' = 'neutral',
): void {
  const status = requiredElement<HTMLElement>('#deletion-status');
  status.textContent = text;
  status.dataset.tone = tone;
}

const PENDING_RECEIPT_SESSION_KEY =
  'birthday-autopilot.pending-deletion-receipt.v1';

function restorePendingReceipt(): string | null {
  try {
    const value = window.sessionStorage.getItem(PENDING_RECEIPT_SESSION_KEY);
    if (value === null) {
      return null;
    }
    if (isDeletionReceiptId(value)) {
      return value;
    }
    window.sessionStorage.removeItem(PENDING_RECEIPT_SESSION_KEY);
    return null;
  } catch {
    return null;
  }
}

function persistPendingReceipt(receiptId: string): boolean {
  if (!isDeletionReceiptId(receiptId)) {
    return false;
  }
  try {
    window.sessionStorage.setItem(PENDING_RECEIPT_SESSION_KEY, receiptId);
    return (
      window.sessionStorage.getItem(PENDING_RECEIPT_SESSION_KEY) === receiptId
    );
  } catch {
    return false;
  }
}

function clearPendingReceipt(): boolean {
  try {
    window.sessionStorage.removeItem(PENDING_RECEIPT_SESSION_KEY);
    return window.sessionStorage.getItem(PENDING_RECEIPT_SESSION_KEY) === null;
  } catch {
    return false;
  }
}

async function setupDeletionPage(config: RuntimeConfig): Promise<void> {
  const continueButton = requiredElement<HTMLButtonElement>('#continue-google');
  const deleteButton = requiredElement<HTMLButtonElement>('#delete-account');
  const signOutButton = requiredElement<HTMLButtonElement>('#sign-out');
  const retrySignOutButton =
    requiredElement<HTMLButtonElement>('#retry-sign-out');
  const confirm = requiredElement<HTMLInputElement>('#delete-confirm');
  const signedInPanel = requiredElement<HTMLElement>('#signed-in-panel');
  const email = requiredElement<HTMLElement>('#signed-in-email');
  const receipt = requiredElement<HTMLElement>('#request-receipt');
  const receiptId = requiredElement<HTMLElement>('#receipt-id');
  const copyButton = requiredElement<HTMLButtonElement>('#copy-reference');
  const receiptInput = requiredElement<HTMLInputElement>('#receipt-check-id');
  const receiptCheckButton =
    requiredElement<HTMLButtonElement>('#check-receipt');
  const clearReceiptButton =
    requiredElement<HTMLButtonElement>('#clear-receipt');
  const receiptStatus = requiredElement<HTMLElement>('#receipt-status');

  setStatus(messages[language].loading);
  const services = await initializeDeletionServices(config);
  let user: User | null = null;
  let pendingRequestId = restorePendingReceipt();
  let deletionAccepted = false;
  let deletionSubmissionInFlight = false;
  let receiptCheckInFlight = false;
  let acceptedReceiptStorageVerified = true;

  const showReceipt = (value: string): void => {
    receiptId.textContent = value;
    receiptInput.value = value;
    receipt.hidden = false;
  };

  const updateDeleteButton = (): void => {
    deleteButton.disabled =
      deletionAccepted ||
      deletionSubmissionInFlight ||
      receiptCheckInFlight ||
      user === null ||
      !confirm.checked;
  };
  const updateReceiptControls = (): void => {
    receiptCheckButton.disabled =
      deletionSubmissionInFlight || receiptCheckInFlight;
    clearReceiptButton.disabled =
      deletionSubmissionInFlight || receiptCheckInFlight;
    receiptInput.disabled = deletionSubmissionInFlight || receiptCheckInFlight;
    if (receiptCheckInFlight) {
      continueButton.disabled = true;
    } else if (!deletionSubmissionInFlight && !deletionAccepted && user === null) {
      continueButton.disabled = false;
    }
  };
  const setDeletionSubmissionInFlight = (value: boolean): void => {
    deletionSubmissionInFlight = value;
    signOutButton.disabled = value || deletionAccepted;
    retrySignOutButton.disabled = value;
    if (value) {
      continueButton.disabled = true;
    } else if (!deletionAccepted && user === null) {
      continueButton.disabled = false;
    }
    updateReceiptControls();
    updateDeleteButton();
  };
  confirm.addEventListener('change', updateDeleteButton);

  const checkReceipt = async (
    allowDuringDeletionSubmission = false,
  ): Promise<void> => {
    if (
      (!allowDuringDeletionSubmission && deletionSubmissionInFlight) ||
      receiptCheckInFlight
    ) {
      return;
    }
    const checkedReceiptId = receiptInput.value.trim();
    if (!isDeletionReceiptId(checkedReceiptId)) {
      receiptStatus.textContent = messages[language].receiptInvalid;
      receiptStatus.dataset.tone = 'warning';
      return;
    }
    if (services.auth.currentUser !== null) {
      receiptStatus.textContent = messages[language].receiptSignOutRequired;
      receiptStatus.dataset.tone = 'warning';
      return;
    }
    receiptCheckInFlight = true;
    updateReceiptControls();
    updateDeleteButton();
    receiptStatus.textContent = messages[language].receiptChecking;
    receiptStatus.dataset.tone = 'neutral';
    try {
      const response = await services.readDeletionReceipt({
        contractVersion: 1,
        receiptId: checkedReceiptId,
      });
      const projection = receiptProjection(response.data);
      if (projection.kind === 'COMPLETED') {
        receiptStatus.textContent = messages[language].receiptCompleted;
        receiptStatus.dataset.tone = 'success';
        if (pendingRequestId === checkedReceiptId) {
          if (clearPendingReceipt()) {
            pendingRequestId = null;
          } else {
            receiptStatus.textContent = `${messages[language].receiptCompleted} ${messages[language].receiptClearFailed}`;
            receiptStatus.dataset.tone = 'warning';
          }
        }
      } else if (projection.kind === 'IN_PROGRESS') {
        receiptStatus.textContent = messages[language].receiptInProgress;
        receiptStatus.dataset.tone = 'neutral';
      } else if (projection.kind === 'NOT_FOUND') {
        receiptStatus.textContent = messages[language].receiptNotFound;
        receiptStatus.dataset.tone = 'warning';
      } else {
        receiptStatus.textContent = messages[language].invalidResponse;
        receiptStatus.dataset.tone = 'warning';
      }
    } catch {
      receiptStatus.textContent = messages[language].unavailable;
      receiptStatus.dataset.tone = 'warning';
    } finally {
      receiptCheckInFlight = false;
      updateReceiptControls();
      updateDeleteButton();
    }
  };

  receiptCheckButton.addEventListener('click', () => {
    void checkReceipt();
  });

  clearReceiptButton.addEventListener('click', () => {
    if (deletionSubmissionInFlight) {
      return;
    }
    if (!clearPendingReceipt()) {
      receiptStatus.textContent = messages[language].receiptClearFailed;
      receiptStatus.dataset.tone = 'warning';
      return;
    }
    pendingRequestId = null;
    receiptId.textContent = '';
    receiptInput.value = '';
    receipt.hidden = true;
    receiptStatus.textContent = messages[language].receiptCleared;
    receiptStatus.dataset.tone = 'neutral';
  });

  const verifyPostAcceptanceSignOut = async (): Promise<void> => {
    retrySignOutButton.disabled = true;
    try {
      await signOut(services.auth);
      if (services.auth.currentUser !== null) {
        throw new Error('Firebase Auth still has a current user');
      }
      user = null;
      email.textContent = '';
      signedInPanel.hidden = true;
      retrySignOutButton.hidden = true;
      continueButton.hidden = true;
      continueButton.disabled = true;
      setStatus(
        acceptedReceiptStorageVerified
          ? messages[language].requestAcceptedSignedOut
          : `${messages[language].requestAcceptedSignedOut} ${messages[language].acceptedReceiptStorageFailed}`,
        acceptedReceiptStorageVerified ? 'success' : 'warning',
      );
    } catch {
      user = services.auth.currentUser ?? user;
      signedInPanel.hidden = false;
      confirm.disabled = true;
      signOutButton.hidden = true;
      retrySignOutButton.hidden = false;
      setStatus(
        acceptedReceiptStorageVerified
          ? messages[language].postAcceptanceSignOutFailed
          : `${messages[language].postAcceptanceSignOutFailed} ${messages[language].acceptedReceiptStorageFailed}`,
        'warning',
      );
    } finally {
      retrySignOutButton.disabled = deletionSubmissionInFlight;
      updateDeleteButton();
    }
  };

  retrySignOutButton.addEventListener('click', () => {
    if (deletionAccepted) {
      void verifyPostAcceptanceSignOut();
    }
  });

  continueButton.addEventListener('click', async () => {
    if (deletionSubmissionInFlight || receiptCheckInFlight) {
      return;
    }
    continueButton.disabled = true;
    setStatus(messages[language].signingIn);
    try {
      const result = await signInWithPopup(services.auth, googleProvider());
      if (!isExactGoogleUser(result.user)) {
        email.textContent =
          result.user.email ?? result.user.providerData[0]?.email ?? '';
        try {
          await signOut(services.auth);
          if (services.auth.currentUser !== null) {
            throw new Error('Firebase Auth still has a current user');
          }
          setStatus(messages[language].wrongProvider, 'warning');
          continueButton.disabled = false;
        } catch {
          user = null;
          signedInPanel.hidden = false;
          continueButton.hidden = true;
          signOutButton.disabled = false;
          updateDeleteButton();
          setStatus(messages[language].signOutFailed, 'warning');
        }
        return;
      }
      user = result.user;
      email.textContent =
        result.user.email ?? result.user.providerData[0]?.email ?? '';
      signedInPanel.hidden = false;
      continueButton.hidden = true;
      setStatus(
        `${messages[language].signedIn}: ${email.textContent}`,
        'success',
      );
      updateDeleteButton();
    } catch (error) {
      const code = errorCode(error);
      setStatus(
        code === 'auth/popup-closed-by-user' ||
          code === 'auth/cancelled-popup-request'
          ? messages[language].popupClosed
          : messages[language].signInFailed,
        'warning',
      );
      continueButton.disabled = false;
    }
  });

  signOutButton.addEventListener('click', async () => {
    if (deletionSubmissionInFlight) {
      return;
    }
    signOutButton.disabled = true;
    try {
      await signOut(services.auth);
      if (services.auth.currentUser !== null) {
        throw new Error('Firebase Auth still has a current user');
      }
      user = null;
      confirm.checked = false;
      email.textContent = '';
      signedInPanel.hidden = true;
      continueButton.hidden = false;
      continueButton.disabled = false;
      setStatus(
        pendingRequestId === null
          ? messages[language].signedOut
          : messages[language].signedOutPending,
      );
    } catch {
      signedInPanel.hidden = false;
      continueButton.hidden = true;
      setStatus(messages[language].signOutFailed, 'warning');
    } finally {
      signOutButton.disabled = false;
      updateDeleteButton();
    }
  });

  deleteButton.addEventListener('click', async () => {
    if (
      deletionSubmissionInFlight ||
      receiptCheckInFlight ||
      user === null ||
      !confirm.checked
    ) {
      updateDeleteButton();
      return;
    }
    const submittedRequestId =
      pendingRequestId ?? window.crypto.randomUUID().toLowerCase();
    setDeletionSubmissionInFlight(true);
    setStatus(messages[language].reauthenticating);
    try {
      const confirmed = await reauthenticateWithPopup(user, googleProvider());
      if (
        confirmed.user.uid !== user.uid ||
        !isExactGoogleUser(confirmed.user)
      ) {
        throw new Error('Google reauthentication identity did not match');
      }
      if (!persistPendingReceipt(submittedRequestId)) {
        setStatus(messages[language].receiptStorageUnavailable, 'warning');
        return;
      }
      pendingRequestId = submittedRequestId;
      setStatus(messages[language].submitting);
      const response = await services.deleteAccount({
        contractVersion: 1,
        requestId: submittedRequestId,
      });
      const outcome = await deletionStartProjection(
        response.data,
        submittedRequestId,
      );
      if (outcome.kind === 'BUSY') {
        setStatus(messages[language].operationInProgress, 'warning');
        return;
      }
      if (outcome.kind === 'MISMATCH') {
        setStatus(messages[language].requestMismatch, 'warning');
        return;
      }
      if (outcome.kind !== 'ACCEPTED') {
        setStatus(messages[language].invalidResponse, 'warning');
        return;
      }

      pendingRequestId = outcome.receiptId;
      acceptedReceiptStorageVerified = persistPendingReceipt(outcome.receiptId);
      deletionAccepted = true;
      showReceipt(outcome.receiptId);
      confirm.disabled = true;
      signOutButton.hidden = true;
      retrySignOutButton.hidden = true;
      setStatus(messages[language].requestAccepted, 'success');
      updateDeleteButton();
      await verifyPostAcceptanceSignOut();
      await checkReceipt(true);
    } catch (error) {
      const code = errorCode(error);
      if (
        code === 'auth/popup-closed-by-user' ||
        code === 'auth/cancelled-popup-request'
      ) {
        setStatus(messages[language].popupClosed, 'warning');
      } else if (
        code === 'functions/failed-precondition' ||
        code === 'functions/unauthenticated'
      ) {
        setStatus(messages[language].recentAuthRequired, 'warning');
      } else if (code?.startsWith('auth/') === true) {
        setStatus(messages[language].signInFailed, 'warning');
      } else {
        setStatus(messages[language].unavailable, 'warning');
      }
    } finally {
      setDeletionSubmissionInFlight(false);
    }
  });

  copyButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(receiptId.textContent ?? '');
      setStatus(messages[language].copied, 'success');
    } catch {
      setStatus(messages[language].copyFailed, 'warning');
    }
  });

  continueButton.disabled = false;
  if (pendingRequestId === null) {
    setStatus(messages[language].ready, 'success');
  } else {
    showReceipt(pendingRequestId);
    setStatus(messages[language].receiptRestored);
    void checkReceipt();
  }
}

async function main(): Promise<void> {
  setupLanguage();
  const year = element<HTMLElement>('[data-current-year]');
  if (year !== null) {
    year.textContent = String(new Date().getUTCFullYear());
  }
  try {
    const config = await loadRuntimeConfig();
    bindRuntimeConfig(config);
    if (document.body.dataset.page === 'delete-account') {
      await setupDeletionPage(config);
    }
  } catch {
    showConfigurationFailure();
    if (document.body.dataset.page === 'delete-account') {
      setStatus(messages[language].configUnavailable, 'warning');
    }
  }
}

void main();
