package com.tecladoespacio.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import java.util.Locale

class VoiceRecognitionActivity : Activity() {
    companion object { private const val REQ_VOICE = 7002 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para escribir")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            startActivityForResult(intent, REQ_VOICE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No hay un servicio de reconocimiento de voz instalado", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Android API, kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                sendBroadcast(
                    Intent(TecladoImeService.ACTION_VOICE_RESULT)
                        .setPackage(packageName)
                        .putExtra(TecladoImeService.EXTRA_VOICE_TEXT, text)
                )
            }
        }
        finish()
    }
}
