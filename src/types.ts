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

  export function showIncomingFullScreen(p: ShowIncomingParams): Promise<void>;

  export function dismissIncomingUi(): void;

  export function finishIncomingActivity(): void;

  export type InitialEvent = {
    name: string;
    data: Record<string, any> | null;
  };

  export function getInitialEvents(): Promise<InitialEvent[]>;

  export function clearInitialEvents(): void;
}
