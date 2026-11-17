package com.beeone.waassistant

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

/** Our heads-up pop-up: customer message + AI draft + Send / My reply / Translate. */
object Popup {
    const val KEY_MY_REPLY = "my_reply"

    fun show(context: Context, chat: Chat, alert: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return

        val prefs = Prefs(context)
        val tr = chat.showTranslation
        val msgShown = if (tr && chat.messageTranslation.isNotBlank()) chat.messageTranslation else chat.customerMessage

        val body = StringBuilder()
        body.append("💬 ").append(msgShown)
        if (tr && chat.messageTranslation.isNotBlank()) body.append("\n(original: ").append(chat.customerMessage).append(")")
        body.append("\n\n")
        when (chat.status) {
            Chat.Status.DRAFTING -> body.append("✍️ Writing a reply…")
            Chat.Status.TRANSLATING -> body.append("🌐 Translating your reply…")
            Chat.Status.SENT -> body.append("✅ Sent: ").append(chat.draft)
            Chat.Status.ERROR -> {
                body.append("⚠️ ").append(chat.error ?: "Error")
                if (chat.draft.isNotBlank()) body.append("\n\n✍️ ").append(chat.draft)
            }
            Chat.Status.READY, Chat.Status.IDLE -> {
                if (tr && chat.draftTranslation.isNotBlank()) {
                    body.append("✍️ Reply (").append(prefs.myLanguage).append("): ").append(chat.draftTranslation)
                    body.append("\n➡️ Will send: ").append(chat.draft)
                } else {
                    body.append("✍️ ").append(chat.draft)
                    if (chat.customerLanguage.isNotBlank()) body.append("   [").append(chat.customerLanguage).append("]")
                }
            }
        }
        if (chat.replyAction == null && chat.status != Chat.Status.SENT) {
            body.append("\n\n(Test only: no WhatsApp chat to reply to)")
        }

        val b = NotificationCompat.Builder(context, App.CHANNEL_DRAFTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF128C7E.toInt())
            .setContentTitle(chat.name)
            .setContentText(body.lines().firstOrNull { it.isNotBlank() } ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.toString()))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(false)
            .setContentIntent(editIntent(context, chat))

        val canSend = chat.status == Chat.Status.READY || (chat.status == Chat.Status.ERROR && chat.draft.isNotBlank())
        if (chat.status != Chat.Status.SENT) {
            if (canSend) {
                b.addAction(0, "Send", broadcast(context, chat, ActionReceiver.ACTION_SEND, 1))
            }
            val input = RemoteInput.Builder(KEY_MY_REPLY)
                .setLabel("My reply (any language)")
                .build()
            val myReply = NotificationCompat.Action.Builder(
                0, "My reply", broadcast(context, chat, ActionReceiver.ACTION_MY_REPLY, 2, mutable = true)
            ).addRemoteInput(input).setAllowGeneratedReplies(false).build()
            b.addAction(myReply)
            if (chat.status == Chat.Status.READY || chat.status == Chat.Status.DRAFTING) {
                b.addAction(0, if (tr) "Original" else "Translate",
                    broadcast(context, chat, ActionReceiver.ACTION_TRANSLATE, 3))
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(chat.notificationId, b.build())
        } catch (_: SecurityException) { }
    }

    fun cancel(context: Context, chat: Chat) {
        NotificationManagerCompat.from(context).cancel(chat.notificationId)
    }

    fun cancelLater(context: Context, chat: Chat) {
        val app = context.applicationContext
        val gen = chat.generation
        Handler(Looper.getMainLooper()).postDelayed({
            if (chat.generation == gen && chat.status == Chat.Status.SENT) cancel(app, chat)
        }, 2500)
    }

    private fun broadcast(context: Context, chat: Chat, action: String, code: Int, mutable: Boolean = false): PendingIntent {
        val i = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(ActionReceiver.EXTRA_KEY, chat.key)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, chat.notificationId * 8 + code, i, flags)
    }

    private fun editIntent(context: Context, chat: Chat): PendingIntent {
        val i = Intent(context, EditActivity::class.java)
            .putExtra(ActionReceiver.EXTRA_KEY, chat.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, chat.notificationId * 8 + 4, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
