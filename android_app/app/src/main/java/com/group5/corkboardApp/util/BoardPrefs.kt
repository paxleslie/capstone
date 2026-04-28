package com.group5.corkboardApp.util

import android.content.Context
import android.content.SharedPreferences

object BoardPrefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences("board_settings", Context.MODE_PRIVATE)
    }

    fun getDefaultColor(householdId: String): String? =
        prefs.getString("default_color_$householdId", null)

    fun setDefaultColor(householdId: String, color: String?) {
        prefs.edit().putString("default_color_$householdId", color).apply()
    }

    fun getDefaultSticker(householdId: String): String? =
        prefs.getString("default_sticker_$householdId", null)

    fun setDefaultSticker(householdId: String, url: String?) {
        prefs.edit().putString("default_sticker_$householdId", url).apply()
    }
}
