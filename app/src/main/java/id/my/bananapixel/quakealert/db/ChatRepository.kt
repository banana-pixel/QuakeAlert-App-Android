package id.my.bananapixel.quakealert.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository interface for Chat data operations.
 * Single responsibility: manage chat messages data.
 */
interface ChatRepository {
    /**
     * Observes chat messages as a Flow.
     * Stays populated even when offline (local DB as SSOT).
     */
    val chatMessages: Flow<List<ChatMessage>>
    
    /**
     * Saves/inserts chat messages to local DB.
     */
    suspend fun saveChatMessages(messages: List<ChatMessage>)
    
    /**
     * Get recent messages (limit 50).
     */
    val recentMessages: Flow<List<ChatMessage>>
    
    /**
     * Clear old messages before a given timestamp.
     */
    suspend fun pruneOldMessages(thresholdTimestamp: Long)
}

/**
 * Default implementation of ChatRepository.
 */
class ChatRepositoryImpl(
    private val chatDao: ChatMessageDao
) : ChatRepository {

    override val chatMessages: Flow<List<ChatMessage>> = chatDao.getAll()

    override val recentMessages: Flow<List<ChatMessage>> = chatDao.getRecent()

    override suspend fun saveChatMessages(messages: List<ChatMessage>) {
        withContext(Dispatchers.IO) {
            chatDao.insertAll(messages) // Persists to disk
        }
    }

    override suspend fun pruneOldMessages(thresholdTimestamp: Long) {
        withContext(Dispatchers.IO) {
            chatDao.pruneOldMessages(thresholdTimestamp)
        }
    }
}
