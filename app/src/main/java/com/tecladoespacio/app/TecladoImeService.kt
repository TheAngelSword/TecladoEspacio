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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs

class TecladoImeService : InputMethodService() {
    private enum class Shift { OFF, ONCE, LOCK }
    private var shift = Shift.OFF
    private lateinit var root: LinearLayout
    private lateinit var suggestions: LinearLayout

    private val rows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l","ñ"),
        listOf("z","x","c","v","b","n","m")
    )
    private val emojis = listOf("😀","😂","😍","🥳","😎","😉","😊","😭","🙏","👍","👏","🔥","❤️","🎉","✨")
    private val stickers = listOf("¡Excelente!","¡Felicidades!","¡Gracias!","¡Buen trabajo!","¡Ánimo!","¡Listo!","❤️🔥","🎉🥳")

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
            setBackgroundColor(Color.rgb(228,228,230))
        }
        buildKeyboard()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shift = if (shouldCap(info)) Shift.ONCE else Shift.OFF
        if (::root.isInitialized) buildKeyboard()
    }

    private fun buildKeyboard() {
        root.removeAllViews()
        addToolbar()
        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38))
        }
        root.addView(suggestions)
        rows[0].let { addLetterRow(it) }
        rows[1].let { addLetterRow(it) }
        addShiftRow()
        addBottomRow()
        updateSuggestions()
    }

    private fun addToolbar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        }
        listOf("😊","GIF","✦","⚙","🎤").forEach { item ->
            val v = TextView(this).apply {
                text = item
                gravity = Gravity.CENTER
                textSize = if (item == "GIF") 13f else 18f
                setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    feedback()
                    when (item) {
                        "😊" -> showPopup(this, emojis)
                        "✦" -> showPopup(this, stickers)
                        "⚙" -> startActivity(Intent(this@TecladoImeService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            bar.addView(v)
        }
        root.addView(bar)
    }

    private fun addLetterRow(keys: List<String>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        keys.forEach { k -> row.addView(key(k, 1f, { commit(display(k)) })) }
        root.addView(row)
    }

    private fun addShiftRow() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(key(if (shift == Shift.LOCK) "⇪" else "⇧", 1.35f, {
            shift = when (shift) { Shift.OFF -> Shift.ONCE; Shift.ONCE -> Shift.LOCK; Shift.LOCK -> Shift.OFF }
            buildKeyboard()
        }, action = true))
        rows[2].forEach { k -> row.addView(key(k, 1f, { commit(display(k)) })) }
        row.addView(key("⌫", 1.35f, { backspace() }, action = true))
        root.addView(row)
    }

    private fun addBottomRow() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(key("?123", 1.1f, { commit("123") }, action = true))
        row.addView(key(",", .8f, { punctuation(",") }))
        row.addView(key("😊", .95f, { showPopup(root, emojis) }, action = true))
        row.addView(key("Espacio", 3.8f, { punctuation(" ") }))
        row.addView(key(".", .75f, { punctuation(".") }))
        row.addView(key("↵", 1.5f, { enter() }, action = true))
        root.addView(row)
    }

    private fun key(text: String, weight: Float, click: () -> Unit, action: Boolean = false): View {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (text.length > 2) 15f else 20f
            setTextColor(Color.rgb(28,28,30))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = bg(if (action) Color.rgb(212,214,240) else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp((48 * KeyboardPrefs.keyHeightPercent(this@TecladoImeService) / 100f).toInt()), weight).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            setOnClickListener { feedback(); click() }
        }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (text.length == 1 && text[0].isLetter() && shift == Shift.ONCE) {
            shift = Shift.OFF
            buildKeyboard()
        } else updateSuggestions()
    }

    private fun punctuation(p: String) {
        if (KeyboardPrefs.autocorrectEnabled(this)) applyCorrection()
        currentInputConnection?.commitText(p, 1)
        if (p == " " || p == "." || p == "!" || p == "?") {
            shift = Shift.ONCE
            buildKeyboard()
        } else updateSuggestions()
    }

    private fun backspace() {
        currentInputConnection?.deleteSurroundingText(1,0)
        updateSuggestions()
    }

    private fun enter() {
        if (KeyboardPrefs.autocorrectEnabled(this)) applyCorrection()
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
        else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun updateSuggestions() {
        if (!::suggestions.isInitialized) return
        suggestions.removeAllViews()
        if (!KeyboardPrefs.autocorrectEnabled(this)) return
        val word = currentWord().lowercase()
        if (word.length < 2) return
        val list = suggest(word).take(3)
        list.forEach { s ->
            suggestions.addView(TextView(this).apply {
                text = s
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(Color.BLACK)
                background = bg(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }
                setOnClickListener { feedback(); replaceWord(s); updateSuggestions() }
            })
        }
    }

    private fun suggest(word: String): List<String> {
        val n = norm(word)
        val prefix = SpanishDictionary.words.filter { norm(it).startsWith(n) }.sortedBy { it.length }.take(3).toMutableList()
        val close = SpanishDictionary.words.asSequence()
            .filter { abs(it.length - word.length) <= 2 }
            .map { it to distance(n, norm(it)) }
            .filter { it.second <= 2 }
            .sortedWith(compareBy<Pair<String,Int>> { it.second }.thenBy { it.first.length })
            .map { it.first }.firstOrNull()
        if (close != null && close !in prefix) prefix.add(0, close)
        return prefix.distinct()
    }

    private fun applyCorrection() {
        val w = currentWord()
        if (w.length < 2) return
        val best = suggest(w.lowercase()).firstOrNull() ?: return
        val d = distance(norm(w.lowercase()), norm(best))
        if (d in 1..2 || (d == 0 && w.lowercase() != best.lowercase())) replaceWord(best)
    }

    private fun replaceWord(new: String) {
        val old = currentWord()
        if (old.isEmpty()) return
        currentInputConnection?.deleteSurroundingText(old.length,0)
        currentInputConnection?.commitText(new,1)
    }

    private fun currentWord(): String {
        val t = currentInputConnection?.getTextBeforeCursor(60,0)?.toString().orEmpty()
        return t.takeLastWhile { it.isLetter() || it in "áéíóúüñÁÉÍÓÚÜÑ" }
    }

    private fun showPopup(anchor: View, items: List<String>) {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)); background = bg(Color.WHITE) }
        lateinit var pop: PopupWindow
        items.chunked(4).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { item ->
                row.addView(TextView(this).apply {
                    text = item; gravity = Gravity.CENTER; textSize = if (item.length <= 2) 22f else 15f
                    setPadding(dp(12),dp(10),dp(12),dp(10)); setTextColor(Color.BLACK)
                    setOnClickListener { feedback(); currentInputConnection?.commitText(item,1); pop.dismiss(); updateSuggestions() }
                })
            }
            outer.addView(row)
        }
        pop = PopupWindow(outer, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(10).toFloat(); isOutsideTouchable = true
        }
        pop.showAsDropDown(anchor, 0, -dp(220))
    }

    private fun feedback() {
        if (KeyboardPrefs.vibrationEnabled(this)) {
            try {
                val v = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                        else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
        }
        if (KeyboardPrefs.soundEnabled(this)) {
            try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK, 0.35f) } catch (_: Exception) {}
        }
    }

    private fun shouldCap(info: EditorInfo?): Boolean {
        if (info == null) return true
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val f = info.inputType and InputType.TYPE_MASK_FLAGS
        return (f and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0 || (f and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0
    }

    private fun display(s: String) = if (shift == Shift.OFF) s else s.uppercase()
    private fun bg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun norm(s: String) = s.lowercase().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u').replace('ü','u').replace('ñ','n')

    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = minOf(
            d[i-1][j] + 1, d[i][j-1] + 1, d[i-1][j-1] + if (a[i-1] == b[j-1]) 0 else 1
        )
        return d[a.length][b.length]
    }
}
