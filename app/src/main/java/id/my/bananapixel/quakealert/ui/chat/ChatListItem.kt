package id.my.bananapixel.quakealert.ui.chat

import id.my.bananapixel.quakealert.db.ChatMessage

/**
 * Sealed class representing items in the chat list (messages or date headers)
 */
sealed class ChatListItem {
    data class MessageItem(val message: ChatMessage) : ChatListItem()
    data class DateHeaderItem(val date: String, val timestamp: Long) : ChatListItem()
}
