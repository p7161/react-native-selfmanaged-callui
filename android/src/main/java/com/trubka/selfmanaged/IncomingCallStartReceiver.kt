package com.trubka.selfmanaged

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class IncomingCallStartReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val extras = intent?.extras ?: return

    val type = extras.getString("type") ?: return
    if (type != "incoming_call") return

    val callId = extras.getString("callId")
    val callkitUUID = extras.getString("callkitUUID") ?: extras.getString("uuid") ?: callId
    val handle = extras.getString("handle") ?: extras.getString("number") ?: ""
    val peerId = extras.getString("peerId")
    val callerName = extras.getString("callerName") ?: extras.getString("displayName") ?: handle
    val avatarUri = extras.getString("avatarUri")
    val video = extras.getBoolean("video", false)
    val receivedAt = System.currentTimeMillis()

    val extraData = Bundle().apply {
      putString("type", type)
      putString("callId", callId)
      putString("callkitUUID", callkitUUID)
      putString("handle", handle)
      putString("peerId", peerId)
      putString("callerName", callerName)
      putString("avatarUri", avatarUri)
      putBoolean("video", video)
    }

    val payload = Bundle().apply {
      putString("type", type)
      putString("callId", callId)
      putString("callkitUUID", callkitUUID)
      putString("handle", handle)
      putString("peerId", peerId)
      putString("callerName", callerName)
      putString("avatarUri", avatarUri)
      putBoolean("video", video)
      putLong("receivedAt", receivedAt)

      // duplicated fields for existing call pipeline
      putString("uuid", callkitUUID)
      putString("number", handle)
      putString("displayName", callerName)
      putBundle("extraData", extraData)
      putBoolean("incoming_call", true)
    }

    val hasActiveIncoming = IncomingCallLock.isActive(context)
    if (hasActiveIncoming) {
      payload.putBoolean("uiShown", false)
      payload.putString("blockedReason", "already_active")
    } else {
      IncomingUi.ensureChannel(context, null, null)
      IncomingUi.show(context, callkitUUID ?: "", handle, callerName, avatarUri, video, extraData)
      val payloadJson = IncomingPushStore.bundleToJson(payload).toString()
      IncomingCallLock.setActive(context, callkitUUID ?: "", receivedAt, payloadJson)
      payload.putBoolean("uiShown", true)
    }

    IncomingPushStore.save(context, payload)
    IncomingUiModule.sendEventToJS("IncomingPush", payload)
  }
}
