package com.marcorable.ccrecorder.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "conversations",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["created_at_epoch_ms", "id"]),
        Index(value = ["audio_relative_path"], unique = true),
    ],
)
data class ConversationEntity(
    val id: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "audio_relative_path") val audioRelativePath: String,
    val title: String,
    @ColumnInfo(defaultValue = "''") val topic: String = "",
)

@Entity(
    tableName = "conversation_pins",
    primaryKeys = ["conversation_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversation_id"])],
)
data class ConversationPinEntity(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val position: Int,
    @ColumnInfo(name = "offset_ms") val offsetMs: Long,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(defaultValue = "''") val original: String = "",
    @ColumnInfo(defaultValue = "''") val translation: String = "",
    val category: String,
)

@Entity(tableName = "parties")
data class PartyEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "conversation_parties",
    primaryKeys = ["conversation_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["live_party_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["live_party_id"]),
        Index(value = ["conversation_id", "snapshot_party_id"]),
    ],
)
data class ConversationPartyEntity(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val position: Int,
    @ColumnInfo(name = "snapshot_party_id") val snapshotPartyId: String,
    @ColumnInfo(name = "snapshot_name") val snapshotName: String,
    @ColumnInfo(name = "live_party_id") val livePartyId: String?,
)

@Entity(
    tableName = "conversation_notes",
    primaryKeys = ["conversation_id", "note_id"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "position"], unique = true),
        Index(value = ["note_id"], unique = true),
    ],
)
data class ConversationNoteEntity(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "note_id") val noteId: String,
    val position: Int,
)

@Entity(tableName = "pending_file_deletions")
data class PendingFileDeletionEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "requested_at_epoch_ms") val requestedAtEpochMs: Long,
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
)

data class ConversationPartyRow(
    @ColumnInfo(name = "snapshot_party_id") val snapshotPartyId: String,
    @ColumnInfo(name = "snapshot_name") val snapshotName: String,
    @ColumnInfo(name = "live_party_id") val livePartyId: String?,
    @ColumnInfo(name = "live_name") val liveName: String?,
)

data class ConversationAggregateRecord(
    val conversation: ConversationEntity,
    val pins: List<ConversationPinEntity>,
    val parties: List<ConversationPartyRow>,
    val notes: List<NoteEntity>,
)
