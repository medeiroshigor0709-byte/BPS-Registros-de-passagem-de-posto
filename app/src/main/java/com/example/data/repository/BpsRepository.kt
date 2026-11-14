package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.ChecklistTemplateDao
import com.example.data.local.EmployeeDao
import com.example.data.local.ResidentNoticeDao
import com.example.data.local.ShiftAttachmentDao
import com.example.data.local.ShiftCameraRecordDao
import com.example.data.local.ShiftDraftDao
import com.example.data.local.ShiftReportDao
import com.example.data.model.CameraMonitoringState
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ResidentNotice
import com.example.data.model.ShiftAttachment
import com.example.data.model.ShiftCameraRecord
import com.example.data.model.ShiftDraft
import com.example.data.model.ShiftReport
import com.example.util.AttachmentStorageManager
import kotlinx.coroutines.flow.Flow

class BpsRepository(
    private val database: AppDatabase,
    private val employeeDao: EmployeeDao = database.employeeDao(),
    private val templateDao: ChecklistTemplateDao = database.checklistTemplateDao(),
    private val reportDao: ShiftReportDao = database.shiftReportDao(),
    private val draftDao: ShiftDraftDao = database.shiftDraftDao(),
    private val attachmentDao: ShiftAttachmentDao = database.shiftAttachmentDao(),
    private val cameraRecordDao: ShiftCameraRecordDao = database.shiftCameraRecordDao(),
    private val residentNoticeDao: ResidentNoticeDao = database.residentNoticeDao(),
    private val appContext: Context? = null
) {
    companion object {
        private const val SEED_PREFS_NAME = "bps_default_seed_prefs"
        private const val KEY_SEED_V4_RECONCILED = "seed_v4_portaria_reconciled"
    }

    // Employees
    val allEmployees: Flow<List<Employee>> = employeeDao.getAllEmployees()
    val activeEmployees: Flow<List<Employee>> = employeeDao.getActiveEmployees()

    suspend fun getAllEmployeesDirect(): List<Employee> = employeeDao.getAllEmployeesDirect()

    suspend fun saveEmployee(employee: Employee): Long {
        val normalizedName = employee.name.trim()
        val existing = employeeDao.getAllEmployeesDirect()
            .firstOrNull { it.name.trim().equals(normalizedName, ignoreCase = true) && it.id != employee.id }
        if (existing != null && employee.id == 0L) {
            val updated = existing.copy(
                name = normalizedName,
                role = employee.role,
                shift = employee.shift,
                phone = employee.phone,
                badgeNumber = employee.badgeNumber,
                isActive = employee.isActive
            )
            employeeDao.updateEmployee(updated)
            return existing.id
        }
        val cleanEmployee = employee.copy(name = normalizedName)
        return if (cleanEmployee.id == 0L) {
            employeeDao.insertEmployee(cleanEmployee)
        } else {
            employeeDao.updateEmployee(cleanEmployee)
            cleanEmployee.id
        }
    }

    suspend fun deleteEmployee(employee: Employee) = employeeDao.deleteEmployee(employee)

    // Checklist Templates
    val allTemplateItems: Flow<List<ChecklistTemplateItem>> = templateDao.getAllTemplateItems()
    val activeTemplateItems: Flow<List<ChecklistTemplateItem>> = templateDao.getActiveTemplateItems()

    suspend fun getAllTemplateItemsDirect(): List<ChecklistTemplateItem> = templateDao.getAllTemplateItemsDirect()

    suspend fun saveTemplateItem(item: ChecklistTemplateItem): Long {
        val cleanItem = item.copy(
            title = item.title.trim(),
            category = item.category.trim().ifBlank { "Geral" },
            modelName = item.modelName.ifBlank { ChecklistTemplateItem.DEFAULT_MODEL_NAME }
        )
        return if (cleanItem.id == 0L) {
            val existing = templateDao.getAllTemplateItemsDirect()
                .firstOrNull { it.title.trim().equals(cleanItem.title, ignoreCase = true) }
            if (existing != null) {
                templateDao.updateItem(
                    existing.copy(
                        title = cleanItem.title,
                        emoji = cleanItem.emoji,
                        isRequired = cleanItem.isRequired,
                        isCamerasItem = cleanItem.isCamerasItem,
                        category = cleanItem.category,
                        isEnabled = cleanItem.isEnabled
                    )
                )
                existing.id
            } else {
                templateDao.insertItem(cleanItem)
            }
        } else {
            templateDao.updateItem(cleanItem)
            cleanItem.id
        }
    }

    suspend fun deleteTemplateItem(item: ChecklistTemplateItem) = templateDao.deleteItem(item)

    suspend fun resetTemplatesToDefault() {
        database.withTransaction {
            templateDao.clearAll()
            templateDao.insertAll(AppDatabase.defaultChecklistSeed())
        }
    }

    // Reports History & Offline Sync Layer
    val allReports: Flow<List<ShiftReport>> = reportDao.getAllReports()
    val pendingReports: Flow<List<ShiftReport>> = reportDao.getPendingSyncReports()
    val pendingCount: Flow<Int> = reportDao.getPendingCount()

    fun searchReports(query: String): Flow<List<ShiftReport>> = reportDao.searchReports(query)

    suspend fun saveReport(report: ShiftReport): Long = reportDao.insertReport(report)

    /**
     * Atomically saves a shift handover report together with its independent 32-camera snapshot
     * (Monitor 1: 16 cameras + Monitor 2: 16 cameras), checklist items snapshot, and linked observation attachments.
     */
    suspend fun saveReportWithCamerasAndAttachments(
        report: ShiftReport,
        cameraState: CameraMonitoringState,
        draftAttachments: List<ShiftAttachment>,
        clearDraftAfter: Boolean = true
    ): Long {
        return database.withTransaction {
            val snapshotJson = cameraState.toSnapshotJson()
            val finalReport = report.copy(
                cameraWorkingCount = cameraState.toWorkingCountText(),
                monitor1Details = cameraState.toMonitorDetailsText(1),
                monitor2Details = cameraState.toMonitorDetailsText(2),
                cameraStatesJson = snapshotJson,
                attachmentCount = draftAttachments.size
            )
            val newReportId = reportDao.insertReport(finalReport)

            val now = finalReport.timestamp
            val cameraRecords = (cameraState.monitor1 + cameraState.monitor2).map { ch ->
                ShiftCameraRecord(
                    reportId = newReportId,
                    monitorNumber = ch.monitorNumber,
                    cameraNumber = ch.cameraNumber,
                    isWorking = ch.isWorking,
                    recordedAt = now
                )
            }
            cameraRecordDao.insertAll(cameraRecords)

            if (draftAttachments.isNotEmpty()) {
                val linkedAttachments = draftAttachments.map { att ->
                    att.copy(id = 0, reportId = newReportId)
                }
                attachmentDao.insertAttachments(linkedAttachments)
            }

            if (clearDraftAfter) {
                draftDao.clearDraft()
                attachmentDao.deleteDraftAttachments()
            }

            newReportId
        }
    }

    suspend fun getCameraRecordsForReportDirect(reportId: Long): List<ShiftCameraRecord> =
        cameraRecordDao.getCamerasForReportDirect(reportId)

    suspend fun markReportAsSynced(id: Long) = reportDao.markAsSynced(id, System.currentTimeMillis())

    suspend fun markAllAsSynced() = reportDao.markAllAsSynced(System.currentTimeMillis())

    suspend fun deleteReport(report: ShiftReport, context: Context? = null) {
        val attachedPaths = attachmentDao.getAttachmentsForReportDirect(report.id).map { it.filePath }.distinct()
        reportDao.deleteReport(report)
        if (context != null) {
            attachedPaths.forEach { path ->
                val refs = attachmentDao.countReferencesByFilePath(path)
                AttachmentStorageManager.deletePhysicalFileIfUnreferenced(context, path, refs)
            }
        }
    }

    // Observation Attachments (Draft & Per-Report)
    val draftAttachments: Flow<List<ShiftAttachment>> = attachmentDao.getDraftAttachments()

    fun getAttachmentsForReport(reportId: Long): Flow<List<ShiftAttachment>> =
        attachmentDao.getAttachmentsForReport(reportId)

    suspend fun getAttachmentsForReportDirect(reportId: Long): List<ShiftAttachment> =
        attachmentDao.getAttachmentsForReportDirect(reportId)

    suspend fun addDraftAttachment(attachment: ShiftAttachment): Pair<ShiftAttachment, Boolean> {
        val existing = attachmentDao.findDraftAttachmentBySha256(attachment.sha256)
        if (existing != null) {
            return existing to true // Already attached to current draft (no duplicate!)
        }
        val newId = attachmentDao.insertAttachment(attachment.copy(id = 0, reportId = null))
        return attachment.copy(id = newId, reportId = null) to false
    }

    suspend fun addAttachmentToReport(reportId: Long, attachment: ShiftAttachment): Pair<ShiftAttachment, Boolean> {
        val existing = attachmentDao.findReportAttachmentBySha256(reportId, attachment.sha256)
        if (existing != null) {
            return existing to true
        }
        val insertedId = database.withTransaction {
            val id = attachmentDao.insertAttachment(attachment.copy(id = 0, reportId = reportId))
            val newCount = attachmentDao.countAttachmentsForReport(reportId)
            reportDao.updateAttachmentCount(reportId, newCount)
            id
        }
        return attachment.copy(id = insertedId, reportId = reportId) to false
    }

    suspend fun removeAttachment(attachment: ShiftAttachment, context: Context? = null) {
        database.withTransaction {
            attachmentDao.deleteAttachment(attachment)
            val reportId = attachment.reportId
            if (reportId != null) {
                val newCount = attachmentDao.countAttachmentsForReport(reportId)
                reportDao.updateAttachmentCount(reportId, newCount)
            }
        }
        if (context != null) {
            val refs = attachmentDao.countReferencesByFilePath(attachment.filePath)
            AttachmentStorageManager.deletePhysicalFileIfUnreferenced(context, attachment.filePath, refs)
        }
    }

    suspend fun updateReportNotes(reportId: Long, generalNotes: String) {
        val count = attachmentDao.countAttachmentsForReport(reportId)
        reportDao.updateNotesAndAttachmentCount(reportId, generalNotes, count)
    }

    // Draft autosave
    suspend fun saveDraft(draft: ShiftDraft) = draftDao.saveDraft(draft)
    suspend fun getDraftDirect(): ShiftDraft? = draftDao.getDraftDirect()
    suspend fun clearDraft() {
        draftDao.clearDraft()
        attachmentDao.deleteDraftAttachments()
    }

    suspend fun ensureDefaultDataSeeded(context: Context? = null) {
        val effectiveContext = context ?: appContext
        if (effectiveContext != null && database.openHelper.databaseName != null) {
            val prefs = effectiveContext.applicationContext.getSharedPreferences(SEED_PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_SEED_V4_RECONCILED, false)) {
                AppDatabase.populateInitialData(database)
                prefs.edit().putBoolean(KEY_SEED_V4_RECONCILED, true).apply()
                return
            }
        }
        if (templateDao.getTemplateCount() == 0 && employeeDao.getEmployeeCount() == 0) {
            AppDatabase.populateInitialData(database)
        }
    }

    // Resident Notices (Avisos de Moradores)
    val allResidentNotices: Flow<List<ResidentNotice>> = residentNoticeDao.getAllNotices()
    val pendingNoticesCount: Flow<Int> = residentNoticeDao.getPendingCount()

    fun getActiveNoticesForHandover(todayDate: String): Flow<List<ResidentNotice>> =
        residentNoticeDao.getActiveNoticesForHandover(todayDate)

    suspend fun getActiveNoticesForHandoverDirect(todayDate: String): List<ResidentNotice> =
        residentNoticeDao.getActiveNoticesForHandoverDirect(todayDate)

    suspend fun saveResidentNotice(notice: ResidentNotice): Long {
        return if (notice.id == 0L) {
            residentNoticeDao.insertNotice(notice)
        } else {
            residentNoticeDao.updateNotice(notice)
            notice.id
        }
    }

    suspend fun deleteResidentNotice(notice: ResidentNotice) = residentNoticeDao.deleteNotice(notice)

    suspend fun getResidentNoticeById(id: Long): ResidentNotice? = residentNoticeDao.getNoticeById(id)
}
