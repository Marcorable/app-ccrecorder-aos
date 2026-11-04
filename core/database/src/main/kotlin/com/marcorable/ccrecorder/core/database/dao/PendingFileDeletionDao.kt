package com.marcorable.ccrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marcorable.ccrecorder.core.database.model.PendingFileDeletionEntity

@Dao
interface PendingFileDeletionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(record: PendingFileDeletionEntity): Long

    @Query("SELECT * FROM pending_file_deletions ORDER BY requested_at_epoch_ms ASC, relative_path ASC")
    suspend fun getAll(): List<PendingFileDeletionEntity>

    @Query("SELECT * FROM pending_file_deletions WHERE relative_path = :relativePath")
    suspend fun get(relativePath: String): PendingFileDeletionEntity?

    @Query("DELETE FROM pending_file_deletions WHERE relative_path = :relativePath")
    suspend fun delete(relativePath: String): Int

    @Query("UPDATE pending_file_deletions SET attempt_count = attempt_count + 1 WHERE relative_path = :relativePath")
    suspend fun incrementAttempt(relativePath: String): Int
}
