package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ChecklistTemplateDao
import com.example.data.local.EmployeeDao
import com.example.data.local.ShiftDraftDao
import com.example.data.local.ShiftReportDao
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ShiftDraft
import com.example.data.model.ShiftReport
import kotlinx.coroutines.flow.Flow

class BpsRepository(
    private val database: AppDatabase,
    private val employeeDao: EmployeeDao = database.employeeDao(),
    private val templateDao: ChecklistTemplateDao = database.checklistTemplateDao(),
    private val reportDao: ShiftReportDao = database.shiftReportDao(),
    private val draftDao: ShiftDraftDao = database.shiftDraftDao()
) {
    // Employees
    val allEmployees: Flow<List<Employee>> = employeeDao.getAllEmployees()
    val activeEmployees: Flow<List<Employee>> = employeeDao.getActiveEmployees()

    suspend fun saveEmployee(employee: Employee): Long {
        return if (employee.id == 0L) {
            employeeDao.insertEmployee(employee)
        } else {
            employeeDao.updateEmployee(employee)
            employee.id
        }
    }

    suspend fun deleteEmployee(employee: Employee) = employeeDao.deleteEmployee(employee)

    // Checklist Templates
    val allTemplateItems: Flow<List<ChecklistTemplateItem>> = templateDao.getAllTemplateItems()
    val activeTemplateItems: Flow<List<ChecklistTemplateItem>> = templateDao.getActiveTemplateItems()

    suspend fun saveTemplateItem(item: ChecklistTemplateItem): Long {
        return if (item.id == 0L) {
            templateDao.insertItem(item)
        } else {
            templateDao.updateItem(item)
            item.id
        }
    }

    suspend fun deleteTemplateItem(item: ChecklistTemplateItem) = templateDao.deleteItem(item)

    suspend fun resetTemplatesToDefault() {
        templateDao.clearAll()
        val defaultItems = listOf(
            ChecklistTemplateItem(title = "Celular", emoji = "📱", isRequired = true, isCamerasItem = false, orderIndex = 0, category = "Equipamentos"),
            ChecklistTemplateItem(title = "Carregador", emoji = "🔌", isRequired = true, isCamerasItem = false, orderIndex = 1, category = "Equipamentos"),
            ChecklistTemplateItem(title = "Interfone", emoji = "📞", isRequired = true, isCamerasItem = false, orderIndex = 2, category = "Comunicação"),
            ChecklistTemplateItem(title = "Livro de ocorrência", emoji = "📔", isRequired = true, isCamerasItem = false, orderIndex = 3, category = "Documentação"),
            ChecklistTemplateItem(title = "Rádio HTS", emoji = "📻", isRequired = true, isCamerasItem = false, orderIndex = 4, category = "Comunicação"),
            ChecklistTemplateItem(title = "Cerca elétrica", emoji = "🚧", isRequired = true, isCamerasItem = false, orderIndex = 5, category = "Segurança Perimetral"),
            ChecklistTemplateItem(title = "Câmaras", emoji = "🎥", isRequired = true, isCamerasItem = true, orderIndex = 6, category = "CFTV & Monitoramento"),
            ChecklistTemplateItem(title = "Guarda chuva", emoji = "☔️", isRequired = false, isCamerasItem = false, orderIndex = 7, category = "Materiais de Apoio"),
            ChecklistTemplateItem(title = "Guarda Sol", emoji = "⛱️", isRequired = false, isCamerasItem = false, orderIndex = 8, category = "Materiais de Apoio"),
            ChecklistTemplateItem(title = "Controle", emoji = "🕹️", isRequired = true, isCamerasItem = false, orderIndex = 9, category = "Acesso & Portões"),
            ChecklistTemplateItem(title = "Caixa d'água", emoji = "🌊", isRequired = true, isCamerasItem = false, orderIndex = 10, category = "Instalações Prediais"),
            ChecklistTemplateItem(title = "Ar condicionado", emoji = "💨", isRequired = true, isCamerasItem = false, orderIndex = 11, category = "Equipamentos da Portaria"),
            ChecklistTemplateItem(title = "Chaves", emoji = "🗝️", isRequired = true, isCamerasItem = false, orderIndex = 12, category = "Acesso & Chaves"),
            ChecklistTemplateItem(title = "Extintor pó químico", emoji = "🧯", isRequired = true, isCamerasItem = false, orderIndex = 13, category = "Prevenção & Combate")
        )
        templateDao.insertAll(defaultItems)
    }

    // Reports History & Offline Sync Layer
    val allReports: Flow<List<ShiftReport>> = reportDao.getAllReports()
    val pendingReports: Flow<List<ShiftReport>> = reportDao.getPendingSyncReports()
    val pendingCount: Flow<Int> = reportDao.getPendingCount()

    fun searchReports(query: String): Flow<List<ShiftReport>> = reportDao.searchReports(query)

    suspend fun saveReport(report: ShiftReport): Long = reportDao.insertReport(report)

    suspend fun markReportAsSynced(id: Long) = reportDao.markAsSynced(id, System.currentTimeMillis())

    suspend fun markAllAsSynced() = reportDao.markAllAsSynced(System.currentTimeMillis())

    suspend fun deleteReport(report: ShiftReport) = reportDao.deleteReport(report)

    // Draft autosave
    suspend fun saveDraft(draft: ShiftDraft) = draftDao.saveDraft(draft)
    suspend fun getDraftDirect(): ShiftDraft? = draftDao.getDraftDirect()
    suspend fun clearDraft() = draftDao.clearDraft()

    suspend fun ensureDefaultDataSeeded() {
        if (templateDao.getTemplateCount() == 0) {
            AppDatabase.populateInitialData(database)
        }
    }
}
