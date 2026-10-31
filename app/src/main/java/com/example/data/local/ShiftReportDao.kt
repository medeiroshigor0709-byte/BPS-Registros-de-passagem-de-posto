package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ShiftReport
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftReportDao {
    @Query("SELECT * FROM shift_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ShiftReport>>

    @Query("SELECT * FROM shift_reports WHERE receivingEmployee LIKE '%' || :query || '%' OR departingEmployee LIKE '%' || :query || '%' OR reportContent LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchReports(query: String): Flow<List<ShiftReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ShiftReport): Long

    @Delete
    suspend fun deleteReport(report: ShiftReport)

    @Query("DELETE FROM shift_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)
}
