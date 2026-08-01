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

  export function showIncomingFullScreen(p: ShowIncomingParams): Promise<void>;

  export function startCallActivity(p: StartCallActivityParams): Promise<void>;

  export function dismissIncomingUi(uuid: string): void;

  export function finishIncomingActivity(uuid?: string): void;

  export function prepareTerminateCall(uuid: string): void;

  export function terminateCall(uuid: string): void;

  export type InitialEvent = {
    name: string;
    data: Record<string, any> | null;
  };

  export function getInitialEvents(): Promise<InitialEvent[]>;

  export function clearInitialEvents(): void;


  export function ensureIncomingChannel(title?: string, description?: string): Promise<void>;

  export function getStringFromDefaultPrefs(key: string): Promise<string | null>;

  export function setStringToDefaultPrefs(key: string, value: string): Promise<boolean>;

  export function removeFromDefaultPrefs(key: string): Promise<boolean>;
}
