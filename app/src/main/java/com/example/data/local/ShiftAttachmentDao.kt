package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ShiftAttachment
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftAttachmentDao {
    @Query("SELECT * FROM shift_attachments WHERE reportId IS NULL ORDER BY createdAt ASC, id ASC")
    fun getDraftAttachments(): Flow<List<ShiftAttachment>>

    @Query("SELECT * FROM shift_attachments WHERE reportId IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getDraftAttachmentsDirect(): List<ShiftAttachment>

    @Query("SELECT * FROM shift_attachments WHERE reportId = :reportId ORDER BY createdAt ASC, id ASC")
    fun getAttachmentsForReport(reportId: Long): Flow<List<ShiftAttachment>>

    @Query("SELECT * FROM shift_attachments WHERE reportId = :reportId ORDER BY createdAt ASC, id ASC")
    suspend fun getAttachmentsForReportDirect(reportId: Long): List<ShiftAttachment>

    @Query("SELECT * FROM shift_attachments WHERE reportId IS NULL AND sha256 = :sha256 LIMIT 1")
    suspend fun findDraftAttachmentBySha256(sha256: String): ShiftAttachment?

    @Query("SELECT * FROM shift_attachments WHERE reportId = :reportId AND sha256 = :sha256 LIMIT 1")
    suspend fun findReportAttachmentBySha256(reportId: Long, sha256: String): ShiftAttachment?

    @Query("SELECT COUNT(*) FROM shift_attachments WHERE reportId = :reportId")
    suspend fun countAttachmentsForReport(reportId: Long): Int

    @Query("SELECT COUNT(*) FROM shift_attachments WHERE filePath = :filePath")
    suspend fun countReferencesByFilePath(filePath: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: ShiftAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<ShiftAttachment>)

    @Delete
    suspend fun deleteAttachment(attachment: ShiftAttachment)

    @Query("DELETE FROM shift_attachments WHERE reportId IS NULL")
    suspend fun deleteDraftAttachments()
}
