package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ShiftCameraRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftCameraRecordDao {
    @Query("SELECT * FROM shift_camera_records WHERE reportId = :reportId ORDER BY monitorNumber ASC, cameraNumber ASC")
    fun getCamerasForReport(reportId: Long): Flow<List<ShiftCameraRecord>>

    @Query("SELECT * FROM shift_camera_records WHERE reportId = :reportId ORDER BY monitorNumber ASC, cameraNumber ASC")
    suspend fun getCamerasForReportDirect(reportId: Long): List<ShiftCameraRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ShiftCameraRecord>)
}
