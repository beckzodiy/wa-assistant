package com.beeone.waassistant

import android.app.Notification
import java.util.concurrent.ConcurrentHashMap

data class ChatLine(val fromMe: Boolean, val text: String, val time: Long)

/** State of one WhatsApp conversation, kept in memory. */
class Chat(val key: String) {
    @Volatile var name: String = ""
    @Volatile var sourcePackage: String = ""
    /** WhatsApp's own "Reply" action from its latest notification. */
    @Volatile var replyAction: Notification.Action? = null
    val history = ArrayList<ChatLine>()          // guarded by `this`
    @Volatile var lastHandledIncoming: String? = null

    @Volatile var customerMessage: String = ""   // latest incoming message(s)
    @Volatile var customerLanguage: String = ""
    @Volatile var messageTranslation: String = ""
    @Volatile var draft: String = ""
    @Volatile var draftTranslation: String = ""
    @Volatile var status: Status = Status.IDLE
    @Volatile var error: String? = null
    @Volatile var showTranslation: Boolean = false
    /** Increments with each new request so stale API results are dropped. */
    @Volatile var generation: Int = 0

    /** Notification id for our pop-up. */
    val notificationId: Int get() = key.hashCode() and 0x7fffffff

    fun historySnapshot(): List<ChatLine> = synchronized(this) { history.toList() }

    enum class Status { IDLE, DRAFTING, READY, TRANSLATING, ERROR, SENT }
}

object ChatStore {
    private val chats = ConcurrentHashMap<String, Chat>()
    fun get(key: String): Chat? = chats[key]
    fun getOrCreate(key: String): Chat = chats.getOrPut(key) { Chat(key) }
}
