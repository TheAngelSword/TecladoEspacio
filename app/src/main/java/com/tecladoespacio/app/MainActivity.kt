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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(30))
            setBackgroundColor(Color.rgb(245,245,247))
        }
        scroll.addView(box)
        box.addView(label("Teclado Espacio", 28f, true))
        box.addView(label("Versión 3.0: diseño tipo Gboard sin botón de punto, símbolos reales, panel amplio de emojis, selector de GIF y dictado por voz.", 15f, false))
        box.addView(Button(this).apply { text = "Activar teclado en Android"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } }, lpTop(16))
        box.addView(Button(this).apply { text = "Elegir Teclado Espacio"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } }, lpTop(8))
        box.addView(label("Ajustes", 20f, true), lpTop(22))
        box.addView(CheckBox(this).apply { text = "Vibrar al presionar teclas"; isChecked = KeyboardPrefs.vibrationEnabled(this@MainActivity); setOnCheckedChangeListener { _, b -> KeyboardPrefs.setVibration(this@MainActivity, b) } })
        box.addView(CheckBox(this).apply { text = "Sonido de click ligero"; isChecked = KeyboardPrefs.soundEnabled(this@MainActivity); setOnCheckedChangeListener { _, b -> KeyboardPrefs.setSound(this@MainActivity, b) } })
        box.addView(CheckBox(this).apply { text = "Corrector/autorrelleno en español"; isChecked = KeyboardPrefs.autocorrectEnabled(this@MainActivity); setOnCheckedChangeListener { _, b -> KeyboardPrefs.setAutocorrect(this@MainActivity, b) } })
        val sizeTitle = label("Tamaño de teclas: ${KeyboardPrefs.keyHeightPercent(this)}%", 16f, true)
        box.addView(sizeTitle, lpTop(14))
        box.addView(SeekBar(this).apply { max = 35; progress = KeyboardPrefs.keyHeightPercent(this@MainActivity) - 100; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) { val value = 100 + p; KeyboardPrefs.setKeyHeightPercent(this@MainActivity, value); sizeTitle.text = "Tamaño de teclas: $value%" } }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) })
        box.addView(label("GIF: el botón abre el selector de archivos de Android y puede insertar un GIF en aplicaciones compatibles. Voz: el micrófono usa el servicio de reconocimiento de voz instalado en el teléfono para dictar texto.", 14f, false), lpTop(18))
        setContentView(scroll)
    }
    private fun label(text: String, size: Float, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(30,30,32)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.START; setPadding(0, dp(4), 0, dp(4)) }
    private fun lpTop(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
