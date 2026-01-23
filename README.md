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
