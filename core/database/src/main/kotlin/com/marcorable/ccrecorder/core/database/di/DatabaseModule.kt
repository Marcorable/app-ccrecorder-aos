package com.marcorable.ccrecorder.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marcorable.ccrecorder.core.database.CCRecorderDatabase
import com.marcorable.ccrecorder.core.database.dao.ConversationDao
import com.marcorable.ccrecorder.core.database.dao.NoteDao
import com.marcorable.ccrecorder.core.database.dao.PartyDao
import com.marcorable.ccrecorder.core.database.dao.PendingFileDeletionDao
import com.marcorable.ccrecorder.core.database.dao.PinDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CCRecorderDatabase =
        Room.databaseBuilder(context, CCRecorderDatabase::class.java, CCRecorderDatabase.NAME)
            .addCallback(DatabaseConstraintsCallback)
            .build()

    @Provides
    fun provideConversationDao(database: CCRecorderDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideNoteDao(database: CCRecorderDatabase): NoteDao = database.noteDao()

    @Provides
    fun providePartyDao(database: CCRecorderDatabase): PartyDao = database.partyDao()

    @Provides
    fun providePinDao(database: CCRecorderDatabase): PinDao = database.pinDao()

    @Provides
    fun providePendingFileDeletionDao(database: CCRecorderDatabase): PendingFileDeletionDao =
        database.pendingFileDeletionDao()
}

object DatabaseConstraintsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(
            """
            CREATE TRIGGER validate_conversation_pin_insert
            BEFORE INSERT ON conversation_pins
            WHEN NEW.position < 0 OR NEW.offset_ms < 0
            BEGIN SELECT RAISE(ABORT, 'conversation pin values must be non-negative'); END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER validate_note_category_insert
            BEFORE INSERT ON notes
            WHEN NEW.category NOT IN ('VOCABULARY', 'SENTENCE')
            BEGIN SELECT RAISE(ABORT, 'invalid note category'); END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER validate_conversation_party_insert
            BEFORE INSERT ON conversation_parties
            WHEN NEW.position < 0 OR
                 (NEW.live_party_id IS NOT NULL AND NEW.snapshot_party_id != NEW.live_party_id)
            BEGIN SELECT RAISE(ABORT, 'invalid conversation party'); END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER validate_conversation_note_insert
            BEFORE INSERT ON conversation_notes
            WHEN NEW.position < 0
            BEGIN SELECT RAISE(ABORT, 'conversation note position must be non-negative'); END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER validate_pending_deletion_insert
            BEFORE INSERT ON pending_file_deletions
            WHEN NEW.attempt_count < 0
            BEGIN SELECT RAISE(ABORT, 'attempt count must be non-negative'); END
            """.trimIndent(),
        )
    }
}
