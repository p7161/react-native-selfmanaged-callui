import { AppRegistry, NativeModules, Platform } from 'react-native';

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

/**
 * Убрать фуллскрин-нотификацию, не завершая звонок (например, его приняли).
 * uuid снимает признак «звонит» именно с этого звонка — без него показ, который
 * ещё в полёте, поднимет incoming-UI с рингтоном уже после принятия.
 */
export function dismissIncomingUi(uuid: string) {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.dismiss(uuid); } catch {}
}

/** Закрыть IncomingCallActivity (если она открыта) */
export function finishIncomingActivity() {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.finishActivity(); } catch {}
}

/**
 * Звонок завершён: убирает нотификацию и активити И помечает uuid терминальным,
 * чтобы показ, который в этот момент ещё летит (резолв аватара, канал, full-screen
 * intent от системы), не поднял UI уже мёртвого звонка.
 * Для «принял звонок» это не подходит — там нужен dismissIncomingUi.
 */
export function terminateCall(uuid: string) {
  if (Platform.OS !== 'android') return;
  try { IncomingUi.terminateCall(uuid); } catch {}
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

export async function getStringFromDefaultPrefs(key: string): Promise<string | null> {
  if (Platform.OS !== 'android') return null;
  try {
    return await IncomingUi.getStringFromDefaultPrefs(key);
  } catch {
    return null;
  }
}

/**
 * Пишем в те же DefaultSharedPreferences, что и patched
 * ReactNativeFirebaseMessagingReceiver (sync commit). Нужен для
 * надёжного клиентского dedup: отметка о том что callId уже
 * обработан, должна пережить kill процесса между WS и последующим
 * push (сервер шлёт push через 10 сек после WS).
 */
export async function setStringToDefaultPrefs(key: string, value: string): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  try {
    return await IncomingUi.setStringToDefaultPrefs(key, value);
  } catch (e) {
    console.warn('[selfManaged] setStringToDefaultPrefs failed', e);
    return false;
  }
}

export async function removeFromDefaultPrefs(key: string): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  try {
    return await IncomingUi.removeFromDefaultPrefs(key);
  } catch {
    return false;
  }
}
