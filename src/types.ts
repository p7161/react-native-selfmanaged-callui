declare module '@trubka/react-native-selfmanaged-callui' {
  export function registerIncomingRoot(Component: any): void;

  export type ShowIncomingParams = {
    uuid: string;
    number: string;
    name?: string;
    displayName?: string;
    avatarUrl?: string;
    extraData?: Record<string, any>;
  };

  export function showIncomingFullScreen(p: ShowIncomingParams): Promise<void>;

  export function dismissIncomingUi(): void;

  export function finishIncomingActivity(): void;
}
