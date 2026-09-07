package com.tecladoespacio.app

import android.content.Context

object KeyboardPrefs {
    private const val NAME = "teclado_espacio"
    private const val VIB = "vibration"
    private const val SOUND = "sound"
    private const val CORRECT = "autocorrect"
    private const val SIZE = "size"
    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun vibrationEnabled(c: Context) = p(c).getBoolean(VIB, true)
    fun soundEnabled(c: Context) = p(c).getBoolean(SOUND, true)
    fun autocorrectEnabled(c: Context) = p(c).getBoolean(CORRECT, true)
    fun keyHeightPercent(c: Context) = p(c).getInt(SIZE, 116)
    fun setVibration(c: Context, v: Boolean) = p(c).edit().putBoolean(VIB, v).apply()
    fun setSound(c: Context, v: Boolean) = p(c).edit().putBoolean(SOUND, v).apply()
    fun setAutocorrect(c: Context, v: Boolean) = p(c).edit().putBoolean(CORRECT, v).apply()
    fun setKeyHeightPercent(c: Context, v: Int) = p(c).edit().putInt(SIZE, v.coerceIn(100,135)).apply()
}
