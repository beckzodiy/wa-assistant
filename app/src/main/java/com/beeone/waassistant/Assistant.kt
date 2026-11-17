package com.beeone.waassistant

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.launch

/** Coordinates AI calls, our pop-up notification and sending through WhatsApp. */
object Assistant {
    private const val TAG = "Assistant"

    fun requestDraft(context: Context, chat: Chat) {
        val app = context.applicationContext
        val gen = ++chat.generation
        chat.status = Chat.Status.DRAFTING
        chat.error = null
        chat.showTranslation = false
        chat.draft = ""
        chat.draftTranslation = ""
        chat.messageTranslation = ""
        Popup.show(app, chat, alert = true)

        App.scope.launch {
            try {
                val r = ClaudeClient.draftReply(Prefs(app), chat)
                if (gen != chat.generation) return@launch   // a newer message arrived
                if (r.customerLanguage.isNotBlank()) chat.customerLanguage = r.customerLanguage
                chat.messageTranslation = r.messageTranslation
                chat.draft = r.draft
                chat.draftTranslation = r.draftTranslation
                chat.status = Chat.Status.READY
            } catch (e: Exception) {
                if (gen != chat.generation) return@launch
                chat.status = Chat.Status.ERROR
                chat.error = e.message ?: "Error"
            }
            Popup.show(app, chat, alert = false)
        }
    }

    /** Owner typed their own reply: translate it into the customer's language, then wait for Send. */
    fun translateOwnReply(context: Context, chat: Chat, myText: String, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val gen = ++chat.generation
        chat.status = Chat.Status.TRANSLATING
        chat.error = null
        chat.showTranslation = false
        Popup.show(app, chat, alert = false)
        App.scope.launch {
            try {
                val out = ClaudeClient.translateMyReply(Prefs(app), chat, myText)
                if (gen != chat.generation) return@launch
                chat.draft = out
                chat.draftTranslation = myText
                chat.status = Chat.Status.READY
            } catch (e: Exception) {
                if (gen != chat.generation) return@launch
                chat.draft = myText
                chat.draftTranslation = ""
                chat.status = Chat.Status.ERROR
                chat.error = "Translation failed (${e.message}). Your original text is ready to send."
            }
            Popup.show(app, chat, alert = false)
            onDone?.invoke()
        }
    }

    /**
     * Sends [text] through WhatsApp's own notification reply action.
     * Called ONLY from a user tap (Send button). Returns an error message or null on success.
     */
    fun send(context: Context, chat: Chat, text: String): String? {
        val body = text.trim()
        if (body.isEmpty()) return "Nothing to send"
        val action = chat.replyAction ?: return "WhatsApp reply is no longer available. Open WhatsApp to reply."
        val inputs = action.remoteInputs ?: return "WhatsApp reply is no longer available."
        return try {
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val results = Bundle()
            for (ri in inputs) results.putCharSequence(ri.resultKey, body)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            }
            action.actionIntent.send(context, 0, intent)

            synchronized(chat) { chat.history.add(ChatLine(true, body, System.currentTimeMillis())) }
            chat.generation++
            chat.status = Chat.Status.SENT
            chat.draft = body
            Popup.show(context, chat, alert = false)
            Popup.cancelLater(context, chat)
            null
        } catch (e: PendingIntent.CanceledException) {
            chat.replyAction = null
            "WhatsApp reply expired. Open WhatsApp to reply."
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
            "Could not send: ${e.message}"
        }
    }

    fun toggleTranslation(context: Context, chat: Chat) {
        val app = context.applicationContext
        chat.showTranslation = !chat.showTranslation
        if (chat.showTranslation && chat.messageTranslation.isBlank() && chat.customerMessage.isNotBlank()) {
            // No cached translation (e.g. model skipped it): fetch it now.
            Popup.show(app, chat, alert = false)
            App.scope.launch {
                try {
                    val p = Prefs(app)
                    chat.messageTranslation = ClaudeClient.translateForMe(p, chat.customerMessage)
                    if (chat.draft.isNotBlank() && chat.draftTranslation.isBlank()) {
                        chat.draftTranslation = ClaudeClient.translateForMe(p, chat.draft)
                    }
                } catch (e: Exception) {
                    chat.messageTranslation = "(translation failed: ${e.message})"
                }
                Popup.show(app, chat, alert = false)
            }
        } else {
            Popup.show(app, chat, alert = false)
        }
    }

    /** Builds a demo chat for the "Test" button. It has no WhatsApp reply action, so Send can't send. */
    fun runTest(context: Context) {
        val chat = ChatStore.getOrCreate("test|demo")
        chat.name = "Test customer"
        chat.replyAction = null
        synchronized(chat) { chat.history.clear() }
        val msg = "مرحبا، هل عندكم حفاضات مقاس 4؟ وكم السعر؟"
        synchronized(chat) { chat.history.add(ChatLine(false, msg, System.currentTimeMillis())) }
        chat.customerMessage = msg
        requestDraft(context, chat)
    }
}
