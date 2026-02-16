package id.my.bananapixel.quakealert.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quake_history")
data class QuakeData(
    @PrimaryKey val id: String,
    val magnitude: Double,
    val place: String,
    val time: Long,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val pga: String,
    val durasi: Int,
    val station_id: String,
    val intensity: String
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    // REMOVE autoGenerate. create a unique ID manually string
    @PrimaryKey val id: String = "",
    val senderId: String,
    val message: String,
    val timestamp: Long
)

@Dao
interface QuakeHistoryDao {
    @Upsert
    suspend fun upsertAll(quakes: List<QuakeData>)

    @Query("SELECT COUNT(*) FROM quake_history")
    suspend fun count(): Int

    @Query("SELECT * FROM quake_history ORDER BY time DESC")
    fun getAll(): Flow<List<QuakeData>>

    @Query("SELECT * FROM quake_history ORDER BY time DESC")
    fun getPaged(): PagingSource<Int, QuakeData>

    @Query("DELETE FROM quake_history")
    suspend fun clearAll()

    @Transaction
    suspend fun deleteAndInsert(quakes: List<QuakeData>) {
        clearAll()
        upsertAll(quakes)
    }
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Change to REPLACE
    suspend fun insertAll(messages: List<ChatMessage>)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC LIMIT 50")
    fun getRecent(): Flow<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE timestamp < :threshold")
    suspend fun pruneOldMessages(threshold: Long)
}