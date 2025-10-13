declare module '@trubka/react-native-selfmanaged-callui' {
    export function showIncomingFullScreen(p: {
        uuid: string;
        number: string;
        name?: string;
        avatarUrl?: string;
        extraData?: Record<string, any>;
    }): Promise<void>;
    // ...остальное без изменений
}
