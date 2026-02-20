package com.trubka.selfmanaged

import android.content.Context

object IncomingCallLock {
  @Volatile
  private var activeUuid: String? = null

  @Volatile
  private var activeSince: Long = 0L

  @Volatile
  private var activePayload: String? = null

  fun isActive(ctx: Context): Boolean {
    return !activeUuid.isNullOrBlank()
  }

  fun getActiveUuid(ctx: Context): String? = activeUuid

  fun setActive(ctx: Context, uuid: String, since: Long = System.currentTimeMillis(), payloadJson: String? = null) {
    activeUuid = uuid
    activeSince = since
    activePayload = payloadJson
  }

  fun clear(ctx: Context) {
    activeUuid = null
    activeSince = 0L
    activePayload = null
  }
}
