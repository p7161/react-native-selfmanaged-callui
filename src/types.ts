declare module '@trubka/react-native-selfmanaged-callui' {
  export function registerIncomingRoot(Component: any): void;

  export type ShowIncomingParams = {
    uuid: string;
    number: string;
    name?: string;
    displayName?: string;
    avatarUri?: string;
    video?: boolean;
    extraData?: Record<string, any>;
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

  export function showIncomingFullScreen(p: ShowIncomingParams): Promise<void>;

  export function startCallActivity(p: StartCallActivityParams): Promise<void>;

  export function dismissIncomingUi(): void;

  export function finishIncomingActivity(): void;

  export type InitialEvent = {
    name: string;
    data: Record<string, any> | null;
  };

  export function getInitialEvents(): Promise<InitialEvent[]>;

  export function clearInitialEvents(): void;

  export function ensureIncomingChannel(title?: string, description?: string): Promise<void>;

  export function addIncomingPushListener(cb: (payload: IncomingPushPayload) => void): { remove(): void };

  export function getInitialPayload(): Promise<IncomingPushPayload | null>;

  export function clearInitialPayload(): void;

  export function clearIncomingLock(): void;

  export function subscribeIncomingPush(cb: (payload: IncomingPushPayload) => void): Promise<{ remove(): void }>;
}
