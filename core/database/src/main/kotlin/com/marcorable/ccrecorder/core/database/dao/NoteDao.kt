package com.marcorable.ccrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.marcorable.ccrecorder.core.database.model.ConversationNoteEntity
import com.marcorable.ccrecorder.core.database.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        ORDER BY CASE WHEN original = '' OR translation = '' THEN 0 ELSE 1 END ASC,
                 created_at_epoch_ms ASC,
                 id ASC
        """,
    )
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(note: NoteEntity): Int

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("SELECT DISTINCT conversation_id FROM conversation_notes WHERE note_id IN (:noteIds)")
    suspend fun conversationIdsForNotes(noteIds: List<String>): List<String>

    @Query("SELECT * FROM conversation_notes WHERE conversation_id = :conversationId ORDER BY position ASC")
    suspend fun linksForConversation(conversationId: String): List<ConversationNoteEntity>

    @Query("UPDATE conversation_notes SET position = :position WHERE conversation_id = :conversationId AND note_id = :noteId")
    suspend fun updatePosition(conversationId: String, noteId: String, position: Int): Int
}
