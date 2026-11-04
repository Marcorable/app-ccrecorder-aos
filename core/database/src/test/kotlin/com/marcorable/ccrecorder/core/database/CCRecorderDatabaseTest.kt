package com.marcorable.ccrecorder.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcorable.ccrecorder.core.database.di.DatabaseConstraintsCallback
import com.marcorable.ccrecorder.core.database.model.ConversationEntity
import com.marcorable.ccrecorder.core.database.model.ConversationNoteEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPartyEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPinEntity
import com.marcorable.ccrecorder.core.database.model.NoteEntity
import com.marcorable.ccrecorder.core.database.model.PartyEntity
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
class CCRecorderDatabaseTest {
    private lateinit var database: CCRecorderDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CCRecorderDatabase::class.java,
        ).addCallback(DatabaseConstraintsCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aggregateRelationsPreserveOrderAndPartySnapshot() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000001"
        val partyId = "00000000-0000-4000-8000-000000000002"
        val directPartyId = "00000000-0000-4000-8000-000000000003"
        database.partyDao().insert(PartyEntity(partyId, "Before"))
        database.conversationDao().insert(
            ConversationEntity(conversationId, 10L, "audio/recordings/$conversationId.m4a", "Title", "Topic"),
        )
        database.conversationDao().insertPins(
            listOf(
                ConversationPinEntity(conversationId, 0, 500L),
                ConversationPinEntity(conversationId, 1, 100L),
            ),
        )
        database.conversationDao().insertParties(
            listOf(
                ConversationPartyEntity(conversationId, 0, partyId, "Before", partyId),
                ConversationPartyEntity(conversationId, 1, directPartyId, "Guest", null),
            ),
        )
        repeat(2) { index ->
            val noteId = "00000000-0000-4000-8000-00000000000${index + 4}"
            database.noteDao().insert(NoteEntity(noteId, index.toLong(), "word$index", "뜻$index", "VOCABULARY"))
            database.conversationDao().insertConversationNote(ConversationNoteEntity(conversationId, noteId, index))
        }

        database.partyDao().update(PartyEntity(partyId, "After"))
        val aggregate = requireNotNull(database.conversationDao().getAggregate(conversationId))
        assertEquals(listOf(500L, 100L), aggregate.pins.map { it.offsetMs })
        assertEquals(listOf("After", "Guest"), aggregate.parties.map { it.liveName ?: it.snapshotName })
        assertEquals(listOf("word0", "word1"), aggregate.notes.map { it.original })

        database.partyDao().delete(partyId)
        val snapshot = requireNotNull(database.conversationDao().getAggregate(conversationId)).parties.first()
        assertNull(snapshot.livePartyId)
        assertEquals("Before", snapshot.snapshotName)
    }

    @Test
    fun deletingConversationCascadesRelationsButPreservesNotesAndForeignKeysAreEnabled() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000011"
        val noteId = "00000000-0000-4000-8000-000000000012"
        database.conversationDao().insert(
            ConversationEntity(conversationId, 10L, "audio/recordings/$conversationId.m4a", "Title", ""),
        )
        database.conversationDao().insertPins(listOf(ConversationPinEntity(conversationId, 0, 0)))
        database.noteDao().insert(NoteEntity(noteId, 1L, "word", "뜻", "VOCABULARY"))
        database.conversationDao().insertConversationNote(ConversationNoteEntity(conversationId, noteId, 0))

        database.conversationDao().delete(conversationId)

        assertNull(database.conversationDao().getEntity(conversationId))
        assertTrue(database.conversationDao().getPins(conversationId).isEmpty())
        assertTrue(database.noteDao().linksForConversation(conversationId).isEmpty())
        assertEquals(noteId, database.noteDao().get(noteId)?.id)
        val cursor = database.openHelper.readableDatabase.query("PRAGMA foreign_keys")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun nextPinUsesOffsetOrderAndInvalidValuesAreRejected() = runTest {
        val conversationId = "00000000-0000-4000-8000-000000000021"
        database.conversationDao().insert(
            ConversationEntity(conversationId, 10L, "audio/recordings/$conversationId.m4a", "Title", ""),
        )
        database.conversationDao().insertPins(
            listOf(
                ConversationPinEntity(conversationId, 0, 900L),
                ConversationPinEntity(conversationId, 1, 300L),
            ),
        )
        assertEquals(300L, database.pinDao().findNext(conversationId, 100L)?.offsetMs)

        val invalid = runCatching {
            database.pinDao().insert(ConversationPinEntity(conversationId, 2, -1L))
        }
        assertTrue(invalid.isFailure)
        assertFalse(database.conversationDao().getPins(conversationId).any { it.offsetMs < 0 })
    }

    @Test
    fun version1IdentityHashIsPinned() {
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42",
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(VERSION_1_IDENTITY_HASH, it.getString(0))
        }
    }

    companion object {
        private const val VERSION_1_IDENTITY_HASH = "b77707a8e2872146cf2373bd07235abd"
    }
}
