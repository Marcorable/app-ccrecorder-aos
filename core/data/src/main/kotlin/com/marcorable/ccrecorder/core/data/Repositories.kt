package com.marcorable.ccrecorder.core.data

import com.marcorable.ccrecorder.core.model.Conversation
import com.marcorable.ccrecorder.core.model.ConversationSummary
import com.marcorable.ccrecorder.core.model.Note
import com.marcorable.ccrecorder.core.model.Party
import com.marcorable.ccrecorder.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<ConversationSummary>>

    suspend fun getConversation(id: String): Conversation

    suspend fun prepareRecording(sessionId: String): String

    suspend fun saveRecording(conversation: Conversation)

    suspend fun updateConversationInfo(
        id: String,
        title: String,
        topic: String,
        parties: List<Party>,
    )

    suspend fun addNote(conversationId: String, note: Note)

    suspend fun deleteNotes(noteIds: Set<String>)

    suspend fun deleteConversation(id: String): ConversationDeletionResult

    suspend fun cancelRecording(sessionId: String)
}

data class ConversationDeletionResult(
    val cleanupPending: Boolean,
)

interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>

    suspend fun getNote(id: String): Note

    suspend fun updateNote(note: Note)

    suspend fun deleteNotes(ids: Set<String>)

    suspend fun deleteIfPresent(id: String)
}

interface PartyRepository {
    fun observeParties(): Flow<List<Party>>

    suspend fun getParty(id: String): Party

    suspend fun createParty(party: Party)

    suspend fun updateParty(party: Party)

    suspend fun deleteParty(id: String)
}

interface SettingsRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setDisplayMode(mode: com.marcorable.ccrecorder.core.model.DisplayMode)

    suspend fun setSkipIntervalSeconds(seconds: Int)

    suspend fun setKeepScreenAwake(keepAwake: Boolean)
}

interface LocalDataInitializer {
    suspend fun initialize(): ReconciliationReport
}

data class ReconciliationReport(
    val deletedPendingFiles: Int,
    val completedQueuedDeletions: Int,
    val failedQueuedDeletions: Int,
    val deletedOrphanRecordings: Int,
    val missingReferencedRecordings: List<String>,
)
