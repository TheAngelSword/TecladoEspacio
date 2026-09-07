package com.tecladoespacio.app

import android.content.BroadcastReceiver
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class TecladoImeService : InputMethodService() {
    private enum class Shift { OFF, ONCE, LOCK }
    private enum class Mode { LETTERS, SYMBOLS, EMOJI, STICKERS }

    private var shift = Shift.OFF
    private var mode = Mode.LETTERS
    private lateinit var root: LinearLayout
    private var suggestions: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

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
        "a" to listOf("á","à","ä","â","ã"),
        "e" to listOf("é","è","ë","ê"),
        "i" to listOf("í","ì","ï","î"),
        "o" to listOf("ó","ò","ö","ô","õ"),
        "u" to listOf("ú","ù","ü","û"),
        "n" to listOf("ñ"),
        "?" to listOf("¿"),
        "!" to listOf("¡")
    )

    private val emojiCategories = listOf(
        "Caras" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥸","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🫣","🤭","🫢","🫡","🤫","🫠","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪"),
        "Gestos" to listOf("👍","👎","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉","👆","👇","☝️","🖐️","✋","🤚","🖖","👋","🤝","👏","🙌","🫶","🙏","✍️","💪","🦾","🫵"),
        "Corazones" to listOf("❤️","🩷","🧡","💛","💚","💙","🩵","💜","🤎","🖤","🩶","🤍","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","♥️"),
        "Objetos" to listOf("🔥","✨","⭐","🌟","💫","⚡","💥","🎉","🎊","🎁","🎈","🎂","🏆","🥇","⚽","🏀","🎮","🎧","🎤","📱","💻","⌚","📷","💡","🔑","💰","💳","🚗","✈️","🏠","☕","🍺","🍕","🌹","🌻","🌈","☀️","🌙")
    )

    private val stickers = listOf(
        "¡Excelente!","¡Felicidades!","¡Gracias!","¡Buen trabajo!","¡Ánimo!","¡Listo!","¡Vamos!","¡Perfecto!",
        "Buenos días ☀️","Buenas noches 🌙","Te quiero ❤️","Nos vemos 👋","Voy en camino 🚗","Ya llegué ✅","❤️🔥","🎉🥳"
    )

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_VOICE_RESULT -> {
                    val text = intent.getStringExtra(EXTRA_VOICE_TEXT).orEmpty()
                    if (text.isNotBlank()) handler.postDelayed({ currentInputConnection?.commitText(text, 1) }, 250)
                }
                ACTION_GIF_SELECTED -> {
                    val uri = intent.getStringExtra(EXTRA_GIF_URI)?.let(Uri::parse)
                    if (uri != null) handler.postDelayed({ commitGif(uri) }, 350)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_VOICE_RESULT)
            addAction(ACTION_GIF_SELECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(resultReceiver, filter)
    }

    override fun onDestroy() {
        try { unregisterReceiver(resultReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }

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
        mode = Mode.LETTERS
        shift = if (shouldCap(info)) Shift.ONCE else Shift.OFF
        if (::root.isInitialized) buildKeyboard()
    }

    private fun buildKeyboard() {
        root.removeAllViews()
        addToolbar()
        when (mode) {
            Mode.LETTERS -> buildLetters()
            Mode.SYMBOLS -> buildSymbols()
            Mode.EMOJI -> buildEmojiPanel()
            Mode.STICKERS -> buildStickerPanel()
        }
    }

    private fun addToolbar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }
        listOf("😊","GIF","✦","⚙","🎤").forEach { item ->
            bar.addView(TextView(this).apply {
                text = item
                gravity = Gravity.CENTER
                textSize = if (item == "GIF") 14f else 19f
                setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    feedback()
                    when (item) {
                        "😊" -> { mode = if (mode == Mode.EMOJI) Mode.LETTERS else Mode.EMOJI; buildKeyboard() }
                        "GIF" -> launchGifPicker()
                        "✦" -> { mode = if (mode == Mode.STICKERS) Mode.LETTERS else Mode.STICKERS; buildKeyboard() }
                        "⚙" -> startActivity(Intent(this@TecladoImeService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "🎤" -> launchVoiceRecognition()
                    }
                }
            })
        }
        root.addView(bar)
    }

    private fun buildLetters() {
        addSuggestionStrip()
        addRow(rows[0])
        addRow(rows[1])
        val third = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        third.addView(key(if (shift == Shift.LOCK) "⇪" else "⇧", 1.35f, action = true) {
            shift = when (shift) { Shift.OFF -> Shift.ONCE; Shift.ONCE -> Shift.LOCK; Shift.LOCK -> Shift.OFF }
            buildKeyboard()
        })
        rows[2].forEach { k -> third.addView(key(display(k), 1f, longOptions = accents[k]) { commit(display(k)) }) }
        third.addView(key("⌫", 1.35f, action = true) { backspace() })
        root.addView(third)
        addBottomRow("?123") { mode = Mode.SYMBOLS; buildKeyboard() }
        updateSuggestions()
    }

    private fun buildSymbols() {
        addRow(symbolRows[0], false)
        addRow(symbolRows[1], false)
        val third = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        symbolRows[2].forEach { k -> third.addView(key(k, 1f, longOptions = accents[k]) { punctuation(k) }) }
        third.addView(key("⌫", 1.35f, action = true) { backspace() })
        root.addView(third)
        addBottomRow("ABC") { mode = Mode.LETTERS; buildKeyboard() }
    }

    private fun addRow(keys: List<String>, letters: Boolean = true) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        keys.forEach { raw ->
            val shown = if (letters) display(raw) else raw
            row.addView(key(shown, 1f, longOptions = if (letters) accents[raw] else accents[raw]) {
                if (letters) commit(shown) else punctuation(shown)
            })
        }
        root.addView(row)
    }

    private fun addSuggestionStrip() {
        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38))
        }
        root.addView(suggestions)
    }

    private fun addBottomRow(modeText: String, modeClick: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(key(modeText, 1.15f, action = true, click = modeClick))
        row.addView(key(",", 0.82f) { punctuation(",") })
        row.addView(key("😊", 0.95f, action = true) { mode = Mode.EMOJI; buildKeyboard() })
        row.addView(key("Espacio", 4.65f) { punctuation(" ") })
        row.addView(key("↵", 1.55f, action = true) { enter() })
        root.addView(row)
    }

    private fun buildEmojiPanel() {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(265))
            isFillViewport = false
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(6)) }
        emojiCategories.forEach { (name, items) ->
            box.addView(TextView(this).apply { text = name; textSize = 13f; setTextColor(Color.DKGRAY); setPadding(dp(6), dp(5), 0, dp(3)); typeface = Typeface.DEFAULT_BOLD })
            items.chunked(7).forEach { chunk ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chunk.forEach { emoji ->
                    row.addView(TextView(this).apply {
                        text = emoji
                        gravity = Gravity.CENTER
                        textSize = 25f
                        background = bg(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                        setOnClickListener { feedback(); currentInputConnection?.commitText(emoji, 1) }
                    })
                }
                repeat(7 - chunk.size) { row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f) }) }
                box.addView(row)
            }
        }
        scroll.addView(box)
        root.addView(scroll)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(key("ABC", 1.4f, action = true) { mode = Mode.LETTERS; buildKeyboard() })
        bottom.addView(key("Espacio", 4.5f) { currentInputConnection?.commitText(" ", 1) })
        bottom.addView(key("⌫", 1.4f, action = true) { backspace() })
        root.addView(bottom)
    }

    private fun buildStickerPanel() {
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(265)) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        stickers.chunked(2).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { sticker ->
                row.addView(TextView(this).apply {
                    text = sticker
                    gravity = Gravity.CENTER
                    textSize = 15f
                    setTextColor(Color.BLACK)
                    background = bg(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                    setOnClickListener { feedback(); currentInputConnection?.commitText("$sticker ", 1) }
                })
            }
            if (chunk.size == 1) row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f) })
            box.addView(row)
        }
        scroll.addView(box)
        root.addView(scroll)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(key("ABC", 1.4f, action = true) { mode = Mode.LETTERS; buildKeyboard() })
        bottom.addView(key("Espacio", 4.5f) { currentInputConnection?.commitText(" ", 1) })
        bottom.addView(key("⌫", 1.4f, action = true) { backspace() })
        root.addView(bottom)
    }

    private fun key(
        text: String,
        weight: Float,
        action: Boolean = false,
        longOptions: List<String>? = null,
        click: () -> Unit
    ): View = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = if (text.length > 2) 15f else 20f
        setTextColor(Color.rgb(28,28,30))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = bg(if (action) Color.rgb(212,214,240) else Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(0, keyHeight(), weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        setOnClickListener { feedback(); click() }
        if (!longOptions.isNullOrEmpty()) {
            setOnLongClickListener { showVariants(this, longOptions); true }
        }
    }

    private fun showVariants(anchor: View, options: List<String>) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)); background = bg(Color.WHITE) }
        val popup = android.widget.PopupWindow(line, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        options.forEach { raw ->
            val value = if (shift == Shift.OFF) raw else raw.uppercase()
            line.addView(TextView(this).apply {
                text = value; gravity = Gravity.CENTER; textSize = 22f; setTextColor(Color.BLACK); setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { feedback(); commit(value); popup.dismiss() }
            })
        }
        popup.elevation = dp(8).toFloat()
        popup.isOutsideTouchable = true
        popup.showAsDropDown(anchor, 0, -keyHeight() * 2)
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (text.length == 1 && text[0].isLetter() && shift == Shift.ONCE) {
            shift = Shift.OFF
            buildKeyboard()
        } else updateSuggestions()
    }

    private fun punctuation(value: String) {
        if (KeyboardPrefs.autocorrectEnabled(this) && (value == " " || value in listOf(".",",","!","?",";",":"))) applyCorrection()
        currentInputConnection?.commitText(value, 1)
        if (value in listOf(".","!","?")) {
            shift = Shift.ONCE
            if (mode == Mode.SYMBOLS) mode = Mode.LETTERS
            buildKeyboard()
        } else if (value == " ") {
            updateAutoCapitalizationAfterSpace()
        } else updateSuggestions()
    }

    private fun updateAutoCapitalizationAfterSpace() {
        val before = currentInputConnection?.getTextBeforeCursor(4, 0)?.toString().orEmpty().trimEnd()
        shift = if (before.isEmpty() || before.lastOrNull() in listOf('.', '!', '?')) Shift.ONCE else Shift.OFF
        if (mode == Mode.LETTERS) buildKeyboard() else updateSuggestions()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        try { ic.deleteSurroundingTextInCodePoints(1, 0) } catch (_: Exception) { ic.deleteSurroundingText(1, 0) }
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
        val strip = suggestions ?: return
        strip.removeAllViews()
        if (mode != Mode.LETTERS || !KeyboardPrefs.autocorrectEnabled(this)) return
        val word = currentWord().lowercase()
        if (word.length < 2) return
        suggest(word).take(3).forEach { s ->
            strip.addView(TextView(this).apply {
                text = s; gravity = Gravity.CENTER; textSize = 15f; setTextColor(Color.BLACK); background = bg(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
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
        val word = currentWord()
        if (word.length < 2) return
        val best = suggest(word.lowercase()).firstOrNull() ?: return
        val d = distance(norm(word.lowercase()), norm(best))
        if (d in 1..2 || (d == 0 && word.lowercase() != best.lowercase())) replaceWord(best)
    }

    private fun replaceWord(newValue: String) {
        val old = currentWord()
        if (old.isEmpty()) return
        currentInputConnection?.deleteSurroundingText(old.length, 0)
        currentInputConnection?.commitText(newValue, 1)
    }

    private fun currentWord(): String {
        val text = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        return text.takeLastWhile { it.isLetter() || it in "áéíóúüñÁÉÍÓÚÜÑ" }
    }

    private fun launchGifPicker() {
        try {
            startActivity(Intent(this, GifPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "No pude abrir el selector de GIF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchVoiceRecognition() {
        try {
            startActivity(Intent(this, VoiceRecognitionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "No pude abrir el reconocimiento de voz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun commitGif(uri: Uri) {
        val ic = currentInputConnection ?: run { Toast.makeText(this, "Vuelve al campo de texto e intenta otra vez", Toast.LENGTH_SHORT).show(); return }
        val editor = currentInputEditorInfo ?: return
        try {
            grantUriPermission(editor.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ic.finishComposingText()
            val info = InputContentInfo(uri, ClipDescription("GIF", arrayOf("image/gif")), null)
            val accepted = ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            if (!accepted) Toast.makeText(this, "Esta aplicación no acepta GIF desde teclados", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo insertar el GIF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun feedback() {
        if (KeyboardPrefs.vibrationEnabled(this)) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) { }
        }
        if (KeyboardPrefs.soundEnabled(this)) {
            try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK, 0.35f) } catch (_: Exception) { }
        }
    }

    private fun shouldCap(info: EditorInfo?): Boolean {
        if (info == null) return true
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val flags = info.inputType and InputType.TYPE_MASK_FLAGS
        return (flags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0 || (flags and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0
    }

    private fun display(value: String) = if (shift == Shift.OFF) value else value.uppercase()
    private fun bg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat() }
    private fun keyHeight() = dp((48 * KeyboardPrefs.keyHeightPercent(this) / 100f).toInt())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun norm(s: String) = s.lowercase().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u').replace('ü','u').replace('ñ','n')

    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = minOf(d[i-1][j] + 1, d[i][j-1] + 1, d[i-1][j-1] + if (a[i-1] == b[j-1]) 0 else 1)
        return d[a.length][b.length]
    }

    companion object {
        const val ACTION_VOICE_RESULT = "com.tecladoespacio.app.VOICE_RESULT"
        const val EXTRA_VOICE_TEXT = "voice_text"
        const val ACTION_GIF_SELECTED = "com.tecladoespacio.app.GIF_SELECTED"
        const val EXTRA_GIF_URI = "gif_uri"
    }
}
