package com.tecladoespacio.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs

class TecladoImeService : InputMethodService() {
    private var upper = false
    private var symbols = false
    private lateinit var root: LinearLayout
    private var suggestions: LinearLayout? = null

    private val rows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l","ñ"),
        listOf("z","x","c","v","b","n","m")
    )
    private val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","-","+","(",")","/"),
        listOf("*","\"","'",":",";","!","?","_",".")
    )
    private val accents = mapOf(
        "a" to listOf("á","à","ä","â"), "e" to listOf("é","è","ë","ê"),
        "i" to listOf("í","ì","ï","î"), "o" to listOf("ó","ò","ö","ô"),
        "u" to listOf("ú","ù","ü","û"), "n" to listOf("ñ")
    )
    private val emojis = listOf("😀","😂","😍","🥳","😎","😉","😊","😭","😡","🙏","👍","👏","🙌","🔥","❤️","💙","🎉","✨")
    private val stickers = listOf("¡Excelente!","¡Felicidades!","¡Gracias!","¡Buen trabajo!","¡Ánimo!","¡Listo!","¯\\_(ツ)_/¯","(づ｡◕‿‿◕｡)づ","❤️🔥","🎉🥳")

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(228,228,230))
            setPadding(dp(5), dp(4), dp(5), dp(7))
        }
        buildKeyboard()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) {
            symbols = false
            upper = shouldCap(info)
            if (::root.isInitialized) buildKeyboard()
        }
    }

    private fun buildKeyboard() {
        root.removeAllViews()
        addToolbar()
        addSuggestions()
        if (symbols) addSymbols() else addLetters()
        refreshSuggestions()
    }

    private fun addToolbar() {
        val bar = row(40)
        toolbarButton(bar, "😊") { showPicker(it, emojis, false) }
        toolbarButton(bar, "GIF") { commit("GIF ") }
        toolbarButton(bar, "✦") { showPicker(it, stickers, true) }
        toolbarButton(bar, "📋") { showKeyboardPicker() }
        toolbarButton(bar, "⚙") { openSettings() }
        toolbarButton(bar, "🎤") { showKeyboardPicker() }
        root.addView(bar)
    }

    private fun toolbarButton(parent: LinearLayout, label: String, action: (View) -> Unit) {
        parent.addView(TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = if (label == "GIF") 13f else 18f
            setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener { feedback(); action(this) }
        })
    }

    private fun addSuggestions() {
        suggestions = row(38).also { root.addView(it) }
    }

    private fun addLetters() {
        addKeyRow(rows[0]); addKeyRow(rows[1])
        val third = row(keyHeight())
        addAction(third, if (upper) "⇪" else "⇧", 1.35f) { upper = !upper; buildKeyboard() }
        rows[2].forEach { addKey(third, it, 1f) }
        addAction(third, "⌫", 1.35f) { backspace() }
        root.addView(third)
        addBottom()
    }

    private fun addSymbols() {
        addKeyRow(symbolRows[0]); addKeyRow(symbolRows[1]); addKeyRow(symbolRows[2])
        addBottom()
    }

    private fun addKeyRow(keys: List<String>) {
        val r = row(keyHeight())
        keys.forEach { addKey(r, it, 1f) }
        root.addView(r)
    }

    private fun addBottom() {
        val r = row(keyHeight())
        addAction(r, if (symbols) "ABC" else "?123", 1.05f) { symbols = !symbols; buildKeyboard() }
        addKey(r, ",", .72f)
        addAction(r, "😊", .9f) { showPicker(it, emojis, false) }
        addAction(r, "✦", .82f) { showPicker(it, stickers, true) }
        addAction(r, "Espacio", 3.2f) { acceptSuggestion(" ") }
        addKey(r, ".", .72f)
        addAction(r, "↵", 1.25f) { enter() }
        root.addView(r)
    }

    private fun addKey(parent: LinearLayout, raw: String, weight: Float) {
        val shown = if (!symbols && upper && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
        val v = TextView(this).apply {
            text = shown; gravity = Gravity.CENTER; textSize = 20f; setTextColor(Color.rgb(28,28,30))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = bg(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, keyHeight(), weight).apply { setMargins(dp(2),dp(3),dp(2),dp(3)) }
            setOnClickListener { feedback(); typeKey(shown) }
        }
        if (!symbols) {
            v.setOnLongClickListener {
                accents[raw.lowercase()]?.let { list -> showPicker(v, list, false); true } ?: false
            }
        }
        parent.addView(v)
    }

    private fun addAction(parent: LinearLayout, label: String, weight: Float, action: (View) -> Unit) {
        parent.addView(TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = if (label.length > 3) 14f else 18f
            setTextColor(Color.rgb(28,28,30)); typeface = Typeface.DEFAULT_BOLD
            background = bg(Color.rgb(210,212,232))
            layoutParams = LinearLayout.LayoutParams(0, keyHeight(), weight).apply { setMargins(dp(2),dp(3),dp(2),dp(3)) }
            setOnClickListener { feedback(); action(this) }
        })
    }

    private fun typeKey(text: String) {
        if (text in listOf(".",",","!","?")) acceptSuggestion(text) else commit(text)
        if (!symbols && upper && text.length == 1 && text[0].isLetter()) {
            upper = false
            buildKeyboard()
        } else refreshSuggestions()
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        refreshSuggestions()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("",1) else ic.deleteSurroundingText(1,0)
        refreshSuggestions()
    }

    private fun enter() {
        if (KeyboardPrefs.autocorrectEnabled(this)) replaceWithBest()
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
        else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        upper = true
        refreshSuggestions()
    }

    private fun acceptSuggestion(trailing: String) {
        if (KeyboardPrefs.autocorrectEnabled(this)) replaceWithBest()
        currentInputConnection?.commitText(trailing, 1)
        if (trailing == " " || trailing == "." || trailing == "!" || trailing == "?") upper = true
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val strip = suggestions ?: return
        strip.removeAllViews()
        if (symbols || !KeyboardPrefs.autocorrectEnabled(this)) return
        val list = findSuggestions()
        list.take(3).forEach { word ->
            strip.addView(TextView(this).apply {
                text = word; gravity = Gravity.CENTER; textSize = 15f; setTextColor(Color.BLACK); background = bg(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }
                setOnClickListener { feedback(); replaceCurrentWord(word); refreshSuggestions() }
            })
        }
    }

    private fun findSuggestions(): List<String> {
        val word = currentWord().lowercase()
        if (word.length < 2) return emptyList()
        val n = normalize(word)
        val prefix = SpanishDictionary.words.filter { normalize(it).startsWith(n) }.sortedBy { it.length }.take(3).toMutableList()
        if (prefix.none { normalize(it) == n }) {
            SpanishDictionary.words.asSequence()
                .filter { abs(it.length - word.length) <= 2 }
                .map { it to distance(n, normalize(it)) }
                .filter { it.second <= 2 }
                .sortedWith(compareBy<Pair<String,Int>> { it.second }.thenBy { it.first.length })
                .firstOrNull()?.first?.let { if (it !in prefix) prefix.add(0,it) }
        }
        return prefix.distinct().take(3)
    }

    private fun replaceWithBest() {
        val old = currentWord()
        if (old.length < 2) return
        val best = findSuggestions().firstOrNull() ?: return
        val d = distance(normalize(old.lowercase()), normalize(best.lowercase()))
        if (d in 0..2 && !old.equals(best, true)) replaceCurrentWord(best)
    }

    private fun replaceCurrentWord(word: String) {
        val ic = currentInputConnection ?: return
        val old = currentWord()
        if (old.isEmpty()) return
        ic.deleteSurroundingText(old.length,0)
        ic.commitText(word,1)
    }

    private fun currentWord(): String {
        val text = currentInputConnection?.getTextBeforeCursor(80,0)?.toString().orEmpty()
        return text.takeLastWhile { it.isLetter() }
    }

    private fun normalize(s: String) = s.lowercase()
        .replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u").replace("ü","u").replace("ñ","n")

    private fun distance(a: String, b: String): Int {
        val c = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i - 1; c[0] = i
            for (j in 1..b.length) {
                val old = c[j]
                c[j] = minOf(c[j] + 1, c[j-1] + 1, prev + if (a[i-1] == b[j-1]) 0 else 1)
                prev = old
            }
        }
        return c[b.length]
    }

    private fun showPicker(anchor: View, items: List<String>, addSpace: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)); background = bg(Color.WHITE) }
        lateinit var popup: PopupWindow
        items.chunked(4).forEach { line ->
            val r = LinearLayout(this)
            line.forEach { item ->
                r.addView(TextView(this).apply {
                    text = item; gravity = Gravity.CENTER; textSize = if (item.length <= 2) 22f else 14f; setTextColor(Color.BLACK)
                    setPadding(dp(10),dp(9),dp(10),dp(9))
                    setOnClickListener { feedback(); commit(if (addSpace) "$item " else item); popup.dismiss() }
                })
            }
            box.addView(r)
        }
        popup = PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(12).toFloat(); isOutsideTouchable = true
        }
        popup.showAsDropDown(anchor, 0, -dp(260))
    }

    private fun feedback() {
        if (KeyboardPrefs.vibrationEnabled(this)) {
            try {
                val vib = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib.vibrate(10)
            } catch (_: Exception) {}
        }
        if (KeyboardPrefs.soundEnabled(this)) {
            try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK, 0.25f) } catch (_: Exception) {}
        }
    }

    private fun shouldCap(info: EditorInfo?): Boolean {
        if (info == null) return true
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val f = info.inputType and InputType.TYPE_MASK_FLAGS
        return f and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 || f and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 || f and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0
    }

    private fun openSettings() = startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun showKeyboardPicker() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    private fun row(height: Int) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height)) }
    private fun keyHeight(): Int = (48f * KeyboardPrefs.keyHeightPercent(this) / 100f).toInt()
    private fun bg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
