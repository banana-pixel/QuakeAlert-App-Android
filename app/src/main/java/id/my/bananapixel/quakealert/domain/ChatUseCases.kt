package id.my.bananapixel.quakealert.domain

import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.db.ChatRepository
import id.my.bananapixel.quakealert.util.Log

/**
 * UseCase for persisting chat messages.
 */
class SaveChatMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messages: List<ChatMessage>) {
        Log.d(TAG, "Saving ${messages.size} chat messages...")
        chatRepository.saveChatMessages(messages)
        Log.d(TAG, "Chat messages saved")
    }

    companion object {
        private const val TAG = "SaveChatMessagesUseCase"
    }
}

/**
 * UseCase for pruning old chat messages.
 */
class PruneChatMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(thresholdTimestamp: Long) {
        Log.d(TAG, "Pruning chat messages older than $thresholdTimestamp...")
        chatRepository.pruneOldMessages(thresholdTimestamp)
        Log.d(TAG, "Chat messages pruned")
    }

    companion object {
        private const val TAG = "PruneChatMessagesUseCase"
    }
}
