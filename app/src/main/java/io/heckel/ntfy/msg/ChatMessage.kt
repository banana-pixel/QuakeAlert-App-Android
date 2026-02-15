package io.heckel.ntfy.msg

import androidx.annotation.Keep

@Keep
data class ChatMessage(
    val senderId: String,
    val message: String,
    val timestamp: Long // No default value, force it to come from server
)
