package com.tecladoespacio.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(30))
            setBackgroundColor(Color.rgb(246, 246, 248))
        }
        scroll.addView(root)

        root.addView(title("Teclado Espacio", 28f))
        root.addView(body("Versión 2: teclado más grande, sugerencias en español, emojis, stickers y retroalimentación configurable."))

        root.addView(button("1. Abrir ajustes de teclados") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("2. Elegir teclado actual") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(sectionTitle("Ajustes"))
        val vibration = option("Vibración al presionar", KeyboardPrefs.vibrationEnabled(this))
        val sound = option("Sonido ligero tipo click", KeyboardPrefs.soundEnabled(this))
        val autocorrect = option("Corrector y autorelleno en español", KeyboardPrefs.autocorrectEnabled(this))
        root.addView(vibration)
        root.addView(sound)
        root.addView(autocorrect)

        vibration.setOnCheckedChangeListener { _, value -> KeyboardPrefs.prefs(this).edit().putBoolean(KeyboardPrefs.KEY_VIBRATION, value).apply() }
        sound.setOnCheckedChangeListener { _, value -> KeyboardPrefs.prefs(this).edit().putBoolean(KeyboardPrefs.KEY_SOUND, value).apply() }
        autocorrect.setOnCheckedChangeListener { _, value -> KeyboardPrefs.prefs(this).edit().putBoolean(KeyboardPrefs.KEY_AUTOCORRECT, value).apply() }

        root.addView(sectionTitle("Tamaño del teclado"))
        val valueLabel = body("${KeyboardPrefs.keyHeightPercent(this)}%")
        root.addView(valueLabel)
        val seek = SeekBar(this).apply {
            max = 35
            progress = KeyboardPrefs.keyHeightPercent(this@MainActivity) - 100
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 100 + progress
                valueLabel.text = "$value%"
                if (fromUser) KeyboardPrefs.prefs(this@MainActivity).edit().putInt(KeyboardPrefs.KEY_KEY_HEIGHT, value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(seek)

        root.addView(sectionTitle("Funciones nuevas"))
        root.addView(body("• Barra de sugerencias/autorrelleno en español.\n• 😊 abre emojis.\n• ✦ abre stickers de texto rápidos.\n• Vibración y click configurables.\n• Tamaño ajustable de 100% a 135%.\n\nLos cambios se aplican al volver a abrir el teclado."))

        setContentView(scroll)
    }

    private fun title(text: String, size: Float) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(25,25,28)); setTypeface(typeface, 1)
    }
    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(Color.rgb(25,25,28)); setTypeface(typeface, 1); setPadding(0, dp(22), 0, dp(8))
    }
    private fun body(text: String) = TextView(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.rgb(80,80,86)); setPadding(0, dp(7), 0, dp(7))
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }
    private fun option(text: String, checked: Boolean) = Switch(this).apply {
        this.text = text; isChecked = checked; textSize = 15f; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
