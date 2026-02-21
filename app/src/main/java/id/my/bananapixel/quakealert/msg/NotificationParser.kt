package id.my.bananapixel.quakealert.msg

import id.my.bananapixel.quakealert.db.Action
import id.my.bananapixel.quakealert.db.Attachment
import id.my.bananapixel.quakealert.db.Icon
import id.my.bananapixel.quakealert.db.Notification
import id.my.bananapixel.quakealert.util.deriveNotificationId
import id.my.bananapixel.quakealert.util.joinTags
import id.my.bananapixel.quakealert.util.toPriority
import id.my.bananapixel.quakealert.util.Log
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val notificationJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

class NotificationParser {

    fun parse(s: String, subscriptionId: Long = 0, baseUrl: String = ""): Notification? {
        val notificationWithTopic = parseWithTopic(s, subscriptionId = subscriptionId, baseUrl = baseUrl)
        return notificationWithTopic?.notification
    }

    fun parseWithTopic(s: String, subscriptionId: Long = 0, baseUrl: String = ""): NotificationWithTopic? {
        val message = notificationJson.decodeFromString(Message.serializer(), s)
        val validEvent = message.event == ApiService.EVENT_MESSAGE ||
                message.event == ApiService.EVENT_MESSAGE_DELETE ||
                message.event == ApiService.EVENT_MESSAGE_CLEAR
        if (!validEvent) {
            return null
        }
        if (hasTag(message.tags, TAG_EARTHQUAKE)) {
            val geoTag = message.tags?.firstOrNull { tag -> tag.startsWith(TAG_GEO_PREFIX, ignoreCase = true) }
            val coordinates = geoTag?.substringAfter(TAG_GEO_PREFIX)?.split(",")?.map { it.trim() }
            if (coordinates != null && coordinates.size == 2) {
                val latitude = coordinates[0]
                val longitude = coordinates[1]
                Log.d(TAG, "Detected Quake from TAGS: $latitude, $longitude")
            }
        }
        val attachment = if (message.attachment?.url != null) {
            Attachment(
                name = message.attachment.name,
                type = message.attachment.type,
                size = message.attachment.size,
                expires = message.attachment.expires,
                url = message.attachment.url,
            )
        } else null
        val actions = message.actions?.map { a ->
            Action(
                id = a.id,
                action = a.action,
                label = a.label,
                clear = a.clear,
                url = a.url,
                method = a.method,
                headers = a.headers,
                body = a.body,
                intent = a.intent,
                extras = a.extras,
                value = a.value,
                progress = null,
                error = null
            )
        }
        val icon: Icon? = if (message.icon != null && message.icon != "") Icon(url = message.icon) else null
        val sequenceId = message.sequenceId ?: message.id // Default to id if sequenceId not provided
        val topic = message.topic
        val notification = Notification(
            id = message.id,
            subscriptionId = subscriptionId,
            timestamp = message.time,
            sequenceId = sequenceId,
            title = message.title ?: "",
            message = message.message ?: "",
            contentType = message.contentType ?: "",
            encoding = message.encoding ?: "",
            priority = toPriority(message.priority),
            tags = joinTags(message.tags),
            click = message.click ?: "",
            icon = icon,
            actions = actions,
            attachment = attachment,
            notificationId = deriveNotificationId(baseUrl, topic, sequenceId),
            deleted = false,
            event = message.event
        )
        return NotificationWithTopic(topic, notification)
    }

    private fun hasTag(tags: List<String>?, target: String): Boolean {
        return tags?.any { tag -> tag.equals(target, ignoreCase = true) } == true
    }

    /**
     * Parse JSON array to Action list. The indirection via MessageAction is probably
     * not necessary, but for "good form".
     */
    fun parseActions(s: String?): List<Action>? {
        if (s.isNullOrBlank()) return null
        val messageActions = try {
            notificationJson.decodeFromString(ListSerializer(MessageAction.serializer()), s)
        } catch (_: Exception) {
            return null
        }
        return messageActions?.map { a ->
            Action(
                id = a.id,
                action = a.action,
                label = a.label,
                clear = a.clear,
                url = a.url,
                method = a.method,
                headers = a.headers,
                body = a.body,
                intent = a.intent,
                extras = a.extras,
                value = a.value,
                progress = null,
                error = null
            )
        }
    }

    data class NotificationWithTopic(val topic: String, val notification: Notification)

    companion object {
        private const val TAG = "QuakeAlert"
        private const val TAG_EARTHQUAKE = "earthquake"
        private const val TAG_GEO_PREFIX = "geo:"
    }
}
