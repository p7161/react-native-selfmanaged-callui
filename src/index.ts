import { AppRegistry, NativeModules, NativeEventEmitter, Platform } from 'react-native';
import RNCallKeep from 'react-native-callkeep';

const N = NativeModules as any;
const IncomingUi = N.IncomingUi; // native helper (Android only)

type SetupOptions = {
  appName: string;
  android?: {
    channelId?: string;
    channelName?: string;
    notificationTitle?: string;
  };
};

export function setupSelfManaged(opts: SetupOptions) {
  if (Platform.OS !== 'android') return;

  // ВАЖНО: selfManaged — на верхнем уровне
  RNCallKeep.setup({
    selfManaged: true,
    ios: { appName: opts.appName },
    android: {
      foregroundService: {
        channelId: opts.android?.channelId ?? 'incoming.calls',
        channelName: opts.android?.channelName ?? 'Incoming Calls',
        notificationTitle: opts.android?.notificationTitle ?? 'Входящий вызов'
      }
    }
  });

  // Зарегистрировать PhoneAccount (повторно — ок)
  RNCallKeep.registerPhoneAccount({
    android: {
      foregroundService: {
        channelId: opts.android?.channelId ?? 'incoming.calls',
        channelName: opts.android?.channelName ?? 'Incoming Calls',
        notificationTitle: opts.android?.notificationTitle ?? 'Входящий вызов'
      }
    }
  });
}

/** Зарегистрировать второй RN-root для экрана звонка */
export function registerIncomingRoot(Component: any) {
  if (Platform.OS !== 'android') return;
  AppRegistry.registerComponent('IncomingRoot', () => Component);
}

/** Показать full-screen входящий + (опционально) CallKeep.displayIncomingCall */
export async function showIncomingFullScreen(params: {
  uuid: string;
  number: string;
  name?: string;
  alsoDisplayWithCallKeep?: boolean;
}) {
  if (Platform.OS !== 'android') return;

  const num = params.number?.startsWith('+') ? params.number : `+${params.number}`;
  const name = params.name ?? num;

  if (params.alsoDisplayWithCallKeep !== false) {
    try { RNCallKeep.displayIncomingCall(params.uuid, num, name, false); } catch {}
  }

  try { IncomingUi.show(params.uuid, num, name); } catch {}
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

/** Привязать CallKeep-события (answer/end + буфер) */
export function bindCallKeepEvents(handlers: {
  onAnswer: (uuid: string) => void,
  onEnd: (uuid: string) => void,
  onShowIncomingUi?: (p: { uuid: string, handle?: string, name?: string }) => void,
}) {
  if (Platform.OS !== 'android') return;
  const emitter = new NativeEventEmitter(RNCallKeep as any);

  // live
  RNCallKeep.addEventListener('answerCall', ({ callUUID }: any) => {
    try { RNCallKeep.answerIncomingCall(callUUID); } catch {}
    handlers.onAnswer?.(callUUID);
  });
  RNCallKeep.addEventListener('endCall', ({ callUUID }: any) => {
    handlers.onEnd?.(callUUID);
  });
  if (handlers.onShowIncomingUi) {
    emitter.addListener('RNCallKeepShowIncomingCallUi', handlers.onShowIncomingUi as any);
  }

  // buffered (cold start)
  (async () => {
    try {
      const initial = await (RNCallKeep as any).getInitialEvents?.() ?? [];
      for (const e of initial) {
        if (e?.name === 'RNCallKeepPerformAnswerCallAction') {
          const uuid = e?.data?.callUUID;
          try { RNCallKeep.answerIncomingCall(uuid); } catch {}
          handlers.onAnswer?.(uuid);
        }
        if (e?.name === 'RNCallKeepPerformEndCallAction') {
          const uuid = e?.data?.callUUID;
          handlers.onEnd?.(uuid);
        }
        if (e?.name === 'RNCallKeepShowIncomingCallUi' && handlers.onShowIncomingUi) {
          handlers.onShowIncomingUi(e.data || {});
        }
      }
      (RNCallKeep as any).clearInitialEvents?.();
    } catch {}
  })();
}
