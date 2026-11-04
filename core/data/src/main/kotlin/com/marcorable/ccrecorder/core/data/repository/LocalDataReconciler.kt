package com.marcorable.ccrecorder.core.data.repository

import com.marcorable.ccrecorder.core.data.LocalDataInitializer
import com.marcorable.ccrecorder.core.data.ReconciliationReport
import com.marcorable.ccrecorder.core.data.file.AudioFileStore
import com.marcorable.ccrecorder.core.database.CCRecorderDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataReconciler @Inject constructor(
    private val database: CCRecorderDatabase,
    private val audioFileStore: AudioFileStore,
) : LocalDataInitializer {
    override suspend fun initialize(): ReconciliationReport = audioFileStore.locked {
        val pendingDeleted = audioFileStore.deleteAllPending()
        var queuedCompleted = 0
        var queuedFailed = 0
        database.pendingFileDeletionDao().getAll().forEach { deletion ->
            val success = runCatching { audioFileStore.deleteRecording(deletion.relativePath) }.getOrDefault(false)
            if (success) {
                database.pendingFileDeletionDao().delete(deletion.relativePath)
                queuedCompleted += 1
            } else {
                database.pendingFileDeletionDao().incrementAttempt(deletion.relativePath)
                queuedFailed += 1
            }
        }

        val referenced = database.conversationDao().getAllAudioRelativePaths().toSet()
        val queued = database.pendingFileDeletionDao().getAll().mapTo(mutableSetOf()) { it.relativePath }
        val onDisk = audioFileStore.listRecordingRelativePaths()
        var orphanDeleted = 0
        (onDisk - referenced - queued).forEach { orphan ->
            if (audioFileStore.deleteRecording(orphan)) orphanDeleted += 1
        }

        ReconciliationReport(
            deletedPendingFiles = pendingDeleted,
            completedQueuedDeletions = queuedCompleted,
            failedQueuedDeletions = queuedFailed,
            deletedOrphanRecordings = orphanDeleted,
            missingReferencedRecordings = (referenced - onDisk).sorted(),
        )
    }
}
