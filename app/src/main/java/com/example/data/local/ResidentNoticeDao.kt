package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ResidentNotice
import kotlinx.coroutines.flow.Flow

@Dao
interface ResidentNoticeDao {

    @Query("SELECT * FROM resident_notices ORDER BY scheduledDate ASC, scheduledTime ASC, id DESC")
    fun getAllNotices(): Flow<List<ResidentNotice>>

    @Query("SELECT * FROM resident_notices ORDER BY scheduledDate ASC, scheduledTime ASC, id DESC")
    suspend fun getAllNoticesDirect(): List<ResidentNotice>

    @Query("SELECT * FROM resident_notices WHERE id = :id")
    suspend fun getNoticeById(id: Long): ResidentNotice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: ResidentNotice): Long

    @Update
    suspend fun updateNotice(notice: ResidentNotice)

    @Delete
    suspend fun deleteNotice(notice: ResidentNotice)

    @Query("SELECT * FROM resident_notices WHERE status IN ('PENDENTE', 'VISUALIZADO') AND scheduledDate >= :todayDate ORDER BY scheduledDate ASC, scheduledTime ASC")
    fun getActiveNoticesForHandover(todayDate: String): Flow<List<ResidentNotice>>

    @Query("SELECT * FROM resident_notices WHERE status IN ('PENDENTE', 'VISUALIZADO') AND scheduledDate >= :todayDate ORDER BY scheduledDate ASC, scheduledTime ASC")
    suspend fun getActiveNoticesForHandoverDirect(todayDate: String): List<ResidentNotice>

    @Query("SELECT COUNT(*) FROM resident_notices WHERE status = 'PENDENTE'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM resident_notices WHERE status = 'PENDENTE'")
    suspend fun getPendingCountDirect(): Int
}
