package com.marcorable.ccrecorder.core.model

data class Note(
    val id: String,
    val createdAtEpochMs: Long,
    val original: String = "",
    val translation: String = "",
    val category: NoteCategory,
) {
    val isIncomplete: Boolean
        get() = original.isBlank() || translation.isBlank()
}

enum class NoteCategory {
    VOCABULARY,
    SENTENCE,
}
