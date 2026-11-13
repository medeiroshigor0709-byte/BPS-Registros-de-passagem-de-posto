package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object AttachmentType {
    const val PHOTO = "PHOTO"
    const val PDF = "PDF"
}

@Entity(
    tableName = "shift_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ShiftReport::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["sha256"])
    ]
)
data class ShiftAttachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reportId: Long? = null, // null = active draft attachment; non-null = linked to saved ShiftReport
    val type: String, // AttachmentType.PHOTO or AttachmentType.PDF
    val displayName: String,
    val filePath: String, // Persistent file inside filesDir/shift_attachments/
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Long = System.currentTimeMillis()
)
