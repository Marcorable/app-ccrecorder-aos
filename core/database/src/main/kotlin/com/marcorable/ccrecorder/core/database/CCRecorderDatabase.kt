package com.marcorable.ccrecorder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marcorable.ccrecorder.core.database.dao.ConversationDao
import com.marcorable.ccrecorder.core.database.dao.NoteDao
import com.marcorable.ccrecorder.core.database.dao.PartyDao
import com.marcorable.ccrecorder.core.database.dao.PendingFileDeletionDao
import com.marcorable.ccrecorder.core.database.dao.PinDao
import com.marcorable.ccrecorder.core.database.model.ConversationEntity
import com.marcorable.ccrecorder.core.database.model.ConversationNoteEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPartyEntity
import com.marcorable.ccrecorder.core.database.model.ConversationPinEntity
import com.marcorable.ccrecorder.core.database.model.NoteEntity
import com.marcorable.ccrecorder.core.database.model.PartyEntity
import com.marcorable.ccrecorder.core.database.model.PendingFileDeletionEntity

@Database(
    entities = [
        ConversationEntity::class,
        ConversationPinEntity::class,
        NoteEntity::class,
        PartyEntity::class,
        ConversationPartyEntity::class,
        ConversationNoteEntity::class,
        PendingFileDeletionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CCRecorderDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun noteDao(): NoteDao

    abstract fun partyDao(): PartyDao

    abstract fun pinDao(): PinDao

    abstract fun pendingFileDeletionDao(): PendingFileDeletionDao

    companion object {
        const val NAME = "ccrecorder.db"
    }
}
