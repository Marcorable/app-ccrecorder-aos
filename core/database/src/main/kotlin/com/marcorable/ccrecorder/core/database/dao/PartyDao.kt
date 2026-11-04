package com.marcorable.ccrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.marcorable.ccrecorder.core.database.model.PartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name ASC, id ASC")
    fun observeAll(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun get(id: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(party: PartyEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(party: PartyEntity): Int

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun delete(id: String): Int
}
