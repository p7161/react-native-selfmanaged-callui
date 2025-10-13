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
import { registerIncomingRoot, showIncomingFullScreen } from '@trubka/react-native-selfmanaged-callui';
registerIncomingRoot(IncomingRoot);


// Whenever you recieve the Android data push show the incoming call
RNCallKeep.displayIncomingCall(
    String(callkitUUID),
    String(fromNumber),
    String(displayName),
    false
);
await showIncomingFullScreen({ uuid, number: fromNumber, displayName, avatarUrl, extraData: {anyExtraData:"true"} });
```

You can see the example of IncomingRoot.tsx in /src/IncomingRoot.tsx
