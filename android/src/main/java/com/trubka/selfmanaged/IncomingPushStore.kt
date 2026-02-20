package com.trubka.selfmanaged

import android.content.Context
import android.os.Bundle
import org.json.JSONObject

object IncomingPushStore {
  private const val PREFS = "rn-selfmanaged-callui"
  private const val KEY_PAYLOAD_JSON = "initial_payload_json"
  private const val KEY_PAYLOAD_AT = "initial_payload_at"

  private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun save(ctx: Context, payloadBundle: Bundle) {
    val json = bundleToJson(payloadBundle).toString()
    prefs(ctx).edit()
      .putString(KEY_PAYLOAD_JSON, json)
      .putLong(KEY_PAYLOAD_AT, System.currentTimeMillis())
      .apply()
  }

  fun get(ctx: Context): Bundle? {
    val raw = prefs(ctx).getString(KEY_PAYLOAD_JSON, null) ?: return null
    return try {
      jsonToBundle(JSONObject(raw))
    } catch (_: Exception) {
      null
    }
  }

  fun clear(ctx: Context) {
    prefs(ctx).edit()
      .remove(KEY_PAYLOAD_JSON)
      .remove(KEY_PAYLOAD_AT)
      .apply()
  }

  fun bundleToJson(bundle: Bundle): JSONObject {
    val json = JSONObject()
    for (key in bundle.keySet()) {
      val value = bundle.get(key)
      when (value) {
        null -> json.put(key, JSONObject.NULL)
        is Bundle -> json.put(key, bundleToJson(value))
        is Boolean, is Number, is String -> json.put(key, value)
        else -> json.put(key, value.toString())
      }
    }
    return json
  }

  private fun jsonToBundle(json: JSONObject): Bundle {
    val b = Bundle()
    val keys = json.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      val value = json.opt(key)
      when (value) {
        JSONObject.NULL -> b.putString(key, null)
        is JSONObject -> b.putBundle(key, jsonToBundle(value))
        is Boolean -> b.putBoolean(key, value)
        is Int -> b.putInt(key, value)
        is Long -> b.putLong(key, value)
        is Double -> b.putDouble(key, value)
        is Number -> b.putDouble(key, value.toDouble())
        is String -> b.putString(key, value)
        else -> b.putString(key, value?.toString())
      }
    }
    return b
  }
}
