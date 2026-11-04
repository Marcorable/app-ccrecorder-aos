package com.marcorable.ccrecorder.core.data.repository

import com.marcorable.ccrecorder.core.database.model.ConversationAggregateRecord
import com.marcorable.ccrecorder.core.database.model.ConversationEntity
import com.marcorable.ccrecorder.core.database.model.NoteEntity
import com.marcorable.ccrecorder.core.database.model.PartyEntity
import com.marcorable.ccrecorder.core.model.AudioAvailability
import com.marcorable.ccrecorder.core.model.Conversation
import com.marcorable.ccrecorder.core.model.ConversationSummary
import com.marcorable.ccrecorder.core.model.Note
import com.marcorable.ccrecorder.core.model.NoteCategory
import com.marcorable.ccrecorder.core.model.Party
import com.marcorable.ccrecorder.core.model.Pin

internal fun ConversationAggregateRecord.toDomain(audioAvailable: Boolean): Conversation = Conversation(
    id = conversation.id,
    createdAtEpochMs = conversation.createdAtEpochMs,
    audioRelativePath = conversation.audioRelativePath,
    title = conversation.title,
    topic = conversation.topic,
    pins = pins.map { Pin(it.offsetMs) },
    parties = parties.map {
        Party(
            id = it.snapshotPartyId,
            name = it.liveName ?: it.snapshotName,
            isAvailable = it.livePartyId != null && it.liveName != null,
        )
    },
    notes = notes.map(NoteEntity::toDomain),
    audioAvailability = if (audioAvailable) AudioAvailability.Available else AudioAvailability.Missing,
)

internal fun ConversationEntity.toSummary(audioAvailable: Boolean): ConversationSummary = ConversationSummary(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    audioRelativePath = audioRelativePath,
    title = title,
    topic = topic,
    audioAvailability = if (audioAvailable) AudioAvailability.Available else AudioAvailability.Missing,
)

internal fun NoteEntity.toDomain(): Note = Note(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    original = original,
    translation = translation,
    category = NoteCategory.valueOf(category),
)

internal fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    original = original,
    translation = translation,
    category = category.name,
)

internal fun PartyEntity.toDomain(): Party = Party(id = id, name = name, isAvailable = true)

internal fun Party.toEntity(): PartyEntity = PartyEntity(id = id, name = name)
