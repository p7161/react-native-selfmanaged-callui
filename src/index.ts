import { AppRegistry, NativeEventEmitter, NativeModules, Platform } from 'react-native';

const N = NativeModules as any;
const IncomingUi = N.IncomingUi; // native helper (Android only)
const incomingEmitter = IncomingUi ? new NativeEventEmitter(IncomingUi) : null;

/** Зарегистрировать второй RN-root для экрана звонка */
export function registerIncomingRoot(Component: any) {
  if (Platform.OS !== 'android') return;
  AppRegistry.registerComponent('IncomingRoot', () => Component);
}

/** Показать full-screen входящий + (опционально) CallKeep.displayIncomingCall */
export type ShowIncomingParams = {
  uuid: string;
  number: string;         // +7900...
  name?: string;
  displayName?: string;   // alias for name
  avatarUri?: string;     // http(s)://... or file://... or relative path for uploadUri
  video?: boolean;
  extraData?: Record<string, any>; // whatever you want JSON string or other text
};

export type StartCallActivityParams = {
  uuid: string;
  number?: string;
  name?: string;
  displayName?: string;
  avatarUri?: string;
  video?: boolean;
  extraData?: Record<string, any>;
};

export type IncomingPushPayload = {
  type?: string;
  callId?: string;
  callkitUUID?: string;
  handle?: string;
  peerId?: string;
  callerName?: string;
  avatarUri?: string;
  video?: boolean;
  receivedAt?: number;
  uiShown?: boolean;
  blockedReason?: string;
  uuid?: string;
  number?: string;
  displayName?: string;
  extraData?: Record<string, any>;
  incoming_call?: boolean;
};

const UPLOAD_URI = 'https://pipe.tel/uploads';

export async function showIncomingFullScreen(p: ShowIncomingParams) {
  if (Platform.OS !== 'android') return;

  const num = p.number;
  const name = p.name ?? p.displayName ?? num;
  const avatarUri = p.avatarUri
    ? (p.avatarUri.startsWith('http') || p.avatarUri.startsWith('file://')
        ? p.avatarUri
        : `${UPLOAD_URI}/${p.avatarUri}`)
    : '';
  const video = Boolean(p.video);

  try {
    await IncomingUi.show(
      p.uuid,
      num,
      name,
      avatarUri,
      video,
      p.extraData ?? null
    );
  } catch {}
}

/** Открыть IncomingCallActivity напрямую (без нотификации) */
export async function startCallActivity(p: StartCallActivityParams) {
  if (Platform.OS !== 'android') return;

  const num = p.number ?? '';
  const name = p.name ?? p.displayName ?? num;
  const avatarUri = p.avatarUri
    ? (p.avatarUri.startsWith('http') || p.avatarUri.startsWith('file://')
        ? p.avatarUri
        : `${UPLOAD_URI}/${p.avatarUri}`)
    : '';
  const video = Boolean(p.video);

  try {
    await IncomingUi.startCallActivity(
      p.uuid,
      num,
      name,
      avatarUri,
      video,
      p.extraData ?? null
    );
  } catch {}
}

/** Убрать фуллскрин-нотификацию */
export function dismissIncomingUi() {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.dismiss(); } catch {}
}

/** Закрыть IncomingCallActivity (если она открыта) */
export function finishIncomingActivity() {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.finishActivity(); } catch {}
}

export async function getInitialEvents() {
  if (Platform.OS !== 'android') return [];
  try {
    return await IncomingUi.getInitialEvents();
  } catch {
    return [];
  }
}

export function clearInitialEvents() {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.clearInitialEvents(); } catch {}
}

export async function ensureIncomingChannel(title?: string, description?: string) {
  if (Platform.OS !== 'android') return;
  try { await IncomingUi.ensureIncomingChannel(title ?? null, description ?? null); } catch {}
}

export function addIncomingPushListener(cb: (payload: IncomingPushPayload) => void) {
  if (Platform.OS !== 'android' || !incomingEmitter) {
    return { remove: () => {} };
  }
  return incomingEmitter.addListener('IncomingPush', cb);
}

export async function getInitialPayload(): Promise<IncomingPushPayload | null> {
  if (Platform.OS !== 'android') return null;
  try {
    return await IncomingUi.getInitialPayload();
  } catch {
    return null;
  }
}

export function clearInitialPayload() {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.clearInitialPayload(); } catch {}
}

export function clearIncomingLock() {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.clearIncomingLock(); } catch {}
}

export async function subscribeIncomingPush(cb: (payload: IncomingPushPayload) => void) {
  const initial = await getInitialPayload();
  if (initial) cb(initial);
  return addIncomingPushListener(cb);
}
