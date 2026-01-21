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
  avatarUrl?: string;     // http(s)://... or file://...
  extraData?: Record<string, any>; // whatever you want JSON string or other text
};

export async function showIncomingFullScreen(p: ShowIncomingParams) {
  if (Platform.OS !== 'android') return;

  const num = p.number;
  const name = p.name ?? p.displayName ?? num;

  // ВАЖНО: RNCallKeep.displayIncomingCall вызывайте сами, когда нужно
  try {
    // avatarUrl и extraData прокинем в нативный helper
    await IncomingUi.show(
        p.uuid,
        num,
        name,
        p.avatarUrl ?? '',
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
