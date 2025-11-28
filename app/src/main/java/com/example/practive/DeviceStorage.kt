package com.example.practive

import android.content.Context

object DeviceStorage {
    private const val PREFERENCES_FILE = "ble_prefs"
    private const val KEY_DEVICE_ADDRESS = "device_address"

    fun saveDeviceAddress(context: Context, address: String) {
        val prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEVICE_ADDRESS, address).apply()
    }

    fun loadDeviceAddress(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ADDRESS, null)
    }
}