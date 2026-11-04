package com.marcorable.ccrecorder.core.data.file

import com.marcorable.ccrecorder.core.model.PersistenceError
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AudioFileStore {
    fun pendingRelativePath(sessionId: String): String

    fun finalRelativePath(sessionId: String): String

    suspend fun preparePending(sessionId: String): String

    suspend fun finalizePending(sessionId: String): String

    suspend fun deletePending(sessionId: String): Boolean

    suspend fun deleteFinal(sessionId: String): Boolean

    suspend fun deleteRecording(relativePath: String): Boolean

    suspend fun recordingExists(relativePath: String): Boolean

    suspend fun deleteAllPending(): Int

    suspend fun listRecordingRelativePaths(): Set<String>

    suspend fun <T> locked(block: suspend () -> T): T
}

@Singleton
class AppAudioFileStore @Inject constructor(
    private val filesDir: File,
) : AudioFileStore {
    private val mutex = Mutex()

    override fun pendingRelativePath(sessionId: String): String {
        validateUuid(sessionId)
        return "audio/pending/$sessionId.pending.m4a"
    }

    override fun finalRelativePath(sessionId: String): String {
        validateUuid(sessionId)
        return "audio/recordings/$sessionId.m4a"
    }

    override suspend fun preparePending(sessionId: String): String {
        val relativePath = pendingRelativePath(sessionId)
        val file = resolve(relativePath, PathKind.Pending)
        file.parentFile?.mkdirs()
        if (!file.createNewFile()) {
            throw PersistenceError.ConversationConflict()
        }
        return relativePath
    }

    override suspend fun finalizePending(sessionId: String): String {
        val pending = resolve(pendingRelativePath(sessionId), PathKind.Pending)
        val final = resolve(finalRelativePath(sessionId), PathKind.Recording)
        if (!pending.isFile || pending.length() <= 0L) {
            throw PersistenceError.AudioFileMissing(pendingRelativePath(sessionId))
        }
        final.parentFile?.mkdirs()
        if (final.exists()) {
            throw PersistenceError.ConversationConflict()
        }
        try {
            Files.move(pending.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            throw PersistenceError.AudioFinalizeFailed(error)
        } catch (error: Exception) {
            throw PersistenceError.AudioFinalizeFailed(error)
        }
        return finalRelativePath(sessionId)
    }

    override suspend fun deletePending(sessionId: String): Boolean =
        deleteIfPresent(resolve(pendingRelativePath(sessionId), PathKind.Pending))

    override suspend fun deleteFinal(sessionId: String): Boolean =
        deleteIfPresent(resolve(finalRelativePath(sessionId), PathKind.Recording))

    override suspend fun deleteRecording(relativePath: String): Boolean =
        deleteIfPresent(resolve(relativePath, PathKind.Recording))

    override suspend fun recordingExists(relativePath: String): Boolean =
        resolve(relativePath, PathKind.Recording).isFile

    override suspend fun deleteAllPending(): Int {
        val directory = safeDirectory(PENDING_DIRECTORY)
        if (!directory.exists()) return 0
        return directory.listFiles().orEmpty().count { file ->
            !Files.isSymbolicLink(file.toPath()) && file.isFile && deleteIfPresent(file)
        }
    }

    override suspend fun listRecordingRelativePaths(): Set<String> {
        val directory = safeDirectory(RECORDINGS_DIRECTORY)
        if (!directory.exists()) return emptySet()
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .mapNotNull { file ->
                val path = "$RECORDINGS_DIRECTORY/${file.name}"
                runCatching { resolve(path, PathKind.Recording); path }.getOrNull()
            }
            .toSet()
    }

    override suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }

    internal fun fileFor(relativePath: String): File {
        val kind = if (relativePath.startsWith("$PENDING_DIRECTORY/")) PathKind.Pending else PathKind.Recording
        return resolve(relativePath, kind)
    }

    private fun resolve(relativePath: String, kind: PathKind): File {
        if (
            relativePath.isBlank() ||
            relativePath.startsWith('/') ||
            relativePath.contains('\\') ||
            relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw PersistenceError.AudioPathInvalid(relativePath)
        }
        val regex = when (kind) {
            PathKind.Pending -> PENDING_PATH_REGEX
            PathKind.Recording -> RECORDING_PATH_REGEX
        }
        if (!regex.matches(relativePath)) throw PersistenceError.AudioPathInvalid(relativePath)

        val base = filesDir.canonicalFile
        val target = File(base, relativePath)
        val targetParent = target.parentFile?.canonicalFile
            ?: throw PersistenceError.AudioPathInvalid(relativePath)
        val allowedDirectory = when (kind) {
            PathKind.Pending -> safeDirectory(PENDING_DIRECTORY)
            PathKind.Recording -> safeDirectory(RECORDINGS_DIRECTORY)
        }.canonicalFile
        if (targetParent != allowedDirectory || !target.canonicalFile.toPath().startsWith(base.toPath())) {
            throw PersistenceError.AudioPathInvalid(relativePath)
        }
        if (Files.isSymbolicLink(target.toPath()) || Files.isSymbolicLink(targetParent.toPath())) {
            throw PersistenceError.AudioPathInvalid(relativePath)
        }
        return target
    }

    private fun safeDirectory(relativePath: String): File {
        val base = filesDir.canonicalFile
        val directory = File(base, relativePath)
        if (directory.exists() && Files.isSymbolicLink(directory.toPath())) {
            throw PersistenceError.AudioPathInvalid(relativePath)
        }
        val canonical = directory.canonicalFile
        if (!canonical.toPath().startsWith(base.toPath())) {
            throw PersistenceError.AudioPathInvalid(relativePath)
        }
        return directory
    }

    private fun validateUuid(id: String) {
        val normalized = runCatching { UUID.fromString(id).toString() }.getOrNull()
        if (normalized != id || !UUID_REGEX.matches(id)) throw PersistenceError.InvalidData("Invalid UUID: $id")
    }

    private fun deleteIfPresent(file: File): Boolean = !file.exists() || file.delete()

    private enum class PathKind { Pending, Recording }

    companion object {
        private const val PENDING_DIRECTORY = "audio/pending"
        private const val RECORDINGS_DIRECTORY = "audio/recordings"
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        private val PENDING_PATH_REGEX = Regex("audio/pending/${UUID_REGEX.pattern}\\.pending\\.m4a")
        private val RECORDING_PATH_REGEX = Regex("audio/recordings/${UUID_REGEX.pattern}\\.m4a")
    }
}
