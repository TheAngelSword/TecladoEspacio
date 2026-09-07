package com.tecladoespacio.app

import android.content.Context

object KeyboardPrefs {
    private const val PREFS_NAME = "teclado_espacio_prefs"
    const val KEY_VIBRATION = "vibration_enabled"
    const val KEY_SOUND = "sound_enabled"
    const val KEY_AUTOCORRECT = "autocorrect_enabled"
    const val KEY_KEY_HEIGHT = "key_height_percent"

    private const val DEFAULT_VIBRATION = true
    private const val DEFAULT_SOUND = true
    private const val DEFAULT_AUTOCORRECT = true
    private const val DEFAULT_KEY_HEIGHT = 116

    fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun vibrationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATION, DEFAULT_VIBRATION)

    fun soundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, DEFAULT_SOUND)

    fun autocorrectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOCORRECT, DEFAULT_AUTOCORRECT)

    fun keyHeightPercent(context: Context): Int =
        prefs(context).getInt(KEY_KEY_HEIGHT, DEFAULT_KEY_HEIGHT)
}
