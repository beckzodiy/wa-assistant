package com.beeone.waassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput

/** Handles the buttons on our pop-up. Every send here is a direct result of the user's tap. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val chat = ChatStore.get(key)
        if (chat == null) {
            // Process was restarted: we no longer hold WhatsApp's reply action.
            Toast.makeText(context, "This chat expired. Please reply in WhatsApp.", Toast.LENGTH_LONG).show()
            context.getSystemService(android.app.NotificationManager::class.java)
                .cancel(key.hashCode() and 0x7fffffff)
            return
        }
        when (intent.action) {
            ACTION_SEND -> {
                val err = Assistant.send(context, chat, chat.draft)
                if (err != null) {
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    Popup.show(context, chat, alert = false)
                }
            }
            ACTION_MY_REPLY -> {
                val typed = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Popup.KEY_MY_REPLY)?.toString()?.trim()
                if (typed.isNullOrEmpty()) {
                    Popup.show(context, chat, alert = false)
                } else {
                    val pending = goAsync()
                    Assistant.translateOwnReply(context, chat, typed) { pending.finish() }
                }
            }
            ACTION_TRANSLATE -> Assistant.toggleTranslation(context, chat)
        }
    }

    companion object {
        const val ACTION_SEND = "com.beeone.waassistant.SEND"
        const val ACTION_MY_REPLY = "com.beeone.waassistant.MY_REPLY"
        const val ACTION_TRANSLATE = "com.beeone.waassistant.TRANSLATE"
        const val EXTRA_KEY = "chat_key"
    }
}
