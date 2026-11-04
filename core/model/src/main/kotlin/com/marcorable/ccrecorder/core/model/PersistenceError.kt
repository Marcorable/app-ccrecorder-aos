package com.marcorable.ccrecorder.core.model

sealed class PersistenceError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class AudioPathInvalid(path: String) : PersistenceError("Invalid audio relative path: $path")

    class AudioFileMissing(path: String) : PersistenceError("Audio file is missing: $path")

    class AudioFinalizeFailed(cause: Throwable? = null) :
        PersistenceError("Failed to finalize the audio file", cause)

    class ConversationConflict(cause: Throwable? = null) :
        PersistenceError("Conversation id or audio path already exists", cause)

    class PersistenceFailed(cause: Throwable? = null) :
        PersistenceError("Local persistence operation failed", cause)

    class CleanupPending(path: String, cause: Throwable? = null) :
        PersistenceError("File cleanup is pending: $path", cause)

    class NotFound(entity: String, id: String) : PersistenceError("$entity was not found: $id")

    class InvalidData(message: String) : PersistenceError(message)
}
