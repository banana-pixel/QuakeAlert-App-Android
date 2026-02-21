package id.my.bananapixel.quakealert.msg

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* This annotation ensures that proguard still works in production builds,
 * see https://stackoverflow.com/a/62753300/1440785 */
@Keep
@Serializable
data class Message(
    val id: String,
    val time: Long,
    @SerialName("sequence_id") val sequenceId: String? = null, // Sequence ID for updating notifications
    val event: String,
    val topic: String,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val headers: Map<String, String>? = null,
    val click: String? = null,
    val icon: String? = null,
    val actions: List<MessageAction>? = null,
    val title: String? = null,
    val message: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val encoding: String? = null,
    val attachment: MessageAttachment? = null,
)

@Keep
@Serializable
data class MessageAttachment(
    val name: String,
    val type: String? = null,
    val size: Long? = null,
    val expires: Long? = null,
    val url: String,
)

@Keep
@Serializable
data class MessageAction(
    val id: String,
    val action: String,
    val label: String, // "view", "broadcast", "http", or "copy"
    val clear: Boolean? = null, // clear notification after successful execution
    val url: String? = null, // used in "view" and "http" actions
    val method: String? = null, // used in "http" action, default is POST (!)
    val headers: Map<String, String>? = null, // used in "http" action
    val body: String? = null, // used in "http" action
    val intent: String? = null, // used in "broadcast" action
    val extras: Map<String, String>? = null, // used in "broadcast" action
    val value: String? = null, // used in "copy" action
)

const val MESSAGE_ENCODING_BASE64 = "base64"
