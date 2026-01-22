import { AppRegistry, NativeModules, NativeEventEmitter, Platform } from 'react-native';

const N = NativeModules as any;
const IncomingUi = N.IncomingUi; // native helper (Android only)


/** Зарегистрировать второй RN-root для экрана звонка */
export function registerIncomingRoot(Component: any) {
  if (Platform.OS !== 'android') return;
  AppRegistry.registerComponent('IncomingRoot', () => Component);
}

/** Показать full-screen входящий + (опционально) CallKeep.displayIncomingCall */
type ShowIncomingParams = {
  uuid: string;
  number: string;         // +7900...
  name?: string;
  displayName?: string;   // alias for name
  avatarUri?: string;     // http(s)://... or file://... or relative path for uploadUri
  video?: boolean;
  extraData?: Record<string, any>; // whatever you want JSON string or other text
};

type StartCallActivityParams = {
  uuid: string;
  number?: string;
  name?: string;
  displayName?: string;
  avatarUri?: string;
  video?: boolean;
  extraData?: Record<string, any>;
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

  // ВАЖНО: RNCallKeep.displayIncomingCall вызывайте сами, когда нужно
  try {
    // avatarUri и extraData прокинем в нативный helper
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
