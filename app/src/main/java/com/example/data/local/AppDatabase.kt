package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ShiftAttachment
import com.example.data.model.ShiftCameraRecord
import com.example.data.model.ShiftDraft
import com.example.data.model.ShiftReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [
        Employee::class,
        ChecklistTemplateItem::class,
        ShiftReport::class,
        ShiftDraft::class,
        ShiftAttachment::class,
        ShiftCameraRecord::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun checklistTemplateDao(): ChecklistTemplateDao
    abstract fun shiftReportDao(): ShiftReportDao
    abstract fun shiftDraftDao(): ShiftDraftDao
    abstract fun shiftAttachmentDao(): ShiftAttachmentDao
    abstract fun shiftCameraRecordDao(): ShiftCameraRecordDao

    companion object {
        const val DEFAULT_MODEL_NAME = ChecklistTemplateItem.DEFAULT_MODEL_NAME

        private val LEGACY_DEMO_EMPLOYEE_NAMES = setOf(
            "carlos silva",
            "rodrigo santos",
            "marcos oliveira"
        )

        private val seedMutex = Mutex()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_reports ADD COLUMN cameraStatesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shift_reports ADD COLUMN attachmentCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shift_drafts ADD COLUMN cameraStatesJson TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shift_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `reportId` INTEGER,
                        `type` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`reportId`) REFERENCES `shift_reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_attachments_reportId` ON `shift_attachments` (`reportId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_attachments_sha256` ON `shift_attachments` (`sha256`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shift_camera_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `reportId` INTEGER NOT NULL,
                        `monitorNumber` INTEGER NOT NULL,
                        `cameraNumber` INTEGER NOT NULL,
                        `isWorking` INTEGER NOT NULL,
                        `recordedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`reportId`) REFERENCES `shift_reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_camera_records_reportId` ON `shift_camera_records` (`reportId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE checklist_templates ADD COLUMN modelName TEXT NOT NULL DEFAULT '${DEFAULT_MODEL_NAME}'"
                )
                db.execSQL(
                    "ALTER TABLE shift_reports ADD COLUMN checklistSnapshotJson TEXT NOT NULL DEFAULT ''"
                )

                // Clean only known demo fictional employees while preserving any real user-added employees
                db.execSQL(
                    "DELETE FROM employees WHERE LOWER(TRIM(name)) IN ('carlos silva', 'rodrigo santos', 'marcos oliveira')"
                )
                // Deduplicate duplicate employee names keeping lowest id
                db.execSQL(
                    """
                    DELETE FROM employees
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM employees GROUP BY LOWER(TRIM(name))
                    )
                    """.trimIndent()
                )

                // Normalize legacy spelling "Câmaras" -> "Câmeras"
                db.execSQL(
                    "UPDATE checklist_templates SET title = 'Câmeras' WHERE LOWER(TRIM(title)) IN ('câmaras', 'camaras')"
                )
                // Deduplicate duplicate checklist items keeping lowest id
                db.execSQL(
                    """
                    DELETE FROM checklist_templates
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM checklist_templates GROUP BY LOWER(TRIM(title))
                    )
                    """.trimIndent()
                )
            }
        }

        fun defaultEmployeesSeed(): List<Employee> = listOf(
            Employee(
                name = "Cláudia",
                role = "Porteira",
                shift = "Diurno (07h às 19h)",
                phone = "",
                badgeNumber = "BPS-101"
            ),
            Employee(
                name = "Higor",
                role = "Porteiro",
                shift = "Noturno (19h às 07h)",
                phone = "",
                badgeNumber = "BPS-104"
            )
        )

        fun defaultChecklistSeed(): List<ChecklistTemplateItem> = listOf(
            ChecklistTemplateItem(title = "Celular", emoji = "📱", isRequired = true, isCamerasItem = false, orderIndex = 0, category = "Equipamentos", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Carregador", emoji = "🔌", isRequired = true, isCamerasItem = false, orderIndex = 1, category = "Equipamentos", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Interfone", emoji = "📞", isRequired = true, isCamerasItem = false, orderIndex = 2, category = "Comunicação", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Livro de ocorrência", emoji = "📔", isRequired = true, isCamerasItem = false, orderIndex = 3, category = "Documentação", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Rádio HTS", emoji = "📻", isRequired = true, isCamerasItem = false, orderIndex = 4, category = "Comunicação", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Cerca elétrica", emoji = "🚧", isRequired = true, isCamerasItem = false, orderIndex = 5, category = "Segurança Perimetral", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Câmeras", emoji = "🎥", isRequired = true, isCamerasItem = true, orderIndex = 6, category = "CFTV & Monitoramento", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Guarda chuva", emoji = "☔️", isRequired = false, isCamerasItem = false, orderIndex = 7, category = "Materiais de Apoio", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Guarda Sol", emoji = "⛱️", isRequired = false, isCamerasItem = false, orderIndex = 8, category = "Materiais de Apoio", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Controle", emoji = "🕹️", isRequired = true, isCamerasItem = false, orderIndex = 9, category = "Acesso & Portões", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Caixa d'água", emoji = "🌊", isRequired = true, isCamerasItem = false, orderIndex = 10, category = "Instalações Prediais", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Ar condicionado", emoji = "💨", isRequired = true, isCamerasItem = false, orderIndex = 11, category = "Equipamentos da Portaria", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Chaves", emoji = "🗝️", isRequired = true, isCamerasItem = false, orderIndex = 12, category = "Acesso & Chaves", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Extintor pó químico", emoji = "🧯", isRequired = true, isCamerasItem = false, orderIndex = 13, category = "Prevenção & Combate", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Portão de pedestre", emoji = "🚶", isRequired = true, isCamerasItem = false, orderIndex = 14, category = "Geral", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Portão de entrada veículos", emoji = "🚗", isRequired = true, isCamerasItem = false, orderIndex = 15, category = "Geral", modelName = DEFAULT_MODEL_NAME),
            ChecklistTemplateItem(title = "Portão de saída veículos", emoji = "🚙", isRequired = true, isCamerasItem = false, orderIndex = 16, category = "Geral", modelName = DEFAULT_MODEL_NAME)
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val instance = Room.databaseBuilder(
                    appCtx,
                    AppDatabase::class.java,
                    "bps_facilities_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        /**
         * Idempotent initial seed & reconciliation routine:
         * - Guarantees only Cláudia and Higor in the default roster (removes only known demo fictional names)
         * - Guarantees the 17 items of model "BPS FACILITIES — PORTARIA" with zero duplicates
         * - Preserves all real user-added employees, custom checklist items, and historical reports
         */
        suspend fun populateInitialData(database: AppDatabase) {
            seedMutex.withLock {
                database.withTransaction {
                    val employeeDao = database.employeeDao()
                    val checklistDao = database.checklistTemplateDao()

                    // 1. Reconcile employees without losing real user additions
                    val existingEmployees = employeeDao.getAllEmployeesDirect()
                    val seenEmployeeKeys = mutableSetOf<String>()
                    existingEmployees.forEach { emp ->
                        val key = emp.name.trim().lowercase()
                        if (key in LEGACY_DEMO_EMPLOYEE_NAMES || !seenEmployeeKeys.add(key)) {
                            employeeDao.deleteEmployee(emp)
                        }
                    }

                    val currentEmployeesAfterCleanup = employeeDao.getAllEmployeesDirect()
                        .map { it.name.trim().lowercase() }
                        .toSet()

                    val missingEmployees = defaultEmployeesSeed().filter { seed ->
                        seed.name.trim().lowercase() !in currentEmployeesAfterCleanup
                    }
                    if (missingEmployees.isNotEmpty()) {
                        employeeDao.insertAll(missingEmployees)
                    }

                    // 2. Reconcile checklist template items cleanly (exact 17 default items, zero duplicates)
                    val existingItems = checklistDao.getAllTemplateItemsDirect()
                    val seenItemKeys = mutableSetOf<String>()
                    existingItems.forEach { item ->
                        var normalizedItem = item
                        val rawKey = item.title.trim().lowercase()
                        if (rawKey == "câmaras" || rawKey == "camaras") {
                            normalizedItem = item.copy(title = "Câmeras")
                        }
                        if (normalizedItem.modelName.isBlank()) {
                            normalizedItem = normalizedItem.copy(modelName = DEFAULT_MODEL_NAME)
                        }
                        val normalizedKey = normalizedItem.title.trim().lowercase()
                        if (!seenItemKeys.add(normalizedKey)) {
                            checklistDao.deleteItem(item)
                        } else if (normalizedItem != item) {
                            checklistDao.updateItem(normalizedItem)
                        }
                    }

                    val currentItemKeys = checklistDao.getAllTemplateItemsDirect()
                        .map { it.title.trim().lowercase() }
                        .toSet()

                    val missingItems = defaultChecklistSeed().filter { seed ->
                        seed.title.trim().lowercase() !in currentItemKeys
                    }
                    if (missingItems.isNotEmpty()) {
                        checklistDao.insertAll(missingItems)
                    }
                }
            }
        }
    }
}
