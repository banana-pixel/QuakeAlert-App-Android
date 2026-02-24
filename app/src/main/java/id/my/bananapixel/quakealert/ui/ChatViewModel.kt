package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.db.ChatRepository
import id.my.bananapixel.quakealert.domain.SaveChatMessagesUseCase
import id.my.bananapixel.quakealert.domain.PruneChatMessagesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel for chat messages. Manages chat data and WebSocket communication.
 * Single responsibility: manage chat message persistence and observation.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    // Injected use cases for business logic
    private val saveChatMessagesUseCase = SaveChatMessagesUseCase(chatRepository)
    private val pruneChatMessagesUseCase = PruneChatMessagesUseCase(chatRepository)

    // UI observes chat messages directly
    val chatMessages: Flow<List<ChatMessage>> = chatRepository.chatMessages

    /**
     * Save chat messages to local database.
     */
    fun saveChatMessages(messages: List<ChatMessage>) = viewModelScope.launch {
        saveChatMessagesUseCase(messages)
    }

    /**
     * Prune old chat messages before given timestamp.
     */
    fun pruneOldMessages(thresholdTimestamp: Long) = viewModelScope.launch {
        pruneChatMessagesUseCase(thresholdTimestamp)
    }
}
