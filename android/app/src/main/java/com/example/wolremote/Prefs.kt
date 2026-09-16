package com.example.wolremote

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "wol_prefs"

    const val KEY_SERVER = "server_url"
    const val KEY_MAC = "mac_address"
    const val KEY_BROADCAST = "broadcast_address"
    const val KEY_TARGET_IP = "target_ip"
    const val KEY_INTERVAL = "poll_interval"
    const val KEY_LAST_EVENT = "last_event"

    fun get(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}