package com.tecladoespacio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class GifPickerActivity : Activity() {
    companion object { private const val REQ_GIF = 7001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/gif"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQ_GIF)
        } catch (_: Exception) {
            Toast.makeText(this, "No encontré una aplicación para elegir GIF", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Android API, kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_GIF && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                sendBroadcast(
                    Intent(TecladoImeService.ACTION_GIF_SELECTED)
                        .setPackage(packageName)
                        .putExtra(TecladoImeService.EXTRA_GIF_URI, uri.toString())
                )
            }
        }
        finish()
    }
}
