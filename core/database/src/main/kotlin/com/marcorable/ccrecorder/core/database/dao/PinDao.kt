package com.marcorable.ccrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marcorable.ccrecorder.core.database.model.ConversationPinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinDao {
    @Query("SELECT * FROM conversation_pins WHERE conversation_id = :conversationId ORDER BY position ASC")
    fun observeForConversation(conversationId: String): Flow<List<ConversationPinEntity>>

    @Query(
        """
        SELECT * FROM conversation_pins
        WHERE conversation_id = :conversationId AND offset_ms > :offsetMs
        ORDER BY offset_ms ASC, position ASC
        LIMIT 1
        """,
    )
    suspend fun findNext(conversationId: String, offsetMs: Long): ConversationPinEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pin: ConversationPinEntity)

    @Query("DELETE FROM conversation_pins WHERE conversation_id = :conversationId AND position = :position")
    suspend fun delete(conversationId: String, position: Int): Int
}
