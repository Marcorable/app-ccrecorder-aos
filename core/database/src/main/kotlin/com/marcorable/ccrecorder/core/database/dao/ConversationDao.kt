package com.marcorable.ccrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.marcorable.ccrecorder.core.database.model.ConversationAggregateRecord
import com.marcorable.ccrecorder.core.database.model.ConversationEntity
import com.marcorable.ccrecorder.core.database.model.ConversationNoteEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPartyEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPartyRow
import com.marcorable.ccrecorder.core.database.model.ConversationPinEntity
import com.marcorable.ccrecorder.core.database.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY created_at_epoch_ms ASC, id ASC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY created_at_epoch_ms ASC, id ASC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getEntity(id: String): ConversationEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM conversations WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT audio_relative_path FROM conversations")
    suspend fun getAllAudioRelativePaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(conversation: ConversationEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(conversation: ConversationEntity): Int

    @Query("UPDATE conversations SET title = :title, topic = :topic WHERE id = :id")
    suspend fun updateInfo(id: String, title: String, topic: String): Int

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPins(pins: List<ConversationPinEntity>)

    @Query("DELETE FROM conversation_pins WHERE conversation_id = :conversationId")
    suspend fun deletePins(conversationId: String): Int

    @Query("SELECT * FROM conversation_pins WHERE conversation_id = :conversationId ORDER BY position ASC")
    suspend fun getPins(conversationId: String): List<ConversationPinEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParties(parties: List<ConversationPartyEntity>)

    @Query("DELETE FROM conversation_parties WHERE conversation_id = :conversationId")
    suspend fun deleteParties(conversationId: String): Int

    @Query(
        """
        SELECT cp.snapshot_party_id, cp.snapshot_name, cp.live_party_id, p.name AS live_name
        FROM conversation_parties cp
        LEFT JOIN parties p ON p.id = cp.live_party_id
        WHERE cp.conversation_id = :conversationId
        ORDER BY cp.position ASC
        """,
    )
    suspend fun getParties(conversationId: String): List<ConversationPartyRow>

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN conversation_notes cn ON cn.note_id = n.id
        WHERE cn.conversation_id = :conversationId
        ORDER BY cn.position ASC
        """,
    )
    suspend fun getNotes(conversationId: String): List<NoteEntity>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM conversation_notes WHERE conversation_id = :conversationId")
    suspend fun nextNotePosition(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversationNote(link: ConversationNoteEntity)

    @Transaction
    suspend fun getAggregate(id: String): ConversationAggregateRecord? {
        val conversation = getEntity(id) ?: return null
        return ConversationAggregateRecord(
            conversation = conversation,
            pins = getPins(id),
            parties = getParties(id),
            notes = getNotes(id),
        )
    }
}
