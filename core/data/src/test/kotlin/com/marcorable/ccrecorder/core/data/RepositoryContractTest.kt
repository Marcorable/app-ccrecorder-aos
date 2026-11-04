package com.marcorable.ccrecorder.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcorable.ccrecorder.core.data.file.AppAudioFileStore
import com.marcorable.ccrecorder.core.data.file.AudioFileStore
import com.marcorable.ccrecorder.core.data.repository.DefaultConversationRepository
import com.marcorable.ccrecorder.core.data.repository.DefaultPartyRepository
import com.marcorable.ccrecorder.core.data.repository.LocalDataReconciler
import com.marcorable.ccrecorder.core.database.CCRecorderDatabase
import com.marcorable.ccrecorder.core.database.di.DatabaseConstraintsCallback
import com.marcorable.ccrecorder.core.model.AudioAvailability
import com.marcorable.ccrecorder.core.model.Conversation
import com.marcorable.ccrecorder.core.model.Note
import com.marcorable.ccrecorder.core.model.NoteCategory
import com.marcorable.ccrecorder.core.model.Party
import com.marcorable.ccrecorder.core.model.Pin
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryContractTest {
    private lateinit var database: CCRecorderDatabase
    private lateinit var directory: File
    private lateinit var files: AppAudioFileStore
    private lateinit var repository: DefaultConversationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CCRecorderDatabase::class.java,
        ).addCallback(DatabaseConstraintsCallback)
            .allowMainThreadQueries()
            .build()
        directory = Files.createTempDirectory("repository-contract").toFile()
        files = AppAudioFileStore(directory)
        repository = DefaultConversationRepository(database, files) { 1234L }
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun aggregateRoundTripUsesLivePartyNameAndPreservesOrders() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000201"
        val partyId = "00000000-0000-4000-8000-000000000202"
        val directPartyId = "00000000-0000-4000-8000-000000000203"
        val partyRepository = DefaultPartyRepository(database.partyDao())
        partyRepository.createParty(Party(partyId, "Initial", true))
        prepareNonEmptyPending(conversationId)
        repository.saveRecording(
            conversation(
                id = conversationId,
                pins = listOf(Pin(500), Pin(100)),
                parties = listOf(Party(partyId, "Initial", true), Party(directPartyId, "Guest", false)),
            ),
        )
        repository.addNote(conversationId, note("00000000-0000-4000-8000-000000000204", 1L))
        repository.addNote(conversationId, note("00000000-0000-4000-8000-000000000205", 2L))
        partyRepository.updateParty(Party(partyId, "Renamed", true))

        val aggregate = repository.getConversation(conversationId)
        assertEquals(listOf(500L, 100L), aggregate.pins.map { it.offsetMs })
        assertEquals(listOf("Renamed", "Guest"), aggregate.parties.map { it.name })
        assertEquals(listOf(1L, 2L), aggregate.notes.map { it.createdAtEpochMs })
        assertEquals(AudioAvailability.Available, aggregate.audioAvailability)

        partyRepository.deleteParty(partyId)
        val snapshot = repository.getConversation(conversationId).parties.first()
        assertFalse(snapshot.isAvailable)
        assertEquals("Initial", snapshot.name)
    }

    @Test
    fun databaseInsertFailureCompensatesFinalAndPendingFiles() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000211"
        val missingPartyId = "00000000-0000-4000-8000-000000000212"
        prepareNonEmptyPending(conversationId)

        assertTrue(
            runCatching {
                repository.saveRecording(
                    conversation(
                        id = conversationId,
                        parties = listOf(Party(missingPartyId, "Missing", true)),
                    ),
                )
            }.isFailure,
        )
        assertFalse(files.fileFor(files.pendingRelativePath(conversationId)).exists())
        assertFalse(files.fileFor(files.finalRelativePath(conversationId)).exists())
        assertNull(database.conversationDao().getEntity(conversationId))
    }

    @Test
    fun deletingNotesRemovesLinksAndRenumbersRemainingPositions() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000214"
        val noteIds = listOf(
            "00000000-0000-4000-8000-000000000215",
            "00000000-0000-4000-8000-000000000216",
            "00000000-0000-4000-8000-000000000217",
        )
        prepareNonEmptyPending(conversationId)
        repository.saveRecording(conversation(conversationId))
        noteIds.forEachIndexed { index, id -> repository.addNote(conversationId, note(id, index.toLong())) }

        repository.deleteNotes(setOf(noteIds[1]))

        assertNull(database.noteDao().get(noteIds[1]))
        assertEquals(listOf(0, 1), database.noteDao().linksForConversation(conversationId).map { it.position })
        assertEquals(listOf(noteIds[0], noteIds[2]), repository.getConversation(conversationId).notes.map { it.id })
    }

    @Test
    fun cancellationDeletesDraftButNeverDeletesCommittedRecording() = runTest {
        val draftId = "00000000-0000-4000-8000-000000000218"
        repository.prepareRecording(draftId)
        repository.cancelRecording(draftId)
        assertFalse(files.fileFor(files.pendingRelativePath(draftId)).exists())

        val committedId = "00000000-0000-4000-8000-000000000219"
        prepareNonEmptyPending(committedId)
        repository.saveRecording(conversation(committedId))
        repository.cancelRecording(committedId)
        assertTrue(files.recordingExists(files.finalRelativePath(committedId)))
        assertTrue(database.conversationDao().exists(committedId))
    }

    @Test
    fun conversationDeletionPreservesNotesAndQueuesFailedFileCleanup() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000221"
        val noteId = "00000000-0000-4000-8000-000000000222"
        prepareNonEmptyPending(conversationId)
        repository.saveRecording(conversation(conversationId))
        repository.addNote(conversationId, note(noteId, 1L))
        val failingStore = FailingDeleteAudioStore(files, failDelete = true)
        val failingRepository = DefaultConversationRepository(database, failingStore) { 1234L }

        val result = failingRepository.deleteConversation(conversationId)

        assertTrue(result.cleanupPending)
        assertNull(database.conversationDao().getEntity(conversationId))
        assertEquals(noteId, database.noteDao().get(noteId)?.id)
        val pending = database.pendingFileDeletionDao().get(files.finalRelativePath(conversationId))
        assertEquals(1, pending?.attemptCount)
        assertTrue(files.recordingExists(files.finalRelativePath(conversationId)))

        failingStore.failDelete = false
        val report = LocalDataReconciler(database, failingStore).initialize()
        assertEquals(1, report.completedQueuedDeletions)
        assertNull(database.pendingFileDeletionDao().get(files.finalRelativePath(conversationId)))
    }

    @Test
    fun startupReconciliationDeletesPendingAndOrphanButKeepsMissingMetadata() = runTest {
        val referencedId = "00000000-0000-4000-8000-000000000231"
        val orphanId = "00000000-0000-4000-8000-000000000232"
        val pendingId = "00000000-0000-4000-8000-000000000233"
        prepareNonEmptyPending(referencedId)
        repository.saveRecording(conversation(referencedId))
        files.fileFor(files.finalRelativePath(referencedId)).delete()
        files.fileFor(files.finalRelativePath(orphanId)).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        repository.prepareRecording(pendingId)

        val report = LocalDataReconciler(database, files).initialize()

        assertEquals(1, report.deletedPendingFiles)
        assertEquals(1, report.deletedOrphanRecordings)
        assertEquals(listOf(files.finalRelativePath(referencedId)), report.missingReferencedRecordings)
        assertEquals(AudioAvailability.Missing, repository.getConversation(referencedId).audioAvailability)
        assertTrue(database.conversationDao().exists(referencedId))
    }

    private suspend fun prepareNonEmptyPending(id: String) {
        repository.prepareRecording(id)
        files.fileFor(files.pendingRelativePath(id)).writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun conversation(
        id: String,
        pins: List<Pin> = emptyList(),
        parties: List<Party> = emptyList(),
    ) = Conversation(
        id = id,
        createdAtEpochMs = 1L,
        audioRelativePath = files.finalRelativePath(id),
        title = "Title",
        topic = "Topic",
        pins = pins,
        parties = parties,
    )

    private fun note(id: String, createdAt: Long) = Note(
        id = id,
        createdAtEpochMs = createdAt,
        original = "word-$createdAt",
        translation = "뜻-$createdAt",
        category = NoteCategory.VOCABULARY,
    )

    private class FailingDeleteAudioStore(
        private val delegate: AudioFileStore,
        var failDelete: Boolean,
    ) : AudioFileStore by delegate {
        override suspend fun deleteRecording(relativePath: String): Boolean =
            if (failDelete) false else delegate.deleteRecording(relativePath)
    }
}
