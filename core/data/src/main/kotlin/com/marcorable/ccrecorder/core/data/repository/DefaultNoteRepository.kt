package com.marcorable.ccrecorder.core.data.repository

import androidx.room.withTransaction
import com.marcorable.ccrecorder.core.data.NoteRepository
import com.marcorable.ccrecorder.core.database.CCRecorderDatabase
import com.marcorable.ccrecorder.core.model.Note
import com.marcorable.ccrecorder.core.model.PersistenceError
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultNoteRepository @Inject constructor(
    private val database: CCRecorderDatabase,
) : NoteRepository {
    private val notes = database.noteDao()

    override fun observeNotes(): Flow<List<Note>> = notes.observeAll().map { values -> values.map { it.toDomain() } }

    override suspend fun getNote(id: String): Note =
        notes.get(id)?.toDomain() ?: throw PersistenceError.NotFound("Note", id)

    override suspend fun updateNote(note: Note) {
        validate(note)
        if (notes.update(note.toEntity()) == 0) throw PersistenceError.NotFound("Note", note.id)
    }

    override suspend fun deleteNotes(ids: Set<String>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            val missing = ids.firstOrNull { notes.get(it) == null }
            if (missing != null) throw PersistenceError.NotFound("Note", missing)
            val affected = notes.conversationIdsForNotes(ids.toList())
            notes.deleteByIds(ids.toList())
            affected.forEach { conversationId ->
                notes.linksForConversation(conversationId).forEachIndexed { position, link ->
                    if (position != link.position) notes.updatePosition(conversationId, link.noteId, position)
                }
            }
        }
    }

    override suspend fun deleteIfPresent(id: String) {
        notes.deleteByIds(listOf(id))
    }

    private fun validate(note: Note) {
        if (runCatching { UUID.fromString(note.id).toString() }.getOrNull() != note.id) {
            throw PersistenceError.InvalidData("Invalid UUID: ${note.id}")
        }
        if (note.original.isEmpty() && note.translation.isEmpty()) {
            throw PersistenceError.InvalidData("A note must contain original or translated text")
        }
    }
}
