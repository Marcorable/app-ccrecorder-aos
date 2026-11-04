package com.marcorable.ccrecorder.core.model

data class Conversation(
    val id: String,
    val createdAtEpochMs: Long,
    val audioRelativePath: String,
    val title: String,
    val topic: String = "",
    val pins: List<Pin> = emptyList(),
    val parties: List<Party> = emptyList(),
    val notes: List<Note> = emptyList(),
    val audioAvailability: AudioAvailability = AudioAvailability.Available,
)

data class ConversationSummary(
    val id: String,
    val createdAtEpochMs: Long,
    val audioRelativePath: String,
    val title: String,
    val topic: String,
    val audioAvailability: AudioAvailability,
)

data class Pin(
    val offsetMs: Long,
)

data class Party(
    val id: String,
    val name: String,
    val isAvailable: Boolean,
)

enum class AudioAvailability {
    Available,
    Missing,
}
