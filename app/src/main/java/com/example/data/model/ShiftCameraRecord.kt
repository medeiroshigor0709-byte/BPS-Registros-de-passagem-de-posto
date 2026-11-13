package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_camera_records",
    foreignKeys = [
        ForeignKey(
            entity = ShiftReport::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["reportId"])
    ]
)
data class ShiftCameraRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reportId: Long,
    val monitorNumber: Int, // 1 or 2
    val cameraNumber: Int,  // 1..16
    val isWorking: Boolean,
    val recordedAt: Long = System.currentTimeMillis()
)
