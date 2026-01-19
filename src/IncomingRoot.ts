import React, { useEffect, useState } from 'react';
import { View, Text, Button, Image, NativeEventEmitter, NativeModules } from 'react-native';
import RNCallKeep from 'react-native-callkeep';

// Example of Incoming screen
export default function IncomingRoot({ initialProps }: any) {
    const [p, setP] = useState(() => ({
        uuid: String(initialProps?.uuid || ''),
        number: String(initialProps?.number || ''),
        displayName: String(initialProps?.displayName || ''),
        avatarUrl: initialProps?.avatarUrl ? String(initialProps.avatarUrl) : '',
        extraData: initialProps?.extraData ?? null,
    }));

    useEffect(() => {
        const emitter = new NativeEventEmitter(NativeModules.DeviceEventManager);
        const sub = emitter.addListener('IncomingIntent', async (e: any) => {
            setP(prev => ({
                ...prev,
                uuid: String(e?.uuid || prev.uuid),
                number: String(e?.number || prev.number),
                displayName: String(e?.displayName || prev.displayName),
                avatarUrl: e?.avatarUrl ? String(e.avatarUrl) : prev.avatarUrl,
                extraData: e?.extraData ?? prev.extraData,
            }));
            const action = e?.notif_action;
            if (action === 'answer') {
                try { RNCallKeep.answerIncomingCall(String(e.uuid)); } catch {}
            }
            if (action === 'decline') {
                try { RNCallKeep.rejectCall(String(e.uuid)); } catch {}
            }
        });
        return () => sub.remove();
    }, []);

    const onAccept = async () => {
        try { RNCallKeep.answerIncomingCall(p.uuid); } catch {}
    };
    const onDecline = async () => {
        try { RNCallKeep.rejectCall(p.uuid); } catch {}
    };

    return (
        <View style={{ flex:1, alignItems:'center', justifyContent:'center', padding:24 }}>
            {!!p.avatarUrl && (
                <Image
                    source={{ uri: p.avatarUrl }}
                    style={{ width: 120, height: 120, borderRadius: 60, marginBottom: 16 }}
                />
            )}
            <Text style={{ fontSize:22, marginBottom:8 }}>{p.displayName || p.number}</Text>
            <Text style={{ opacity:0.6, marginBottom:24 }}>Входящий вызов</Text>
            <View style={{ flexDirection:'row', gap:12 }}>
                <Button title="Отклонить" onPress={onDecline} />
                <Button title="Ответить"  onPress={onAccept} />
            </View>
        </View>
    );
}
