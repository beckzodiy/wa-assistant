package com.beeone.waassistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Reads incoming WhatsApp Business notifications. It only READS them and remembers
 * WhatsApp's own reply action. Nothing is sent from here.
 */
class WaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle notification", e)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        val pkg = sbn.packageName
        val watched = pkg == PKG_BUSINESS || (prefs.includePersonalWhatsApp && pkg == PKG_PERSONAL)
        if (!watched) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val reply = findReplyAction(n) ?: return   // no reply button = not a chat message

        val extras = n.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style?.isGroupConversation == true && !prefs.includeGroups) return

        val title = style?.conversationTitle?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return
        val key = "$pkg|" + (n.shortcutId ?: sbn.tag ?: title)

        // Collect messages from the notification (WhatsApp includes unread history).
        val lines: List<ChatLine> = if (style != null && style.messages.isNotEmpty()) {
            val me = style.user.name?.toString()
            style.messages.mapNotNull { m ->
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return@mapNotNull null
                val sender = m.person?.name?.toString()
                val fromMe = m.person == null || (me != null && sender == me)
                val shown = if (style.isGroupConversation && !fromMe && sender != null) "$sender: $text" else text
                ChatLine(fromMe, shown, m.timestamp)
            }
        } else {
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (text.isEmpty()) emptyList() else listOf(ChatLine(false, text, sbn.postTime))
        }
        if (lines.isEmpty()) return

        val chat = ChatStore.getOrCreate(key)
        chat.name = title
        chat.sourcePackage = pkg
        chat.replyAction = reply   // always keep the freshest reply action

        mergeHistory(chat, lines)

        // Only act when the newest message is from the customer and is new.
        val last = lines.last()
        if (last.fromMe) return
        val trailingIncoming = lines.takeLastWhile { !it.fromMe }
        val signature = trailingIncoming.joinToString("\u0001") { "${it.time}:${it.text}" }
        if (signature == chat.lastHandledIncoming) return
        chat.lastHandledIncoming = signature

        chat.customerMessage = trailingIncoming.takeLast(5).joinToString("\n") { it.text }
        Assistant.requestDraft(this, chat)
    }

    private fun mergeHistory(chat: Chat, lines: List<ChatLine>) {
        synchronized(chat) {
            for (l in lines) {
                val dup = chat.history.any { it.time == l.time && it.text == l.text }
                if (!dup) chat.history.add(l)
            }
            chat.history.sortBy { it.time }
            while (chat.history.size > 30) chat.history.removeAt(0)
        }
    }

    private fun findReplyAction(n: Notification): Notification.Action? {
        fun ok(a: Notification.Action) =
            a.actionIntent != null && a.remoteInputs?.any { it.allowFreeFormInput } == true
        n.actions?.firstOrNull { ok(it) }?.let { return it }
        return Notification.WearableExtender(n).actions.firstOrNull { ok(it) }
    }

    companion object {
        private const val TAG = "WaListener"
        const val PKG_BUSINESS = "com.whatsapp.w4b"
        const val PKG_PERSONAL = "com.whatsapp"
        @Volatile var connected = false
    }
}
