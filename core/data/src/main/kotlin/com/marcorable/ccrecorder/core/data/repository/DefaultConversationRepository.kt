package com.marcorable.ccrecorder.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.marcorable.ccrecorder.core.data.ConversationDeletionResult
import com.marcorable.ccrecorder.core.data.ConversationRepository
import com.marcorable.ccrecorder.core.data.file.AudioFileStore
import com.marcorable.ccrecorder.core.database.CCRecorderDatabase
import com.marcorable.ccrecorder.core.database.model.ConversationEntity
import com.marcorable.ccrecorder.core.database.model.ConversationNoteEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPartyEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPinEntity
import com.marcorable.ccrecorder.core.database.model.PendingFileDeletionEntity
import com.marcorable.ccrecorder.core.model.Conversation
import com.marcorable.ccrecorder.core.model.ConversationSummary
import com.marcorable.ccrecorder.core.model.Note
import com.marcorable.ccrecorder.core.model.Party
import com.marcorable.ccrecorder.core.model.PersistenceError
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultConversationRepository(
    private val database: CCRecorderDatabase,
    private val audioFileStore: AudioFileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ConversationRepository {
    private val conversations = database.conversationDao()
    private val notes = database.noteDao()
    private val pendingDeletions = database.pendingFileDeletionDao()

    override fun observeConversations(): Flow<List<ConversationSummary>> =
        conversations.observeAll().map { records ->
            records.map { record ->
                record.toSummary(runCatching { audioFileStore.recordingExists(record.audioRelativePath) }.getOrDefault(false))
            }
        }

    override suspend fun getConversation(id: String): Conversation {
        validateUuid(id)
        val record = conversations.getAggregate(id) ?: throw PersistenceError.NotFound("Conversation", id)
        val available = runCatching {
            audioFileStore.recordingExists(record.conversation.audioRelativePath)
        }.getOrDefault(false)
        return record.toDomain(available)
    }

    override suspend fun prepareRecording(sessionId: String): String = audioFileStore.locked {
        validateUuid(sessionId)
        audioFileStore.preparePending(sessionId)
    }

    override suspend fun saveRecording(conversation: Conversation) = audioFileStore.locked {
        validateConversation(conversation)
        val expectedPath = audioFileStore.finalRelativePath(conversation.id)
        if (conversation.audioRelativePath != expectedPath) {
            throw PersistenceError.AudioPathInvalid(conversation.audioRelativePath)
        }

        var finalizedByThisCall = false
        try {
            audioFileStore.finalizePending(conversation.id)
            finalizedByThisCall = true
            database.withTransaction {
                conversations.insert(
                    ConversationEntity(
                        id = conversation.id,
                        createdAtEpochMs = conversation.createdAtEpochMs,
                        audioRelativePath = conversation.audioRelativePath,
                        title = conversation.title,
                        topic = conversation.topic,
                    ),
                )
                conversations.insertPins(
                    conversation.pins.mapIndexed { position, pin ->
                        ConversationPinEntity(conversation.id, position, pin.offsetMs)
                    },
                )
                conversations.insertParties(conversation.parties.toSnapshots(conversation.id))
            }
        } catch (error: Throwable) {
            if (finalizedByThisCall) audioFileStore.deleteFinal(conversation.id)
            audioFileStore.deletePending(conversation.id)
            if (error.hasCause<SQLiteConstraintException>()) {
                throw PersistenceError.ConversationConflict(error)
            }
            if (error is PersistenceError) throw error
            throw PersistenceError.PersistenceFailed(error)
        }
    }

    override suspend fun updateConversationInfo(
        id: String,
        title: String,
        topic: String,
        parties: List<Party>,
    ) {
        validateUuid(id)
        parties.forEach(::validateParty)
        try {
            database.withTransaction {
                if (conversations.updateInfo(id, title, topic) == 0) {
                    throw PersistenceError.NotFound("Conversation", id)
                }
                conversations.deleteParties(id)
                conversations.insertParties(parties.toSnapshots(id))
            }
        } catch (error: PersistenceError) {
            throw error
        } catch (error: Throwable) {
            throw PersistenceError.PersistenceFailed(error)
        }
    }

    override suspend fun addNote(conversationId: String, note: Note) {
        validateUuid(conversationId)
        validateNote(note)
        try {
            database.withTransaction {
                if (!conversations.exists(conversationId)) {
                    throw PersistenceError.NotFound("Conversation", conversationId)
                }
                notes.insert(note.toEntity())
                conversations.insertConversationNote(
                    ConversationNoteEntity(
                        conversationId = conversationId,
                        noteId = note.id,
                        position = conversations.nextNotePosition(conversationId),
                    ),
                )
            }
        } catch (error: PersistenceError) {
            throw error
        } catch (error: Throwable) {
            throw PersistenceError.PersistenceFailed(error)
        }
    }

    override suspend fun deleteNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        noteIds.forEach(::validateUuid)
        database.withTransaction {
            val missingId = noteIds.firstOrNull { notes.get(it) == null }
            if (missingId != null) throw PersistenceError.NotFound("Note", missingId)
            val affectedConversations = notes.conversationIdsForNotes(noteIds.toList())
            notes.deleteByIds(noteIds.toList())
            affectedConversations.forEach { conversationId ->
                notes.linksForConversation(conversationId).forEachIndexed { position, link ->
                    if (link.position != position) notes.updatePosition(conversationId, link.noteId, position)
                }
            }
        }
    }

    override suspend fun deleteConversation(id: String): ConversationDeletionResult {
        validateUuid(id)
        val relativePath = try {
            database.withTransaction {
                val entity = conversations.getEntity(id) ?: throw PersistenceError.NotFound("Conversation", id)
                pendingDeletions.enqueue(
                    PendingFileDeletionEntity(
                        relativePath = entity.audioRelativePath,
                        requestedAtEpochMs = clock(),
                    ),
                )
                conversations.delete(id)
                entity.audioRelativePath
            }
        } catch (error: PersistenceError) {
            throw error
        } catch (error: Throwable) {
            throw PersistenceError.PersistenceFailed(error)
        }

        val deleted = runCatching {
            audioFileStore.locked { audioFileStore.deleteRecording(relativePath) }
        }.getOrDefault(false)
        if (deleted) {
            pendingDeletions.delete(relativePath)
        } else {
            pendingDeletions.incrementAttempt(relativePath)
        }
        return ConversationDeletionResult(cleanupPending = !deleted)
    }

    override suspend fun cancelRecording(sessionId: String) = audioFileStore.locked {
        validateUuid(sessionId)
        audioFileStore.deletePending(sessionId)
        if (!conversations.exists(sessionId)) {
            audioFileStore.deleteFinal(sessionId)
        }
        Unit
    }

    private fun validateConversation(conversation: Conversation) {
        validateUuid(conversation.id)
        if (conversation.createdAtEpochMs < 0L) throw PersistenceError.InvalidData("Negative creation time")
        conversation.pins.forEach { pin ->
            if (pin.offsetMs < 0L) throw PersistenceError.InvalidData("Negative pin offset")
        }
        conversation.parties.forEach(::validateParty)
    }

    private fun validateNote(note: Note) {
        validateUuid(note.id)
        if (note.createdAtEpochMs < 0L) throw PersistenceError.InvalidData("Negative creation time")
        if (note.original.isEmpty() && note.translation.isEmpty()) {
            throw PersistenceError.InvalidData("A note must contain original or translated text")
        }
    }

    private fun validateParty(party: Party) {
        validateUuid(party.id)
    }

    private fun validateUuid(id: String) {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) {
            throw PersistenceError.InvalidData("Invalid UUID: $id")
        }
    }

    private fun List<Party>.toSnapshots(conversationId: String): List<ConversationPartyEntity> =
        mapIndexed { position, party ->
            ConversationPartyEntity(
                conversationId = conversationId,
                position = position,
                snapshotPartyId = party.id,
                snapshotName = party.name,
                livePartyId = party.id.takeIf { party.isAvailable },
            )
        }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }
}
