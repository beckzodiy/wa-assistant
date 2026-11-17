package com.beeone.waassistant

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens when you tap the pop-up: edit the draft or write your own, translate, then Send. */
class EditActivity : AppCompatActivity() {
    private var chat: Chat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        bind()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind()
    }

    private fun bind() {
        val key = intent.getStringExtra(ActionReceiver.EXTRA_KEY)
        val c = key?.let { ChatStore.get(it) }
        if (c == null) {
            Toast.makeText(this, "This chat expired. Please reply in WhatsApp.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        chat = c
        val reply = findViewById<EditText>(R.id.reply)
        val info = findViewById<TextView>(R.id.info)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnTr = findViewById<Button>(R.id.btnTranslate)
        findViewById<TextView>(R.id.title).text = c.name
        findViewById<TextView>(R.id.customerMsg).text = c.customerMessage
        findViewById<TextView>(R.id.customerMsgTr).text =
            if (c.messageTranslation.isNotBlank()) c.messageTranslation else ""
        reply.setText(c.draft)
        reply.setSelection(reply.text.length)
        val lang = c.customerLanguage.ifBlank { "customer's language" }
        btnTr.text = "Translate to $lang"
        info.text = when (c.status) {
            Chat.Status.DRAFTING -> "AI is still writing… you can type your own reply."
            Chat.Status.ERROR -> c.error ?: ""
            else -> ""
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }

        btnTr.setOnClickListener {
            val text = reply.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            btnTr.isEnabled = false; btnSend.isEnabled = false
            info.text = "Translating…"
            lifecycleScope.launch {
                try {
                    val out = withContext(Dispatchers.IO) {
                        ClaudeClient.translateMyReply(Prefs(this@EditActivity), c, text)
                    }
                    reply.setText(out)
                    reply.setSelection(reply.text.length)
                    info.text = "Original: $text"
                } catch (e: Exception) {
                    info.text = "Translation failed: ${e.message}"
                } finally {
                    btnTr.isEnabled = true; btnSend.isEnabled = true
                }
            }
        }

        btnSend.setOnClickListener {
            val text = reply.text.toString()
            val err = Assistant.send(this, c, text)
            if (err == null) {
                Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                info.text = err
            }
        }
    }
}
