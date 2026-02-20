@trubka/react-native-selfmanaged-callui

# What it does

Enables self-managed mode in CallKeep;

Provides a native helper for raising the call screen to full screen, even on a locked screen;

Provides IncomingCallActivity (second Activity) and the IncomingUi RN module (show/dismiss/finish);

Provides convenient JS helpers: registering a second RN root, binding CallKeep events, and showing incoming calls.

# Install

```
npm i https://github.com/p7161/react-native-selfmanaged-callui
```

In your index.ts or index.android.ts
```
import { registerRootComponent } from 'expo';
import { AppRegistry } from 'react-native';
import App from './App';
import IncomingRoot from './IncomingRoot';

// 1) Main app
registerRootComponent(App);

// 2) Call Screen
import { ensureIncomingChannel, registerIncomingRoot, showIncomingFullScreen } from '@trubka/react-native-selfmanaged-callui';
registerIncomingRoot(IncomingRoot);

// Ensure/update notification channel metadata before using incoming UI
await ensureIncomingChannel('Incoming Calls', 'Incoming call notifications');


// Whenever you recieve the Android data push show the incoming call
RNCallKeep.displayIncomingCall(
    String(callkitUUID),
    String(fromNumber),
    String(displayName),
    false
);
await showIncomingFullScreen({ uuid, number: fromNumber, displayName, avatarUri, extraData: {anyExtraData:"true"} });
```

You can see the example of IncomingRoot.tsx in /src/IncomingRoot.tsx

## Incoming UI events

The Android notification actions now emit events from the `IncomingUi` module. You can listen to
`answerCall`, `endCall`, and the batched `IncomingUiDidLoadWithEvents` (when JS was not ready).

```
import { NativeModules, NativeEventEmitter } from 'react-native';

const IncomingUi = NativeModules.IncomingUi;
const emitter = new NativeEventEmitter(IncomingUi);

emitter.addListener('answerCall', (data) => {
  CallManager.answerCall(String(data.uuid));
});

emitter.addListener('endCall', () => {
  CallManager.endCall();
});

emitter.addListener('IncomingUiDidLoadWithEvents', (events) => {
  for (const ev of events) {
    if (ev.name === 'answerCall') CallManager.answerCall(String(ev.data?.uuid));
    if (ev.name === 'endCall') CallManager.endCall();
  }
});
```

## Android 13+ notifications

For Android 13+ (API 33) you should request `POST_NOTIFICATIONS` at runtime in your app,
otherwise notifications may not appear. This library does not declare that permission in its
manifest so you can control the request flow in your application.

## MIUI (HyperOS) permissions usage (React Native)

Below is a minimal example of how to detect MIUI/HyperOS, check MIUI custom app-op permissions,
and open the MIUI permission manager screens from React Native:

```ts
import { NativeModules, Platform } from 'react-native';

const { IncomingUi } = NativeModules;

async function ensureMiuiPermissions() {
  if (Platform.OS !== 'android') return;

  const isMiui = await IncomingUi.isMiui();
  if (!isMiui) return;

  const major = await IncomingUi.getMiuiMajorVersion();
  console.log('MIUI/HyperOS version:', major);

  const ops = await IncomingUi.getMiuiCustomPermissionOps();
  const autoStartGranted = await IncomingUi.isMiuiCustomPermissionGranted(ops.OP_AUTO_START);
  const bgStartGranted = await IncomingUi.isMiuiCustomPermissionGranted(
    ops.OP_BACKGROUND_START_ACTIVITY
  );

  if (!autoStartGranted || !bgStartGranted) {
    // open MIUI-specific permission manager
    IncomingUi.openMiuiPermissionManager();
  }
}
```


## IncomingPush API (single listener + cold start payload)

Use these helpers when your app receives Android FCM data pushes (`type=incoming_call`) via the
built-in library receiver.

```ts
import {
  addIncomingPushListener,
  getInitialPayload,
  clearInitialPayload,
  clearIncomingLock,
  subscribeIncomingPush,
} from '@trubka/react-native-selfmanaged-callui';

// payload shape from native receiver/activity
// type IncomingPushPayload = {
//   type?: string;
//   callId?: string;
//   callkitUUID?: string;
//   handle?: string;
//   peerId?: string;
//   callerName?: string;
//   avatarUri?: string;
//   video?: boolean;
//   receivedAt?: number;
//   uiShown?: boolean;
//   blockedReason?: string; // "already_active" when dedupe lock blocks UI
//   uuid?: string;
//   number?: string;
//   displayName?: string;
//   extraData?: Record<string, any>;
//   incoming_call?: boolean;
// };
```

### 1) `addIncomingPushListener(cb)`

```ts
const sub = addIncomingPushListener((payload) => {
  console.log('IncomingPush event:', payload);

  // UI not shown due to active lock, but push still delivered to RN
  if (payload.uiShown === false && payload.blockedReason === 'already_active') {
    // optional custom app logic
  }
});

// later
sub.remove();
```

### 2) `getInitialPayload()`

Use for cold start / when RN initialized after native `onReceive`.

```ts
const initial = await getInitialPayload();
if (initial) {
  console.log('Initial incoming payload:', initial);
}
```

### 3) `clearInitialPayload()`

```ts
clearInitialPayload();
```

### 4) `clearIncomingLock()`

Manual unlock from RN (for example, when your app has completed its own call teardown flow).

```ts
clearIncomingLock();
```

### 5) `subscribeIncomingPush(cb)`

Convenience helper: first returns last initial payload (if exists), then subscribes to live events.

```ts
const sub = await subscribeIncomingPush((payload) => {
  console.log('incoming push (initial or live):', payload);
});

// later
sub.remove();
```
