package id.my.bananapixel.quakealert.msg

import kotlinx.serialization.Serializable

/** Socket.IO payload for chat (send_message / receive_message / chat_history). */
@Serializable
data class ChatMessagePayload(
    val senderId: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)
