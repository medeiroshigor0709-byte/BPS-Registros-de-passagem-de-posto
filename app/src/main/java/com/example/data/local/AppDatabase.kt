package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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

@Database(
    entities = [
        Employee::class,
        ChecklistTemplateItem::class,
        ShiftReport::class,
        ShiftDraft::class,
        ShiftAttachment::class,
        ShiftCameraRecord::class
    ],
    version = 3,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bps_facilities_database"
                )
                    .addMigrations(MIGRATION_2_3)
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

        suspend fun populateInitialData(database: AppDatabase) {
            val employeeDao = database.employeeDao()
            val checklistDao = database.checklistTemplateDao()

            // Pre-seed employees including Higor from prompt
            val defaultEmployees = listOf(
                Employee(name = "Higor", role = "Porteiro", shift = "Noturno (19h às 07h)", phone = "(11) 98765-4321", badgeNumber = "BPS-104"),
                Employee(name = "Carlos Silva", role = "Porteiro", shift = "Diurno (07h às 19h)", phone = "(11) 97654-3210", badgeNumber = "BPS-102"),
                Employee(name = "Rodrigo Santos", role = "Folguista", shift = "Escala 12x36", phone = "(11) 96543-2109", badgeNumber = "BPS-108"),
                Employee(name = "Marcos Oliveira", role = "Supervisor Operacional", shift = "Geral", phone = "(11) 95432-1098", badgeNumber = "BPS-015")
            )
            employeeDao.insertAll(defaultEmployees)

            // Pre-seed checklist items exactly matching user prompt
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
            checklistDao.insertAll(defaultItems)
        }
    }
}
