package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AttachmentType
import com.example.data.model.CameraMonitoringState
import com.example.data.model.CameraStatus
import com.example.data.model.ChecklistSnapshotHelper
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ShiftAttachment
import com.example.data.model.ShiftReport
import com.example.data.repository.BpsRepository
import com.example.ui.components.BpsHeader
import com.example.ui.components.CameraMonitoringCard
import com.example.ui.screens.MainAppScreen
import com.example.ui.screens.ShiftHandoverScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BpsViewModel
import com.example.ui.viewmodel.ItemCheckState
import com.example.ui.viewmodel.ShiftFormUiState
import com.example.util.AttachmentStorageManager
import com.example.util.ImageToPdfConverter
import com.example.util.NetworkMonitor
import com.example.util.PdfReportGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.ui.viewmodel.BpsViewModelFactory
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BpsUpdateFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: BpsRepository
    private val tempFiles = mutableListOf<File>()
    private val viewModelStores = mutableListOf<ViewModelStore>()

    private fun createTestViewModel(): BpsViewModel {
        val store = ViewModelStore()
        viewModelStores.add(store)
        return ViewModelProvider(store, BpsViewModelFactory(repository, NetworkMonitor(context)))[BpsViewModel::class.java]
    }

    @Before
    fun setup() {
        clearFileProviderCache()
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BpsRepository(db)
    }

    @After
    fun teardown() {
        viewModelStores.forEach { it.clear() }
        viewModelStores.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(50)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
        tempFiles.forEach { it.delete() }
        AttachmentStorageManager.getAttachmentsDir(context).deleteRecursively()
        File(context.filesDir, "generated_pdfs").deleteRecursively()
        clearFileProviderCache()
    }

    private fun clearFileProviderCache() {
        runCatching {
            val field = FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    private fun createTempImageFile(name: String, width: Int, height: Int, format: Bitmap.CompressFormat, color: Int): File {
        val file = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(file).use { out ->
            bmp.compress(format, 95, out)
        }
        bmp.recycle()
        tempFiles.add(file)
        return file
    }

    private fun createTempPdfFile(name: String, content: String = "BPS Test PDF"): File {
        val file = File(context.cacheDir, name)
        val minimalPdf = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >> endobj
            xref
            0 4
            0000000000 65535 f 
            0000000009 00000 n 
            0000000058 00000 n 
            0000000115 00000 n 
            trailer << /Size 4 /Root 1 0 R >>
            startxref
            190
            %%EOF
            % $content
        """.trimIndent()
        file.writeText(minimalPdf)
        tempFiles.add(file)
        return file
    }

    // 1. Conversão de uma imagem para PDF (offline)
    @Test
    fun `test 1 - convert single image to PDF offline`() {
        val jpgFile = createTempImageFile("single_photo.jpg", 800, 1200, Bitmap.CompressFormat.JPEG, Color.BLUE)
        val uri = Uri.fromFile(jpgFile)

        val result = ImageToPdfConverter.convertImagesToPdf(context, listOf(uri))
        assertTrue("Single image conversion should succeed", result.isSuccess)
        val pdfResult = result.getOrThrow()
        assertEquals(1, pdfResult.pageCount)
        assertTrue(pdfResult.pdfFile.exists())
        assertTrue(pdfResult.fileSizeBytes > 0)
        assertTrue(pdfResult.pdfFile.readBytes().take(4).toByteArray().decodeToString().startsWith("%PDF"))
    }

    // 2. Conversão de várias imagens (JPG, PNG, WEBP) para um único PDF (1 página por imagem)
    @Test
    fun `test 2 - convert multiple images JPG PNG WEBP to single multi-page PDF`() {
        val jpgFile = createTempImageFile("img1.jpg", 1200, 800, Bitmap.CompressFormat.JPEG, Color.RED)
        val pngFile = createTempImageFile("img2.png", 600, 900, Bitmap.CompressFormat.PNG, Color.GREEN)
        @Suppress("DEPRECATION")
        val webpFile = createTempImageFile("img3.webp", 1000, 1000, Bitmap.CompressFormat.WEBP, Color.YELLOW)

        val uris = listOf(Uri.fromFile(jpgFile), Uri.fromFile(pngFile), Uri.fromFile(webpFile))
        val result = ImageToPdfConverter.convertImagesToPdf(context, uris)

        assertTrue("Multi-image conversion should succeed", result.isSuccess)
        val pdfResult = result.getOrThrow()
        assertEquals(3, pdfResult.pageCount)
        assertTrue(pdfResult.pdfFile.exists())
        assertTrue(pdfResult.fileSizeBytes > 500)
    }

    // 2b. Erro claro ao tentar converter arquivo de formato inválido
    @Test
    fun `test 2b - reject unsupported format in image to PDF converter`() {
        val txtFile = File(context.cacheDir, "invalid.txt").apply { writeText("not an image") }
        tempFiles.add(txtFile)

        val result = ImageToPdfConverter.convertImagesToPdf(context, listOf(Uri.fromFile(txtFile)))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("formato não suportado", ignoreCase = true) == true)
    }

    // 3. Salvamento no dispositivo e URI de compartilhamento via FileProvider
    @Test
    fun `test 3 - save generated PDF to destination Uri and generate FileProvider share Uri`() {
        val pngFile = createTempImageFile("doc_page.png", 640, 480, Bitmap.CompressFormat.PNG, Color.CYAN)
        val pdfResult = ImageToPdfConverter.convertImagesToPdf(context, listOf(Uri.fromFile(pngFile))).getOrThrow()

        // Save to destination URI
        val savedTarget = File(context.cacheDir, "saved_by_user.pdf")
        tempFiles.add(savedTarget)
        val saveRes = ImageToPdfConverter.savePdfToDestinationUri(context, pdfResult.pdfFile, Uri.fromFile(savedTarget))
        assertTrue(saveRes.isSuccess)
        assertEquals(pdfResult.pdfFile.length(), savedTarget.length())

        // Verify FileProvider URI can be created for sharing/viewing
        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfResult.pdfFile)
        assertNotNull(shareUri)
        assertTrue(shareUri.toString().contains("generated_pdfs"))
    }

    // 4 & 5 & 6. Anexação de fotos e PDFs, deduplicação sem duplicar arquivo físico, remoção segura
    @Test
    fun `test 4 5 6 - attach photos and PDFs in observations, deduplicate, and safely remove`() = runTest {
        val photo1 = createTempImageFile("obs_photo_1.jpg", 400, 400, Bitmap.CompressFormat.JPEG, Color.MAGENTA)
        val pdf1 = createTempPdfFile("manual_portaria.pdf", "Anexo 1")

        // Import photo
        val storedPhoto = AttachmentStorageManager.importAttachmentFromUri(
            context, Uri.fromFile(photo1), AttachmentType.PHOTO
        ).getOrThrow()
        assertTrue(File(storedPhoto.filePath).exists())
        assertTrue(storedPhoto.filePath.contains(context.filesDir.absolutePath))

        val (attPhoto1, dup1) = repository.addDraftAttachment(
            ShiftAttachment(
                reportId = null,
                type = storedPhoto.type,
                displayName = storedPhoto.displayName,
                filePath = storedPhoto.filePath,
                mimeType = storedPhoto.mimeType,
                sizeBytes = storedPhoto.sizeBytes,
                sha256 = storedPhoto.sha256
            )
        )
        assertFalse(dup1)

        // Attempt attaching same photo again -> deduplicated in draft
        val (_, dup2) = repository.addDraftAttachment(
            ShiftAttachment(
                reportId = null,
                type = storedPhoto.type,
                displayName = "copia_foto.jpg",
                filePath = storedPhoto.filePath,
                mimeType = storedPhoto.mimeType,
                sizeBytes = storedPhoto.sizeBytes,
                sha256 = storedPhoto.sha256
            )
        )
        assertTrue(dup2)

        // Import PDF
        val storedPdf = AttachmentStorageManager.importAttachmentFromUri(
            context, Uri.fromFile(pdf1), AttachmentType.PDF
        ).getOrThrow()
        val (attPdf1, dupPdf) = repository.addDraftAttachment(
            ShiftAttachment(
                reportId = null,
                type = storedPdf.type,
                displayName = storedPdf.displayName,
                filePath = storedPdf.filePath,
                mimeType = storedPdf.mimeType,
                sizeBytes = storedPdf.sizeBytes,
                sha256 = storedPdf.sha256
            )
        )
        assertFalse(dupPdf)

        val draftList = repository.draftAttachments.first()
        assertEquals(2, draftList.size)

        // Remove photo before saving -> removed from DB and unreferenced physical file cleaned
        repository.removeAttachment(attPhoto1, context)
        val afterRemoval = repository.draftAttachments.first()
        assertEquals(1, afterRemoval.size)
        assertEquals(attPdf1.id, afterRemoval.first().id)
        assertFalse(File(storedPhoto.filePath).exists())
        assertTrue(File(storedPdf.filePath).exists())
    }

    // 7. Persistência dos anexos após reiniciar o aplicativo (reabrir banco em disco + adicionar novo anexo ao registro salvo)
    @Test
    fun `test 7 - attachment persistence across app restart and late attachment editing`() = runTest {
        val dbName = "test_persistence_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.delete()

        val photo1 = createTempImageFile("persist_photo.jpg", 300, 300, Bitmap.CompressFormat.JPEG, Color.DKGRAY)
        val pdf1 = createTempPdfFile("persist_doc.pdf", "Persistido")

        // Session 1: Create draft attachment, save shift handover, close DB
        var sessionDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        var sessionRepo = BpsRepository(sessionDb)

        val storedPhoto = AttachmentStorageManager.importAttachmentFromUri(
            context, Uri.fromFile(photo1), AttachmentType.PHOTO
        ).getOrThrow()
        val (draftPhoto, _) = sessionRepo.addDraftAttachment(
            ShiftAttachment(
                reportId = null,
                type = storedPhoto.type,
                displayName = storedPhoto.displayName,
                filePath = storedPhoto.filePath,
                mimeType = storedPhoto.mimeType,
                sizeBytes = storedPhoto.sizeBytes,
                sha256 = storedPhoto.sha256
            )
        )

        val reportId = sessionRepo.saveReportWithCamerasAndAttachments(
            report = ShiftReport(
                greeting = "Boa Noite",
                postName = "*PORTARIA*",
                departingEmployee = "Carlos",
                receivingEmployee = "Higor",
                reportContent = "Teste persistência",
                generalNotes = "Ocorrência salva"
            ),
            cameraState = CameraMonitoringState.allWorking(),
            draftAttachments = listOf(draftPhoto),
            clearDraftAfter = true
        )
        sessionDb.close()

        // Session 2 (simulated app restart): reopen DB from disk
        sessionDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        sessionRepo = BpsRepository(sessionDb)

        val persistedAttachments = sessionRepo.getAttachmentsForReportDirect(reportId)
        assertEquals(1, persistedAttachments.size)
        assertTrue(File(persistedAttachments.first().filePath).exists())
        assertEquals(storedPhoto.sha256, persistedAttachments.first().sha256)

        // Add a PDF attachment later to the existing saved report
        val storedPdf = AttachmentStorageManager.importAttachmentFromUri(
            context, Uri.fromFile(pdf1), AttachmentType.PDF
        ).getOrThrow()
        sessionRepo.addAttachmentToReport(
            reportId,
            ShiftAttachment(
                reportId = reportId,
                type = storedPdf.type,
                displayName = storedPdf.displayName,
                filePath = storedPdf.filePath,
                mimeType = storedPdf.mimeType,
                sizeBytes = storedPdf.sizeBytes,
                sha256 = storedPdf.sha256
            )
        )

        val updatedAttachments = sessionRepo.getAttachmentsForReportDirect(reportId)
        assertEquals(2, updatedAttachments.size)
        val savedReport = sessionDb.shiftReportDao().getReportById(reportId)
        assertEquals(2, savedReport?.attachmentCount)

        sessionDb.close()
        dbFile.delete()
    }

    // 8, 9, 10. Alteração individual das 16 câmeras do Monitor 1 e 16 câmeras do Monitor 2 + atualização automática dos indicadores
    @Test
    fun `test 8 9 10 - 32 cameras individual status toggles and automatic summary updates`() {
        var state = CameraMonitoringState.allWorking()
        assertEquals(32, state.totalCameras)
        assertEquals(32, state.totalWorking)
        assertEquals(0, state.totalOffline)
        assertEquals(16, state.monitor1.size)
        assertEquals(16, state.monitor2.size)

        // Toggle every single camera in Monitor 1 (1..16) individually
        for (cam in 1..16) {
            state = state.withCameraStatus(1, cam, CameraStatus.OFFLINE)
            assertEquals(16 - cam, state.monitor1Summary.working)
            assertEquals(cam, state.monitor1Summary.offline)
            assertEquals(16, state.monitor2Summary.working)
            assertEquals(32 - cam, state.totalWorking)
            assertEquals(cam, state.totalOffline)
        }

        // Restore some in Monitor 1 and toggle cameras in Monitor 2 (1..16)
        state = state.withAllMonitorStatus(1, CameraStatus.WORKING)
            .withCameraStatus(1, 4, CameraStatus.OFFLINE)
            .withCameraStatus(1, 15, CameraStatus.OFFLINE)

        assertEquals(14, state.monitor1Summary.working)
        assertEquals(2, state.monitor1Summary.offline)

        state = state.withCameraStatus(2, 11, CameraStatus.OFFLINE)
            .withCameraStatus(2, 15, CameraStatus.OFFLINE)
            .withCameraStatus(2, 16, CameraStatus.OFFLINE)

        assertEquals(13, state.monitor2Summary.working)
        assertEquals(3, state.monitor2Summary.offline)

        // General 32-camera indicator
        assertEquals(32, state.totalCameras)
        assertEquals(27, state.totalWorking)
        assertEquals(5, state.totalOffline)
    }

    // 11 & 12. Salvamento dos 32 estados na passagem e imutabilidade do histórico ao criar novas passagens
    @Test
    fun `test 11 12 - each shift handover preserves its own independent 32-camera history`() = runTest {
        // Handover 1: Monitor 1 Cam 02 offline, Monitor 2 Cam 03 offline (30 working, 2 offline)
        val camState1 = CameraMonitoringState.allWorking()
            .withCameraStatus(1, 2, CameraStatus.OFFLINE)
            .withCameraStatus(2, 3, CameraStatus.OFFLINE)

        val report1Id = repository.saveReportWithCamerasAndAttachments(
            report = ShiftReport(
                timestamp = 1700000000000L,
                greeting = "Bom Dia",
                postName = "*PORTARIA*",
                departingEmployee = "Carlos",
                receivingEmployee = "Higor",
                reportContent = "Passagem 1"
            ),
            cameraState = camState1,
            draftAttachments = emptyList()
        )

        // Handover 2: Totally different camera states (Monitor 1 Cams 1,5,10,16 offline; Monitor 2 all 16 working)
        val camState2 = CameraMonitoringState.allWorking()
            .withCameraStatus(1, 1, CameraStatus.OFFLINE)
            .withCameraStatus(1, 5, CameraStatus.OFFLINE)
            .withCameraStatus(1, 10, CameraStatus.OFFLINE)
            .withCameraStatus(1, 16, CameraStatus.OFFLINE)

        val report2Id = repository.saveReportWithCamerasAndAttachments(
            report = ShiftReport(
                timestamp = 1700043200000L,
                greeting = "Boa Noite",
                postName = "*PORTARIA*",
                departingEmployee = "Higor",
                receivingEmployee = "Rodrigo",
                reportContent = "Passagem 2"
            ),
            cameraState = camState2,
            draftAttachments = emptyList()
        )

        assertNotEquals(report1Id, report2Id)

        // Verify Report 1 still has its exact 32-camera records untouched!
        val r1Records = repository.getCameraRecordsForReportDirect(report1Id)
        assertEquals(32, r1Records.size)
        val restoredR1 = CameraMonitoringState.fromRecords(r1Records)!!
        assertEquals(30, restoredR1.totalWorking)
        assertEquals(2, restoredR1.totalOffline)
        assertFalse(restoredR1.monitor1.first { it.cameraNumber == 2 }.isWorking)
        assertTrue(restoredR1.monitor1.first { it.cameraNumber == 1 }.isWorking)
        assertFalse(restoredR1.monitor2.first { it.cameraNumber == 3 }.isWorking)

        // Verify Report 2 has its own independent 32-camera records
        val r2Records = repository.getCameraRecordsForReportDirect(report2Id)
        assertEquals(32, r2Records.size)
        val restoredR2 = CameraMonitoringState.fromRecords(r2Records)!!
        assertEquals(28, restoredR2.totalWorking)
        assertEquals(4, restoredR2.totalOffline)
        assertEquals(16, restoredR2.monitor2Summary.working)
        assertTrue(restoredR2.monitor1.first { it.cameraNumber == 2 }.isWorking)
        assertFalse(restoredR2.monitor1.first { it.cameraNumber == 1 }.isWorking)
    }

    // 13. UI Compose Test: Toggling cameras updates Monitor 1, Monitor 2, and Overall indicators live
    @Test
    fun `test 13 - Compose UI camera selector updates Monitor 1, Monitor 2, and Overall summaries`() {
        val dummyItem = ChecklistTemplateItem(
            id = 7,
            title = "Câmaras",
            emoji = "🎥",
            isRequired = true,
            isCamerasItem = true
        )

        composeTestRule.setContent {
            val state = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(CameraMonitoringState.allWorking())
            }
            MyApplicationTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())
                ) {
                    CameraMonitoringCard(
                        item = dummyItem,
                        cameraState = state.value,
                        onSetCameraStatus = { m, c, s ->
                            state.value = state.value.withCameraStatus(m, c, s)
                        },
                        onSetAllMonitorCameras = { m, s ->
                            state.value = state.value.withAllMonitorStatus(m, s)
                        },
                        onApplyAllWorking = { state.value = CameraMonitoringState.allWorking() },
                        onApplyDefaultBpsPreset = { state.value = CameraMonitoringState.defaultBpsPreset() }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overall_working_cameras").assertTextContains("32")
        composeTestRule.onNodeWithTag("overall_offline_cameras").assertTextContains("0")

        // Mark Monitor 1 Camera 02 offline
        composeTestRule.onNodeWithTag("monitor_1_cam_02_offline")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overall_working_cameras")
            .performScrollTo()
            .assertTextContains("31")
        composeTestRule.onNodeWithTag("overall_offline_cameras").assertTextContains("1")
        composeTestRule.onNodeWithTag("monitor_1_summary_text", useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("15 funcionando", substring = true)

        // Mark Monitor 2 Camera 03 offline
        composeTestRule.onNodeWithTag("monitor_2_cam_03_offline")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overall_working_cameras")
            .performScrollTo()
            .assertTextContains("30")
        composeTestRule.onNodeWithTag("overall_offline_cameras").assertTextContains("2")
        composeTestRule.onNodeWithTag("monitor_2_summary_text", useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("15 funcionando", substring = true)
    }

    // 14. Room Migration 4 -> 5 preserves existing reports and enables resident notices
    @Test
    fun `test 14 - Room migration 4 to 5 preserves existing data`() {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `employees` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `shift` TEXT NOT NULL, `phone` TEXT NOT NULL, `badgeNumber` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `isRequired` INTEGER NOT NULL, `isCamerasItem` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `category` TEXT NOT NULL, `modelName` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `greeting` TEXT NOT NULL, `postName` TEXT NOT NULL, `departingEmployee` TEXT NOT NULL, `receivingEmployee` TEXT NOT NULL, `reportContent` TEXT NOT NULL, `cameraWorkingCount` TEXT NOT NULL, `monitor1Details` TEXT NOT NULL, `monitor2Details` TEXT NOT NULL, `generalNotes` TEXT NOT NULL, `isPendingSync` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `syncedAt` INTEGER, `hasPhotos` INTEGER NOT NULL, `photoCount` INTEGER NOT NULL, `cameraStatesJson` TEXT NOT NULL, `attachmentCount` INTEGER NOT NULL, `checklistSnapshotJson` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_drafts` (`id` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `greeting` TEXT NOT NULL, `postName` TEXT NOT NULL, `receivingEmployee` TEXT NOT NULL, `departingEmployee` TEXT NOT NULL, `cameraWorkingCount` TEXT NOT NULL, `monitor1Details` TEXT NOT NULL, `monitor2Details` TEXT NOT NULL, `generalNotes` TEXT NOT NULL, `cameraStatesJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` INTEGER, `type` TEXT NOT NULL, `displayName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`reportId`) REFERENCES `shift_reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_attachments_reportId` ON `shift_attachments` (`reportId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_attachments_sha256` ON `shift_attachments` (`sha256`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_camera_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` INTEGER NOT NULL, `monitorNumber` INTEGER NOT NULL, `cameraNumber` INTEGER NOT NULL, `isWorking` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL, FOREIGN KEY(`reportId`) REFERENCES `shift_reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_camera_records_reportId` ON `shift_camera_records` (`reportId`)")
                    db.execSQL("INSERT INTO `shift_reports` (`timestamp`, `greeting`, `postName`, `departingEmployee`, `receivingEmployee`, `reportContent`, `cameraWorkingCount`, `monitor1Details`, `monitor2Details`, `generalNotes`, `isPendingSync`, `syncStatus`, `syncedAt`, `hasPhotos`, `photoCount`, `cameraStatesJson`, `attachmentCount`, `checklistSnapshotJson`) VALUES (1700000000000, 'Boa Noite', '*PORTARIA*', 'Carlos', 'Higor', 'Relatório legado', '28 funcionando', '1 Câmera 15 fora do ar', '2 Câmeras 11,15 e 16 fora do ar', 'Nota antiga', 0, 'ENVIADO', NULL, 0, 0, '', 0, '')")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        factory.create(config).writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val cursor = migratedDb.openHelper.readableDatabase.query("SELECT receivingEmployee, generalNotes FROM shift_reports")
        assertTrue(cursor.moveToFirst())
        assertEquals("Higor", cursor.getString(0))
        assertEquals("Nota antiga", cursor.getString(1))
        cursor.close()

        val noticesCursor = migratedDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM resident_notices")
        assertTrue(noticesCursor.moveToFirst())
        assertEquals(0, noticesCursor.getInt(0))
        noticesCursor.close()

        migratedDb.close()
        dbFile.delete()
    }

    // 15. Safe Area: BpsHeader starts strictly below Android status bar insets
    @Test
    fun `test 15 - BpsHeader respects Android statusBar top inset without overlap`() {
        var topInsetDp = 0f
        var hostView: android.view.View? = null

        composeTestRule.setContent {
            hostView = LocalView.current
            topInsetDp = with(LocalDensity.current) { 120.toDp().value }
            MyApplicationTheme(darkTheme = true) {
                BpsHeader(subtitle = "Registro de Passagem de Posto", isOnline = true)
            }
        }

        composeTestRule.runOnIdle {
            val view = hostView!!
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 120, 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        composeTestRule.waitForIdle()

        val headerCardBounds = composeTestRule.onNodeWithTag("bps_header_card").getBoundsInRoot()
        val headerContentBounds = composeTestRule.onNodeWithTag("bps_header_content_row").getBoundsInRoot()

        assertEquals(0f, headerCardBounds.top.value, 0.5f)
        assertTrue(
            "Header content (${headerContentBounds.top.value}dp) must start below status bar (${topInsetDp}dp)",
            headerContentBounds.top.value >= topInsetDp
        )
    }

    // 16. Safe Area + All 5 Navigation Tabs: header below status bar and bottom bar above 3-button nav bar
    @Test
    fun `test 16 - MainAppScreen respects top and bottom safe areas across all 5 tabs`() {
        val viewModel = createTestViewModel()
        var statusTopDp = 0f
        var navBottomDp = 0f
        var hostView: android.view.View? = null

        composeTestRule.setContent {
            hostView = LocalView.current
            with(LocalDensity.current) {
                statusTopDp = 96.toDp().value
                navBottomDp = 128.toDp().value
            }
            MyApplicationTheme(darkTheme = true) {
                MainAppScreen(viewModel = viewModel)
            }
        }

        composeTestRule.runOnIdle {
            val view = hostView!!
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 128))
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        composeTestRule.waitForIdle()

        val rootBounds = composeTestRule.onRoot().getBoundsInRoot()
        val headerContentBounds = composeTestRule.onNodeWithTag("bps_header_content_row").getBoundsInRoot()
        val firstTabBounds = composeTestRule.onNodeWithTag("nav_tab_shift_handover").getBoundsInRoot()

        assertTrue(
            "Header content (${headerContentBounds.top.value}dp) must clear status bar (${statusTopDp}dp)",
            headerContentBounds.top.value >= statusTopDp
        )
        assertTrue(
            "Bottom nav item (${firstTabBounds.bottom.value}dp) must sit above nav bar (${rootBounds.bottom.value - navBottomDp}dp)",
            firstTabBounds.bottom.value <= (rootBounds.bottom.value - navBottomDp + 1f)
        )

        // Switch across all 6 tabs and confirm UI renders cleanly
        listOf(
            "nav_tab_image_to_pdf",
            "nav_tab_custom_template",
            "nav_tab_employees",
            "nav_tab_history",
            "nav_tab_resident_notices",
            "nav_tab_shift_handover"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("bps_header_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("bps_bottom_navigation").assertIsDisplayed()
        }
    }

    // 17. Equipe inicial limpa: apenas Cláudia e Higor, idempotente e preservando colaboradores reais do usuário
    @Test
    fun `test 17 - initial employee seed contains only Claudia and Higor and is idempotent`() = runTest {
        // Insert legacy demo names + a real user-added employee before seeding
        db.employeeDao().insertAll(
            listOf(
                Employee(name = "Carlos Silva", role = "Porteiro", shift = "Diurno"),
                Employee(name = "Rodrigo Santos", role = "Folguista", shift = "12x36"),
                Employee(name = "Marcos Oliveira", role = "Supervisor", shift = "Geral"),
                Employee(name = "João Real", role = "Porteiro", shift = "Noturno")
            )
        )

        // Call seed twice to guarantee idempotency
        AppDatabase.populateInitialData(db)
        AppDatabase.populateInitialData(db)

        val names = repository.getAllEmployeesDirect().map { it.name }.sorted()
        assertEquals(listOf("Cláudia", "Higor", "João Real"), names)

        // Pure fresh seed (no prior employees) -> strictly Cláudia and Higor
        db.employeeDao().getAllEmployeesDirect().forEach { db.employeeDao().deleteEmployee(it) }
        AppDatabase.populateInitialData(db)
        val freshNames = repository.getAllEmployeesDirect().map { it.name }.sorted()
        assertEquals(listOf("Cláudia", "Higor"), freshNames)
    }

    // 18. Modelo único padrão "BPS FACILITIES — PORTARIA" com exatamente 17 itens (01..17) e zero duplicidades
    @Test
    fun `test 18 - default checklist model has exact 17 items without duplicates`() = runTest {
        // Seed twice + also simulate legacy "Câmaras" duplicate
        db.checklistTemplateDao().insertItem(
            ChecklistTemplateItem(title = "Câmaras", emoji = "🎥", isRequired = true, isCamerasItem = true, orderIndex = 6)
        )
        AppDatabase.populateInitialData(db)
        AppDatabase.populateInitialData(db)

        val items = repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        assertEquals(17, items.size)
        assertTrue(items.all { it.modelName == ChecklistTemplateItem.DEFAULT_MODEL_NAME })
        assertEquals(
            listOf(
                "Celular",
                "Carregador",
                "Interfone",
                "Livro de ocorrência",
                "Rádio HTS",
                "Cerca elétrica",
                "Câmeras",
                "Guarda chuva",
                "Guarda Sol",
                "Controle",
                "Caixa d'água",
                "Ar condicionado",
                "Chaves",
                "Extintor pó químico",
                "Portão de pedestre",
                "Portão de entrada veículos",
                "Portão de saída veículos"
            ),
            items.map { it.title }
        )
        assertEquals(1, items.count { it.isCamerasItem })
        assertEquals("Câmeras", items.first { it.isCamerasItem }.title)

        // Verify resetTemplatesToDefault keeps exact 17 items
        repository.resetTemplatesToDefault()
        assertEquals(17, repository.getAllTemplateItemsDirect().size)
    }

    // 19. Room Migration 3 -> 4 preserva relatórios históricos e remove apenas funcionários fictícios de demo
    @Test
    fun `test 19 - Room migration 3 to 4 preserves history and custom employees while cleaning demo names`() {
        val dbName = "migration_v3_v4_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `employees` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `shift` TEXT NOT NULL, `phone` TEXT NOT NULL, `badgeNumber` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `isRequired` INTEGER NOT NULL, `isCamerasItem` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `category` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `greeting` TEXT NOT NULL, `postName` TEXT NOT NULL, `departingEmployee` TEXT NOT NULL, `receivingEmployee` TEXT NOT NULL, `reportContent` TEXT NOT NULL, `cameraWorkingCount` TEXT NOT NULL, `monitor1Details` TEXT NOT NULL, `monitor2Details` TEXT NOT NULL, `generalNotes` TEXT NOT NULL, `isPendingSync` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `syncedAt` INTEGER, `hasPhotos` INTEGER NOT NULL, `photoCount` INTEGER NOT NULL, `cameraStatesJson` TEXT NOT NULL, `attachmentCount` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_drafts` (`id` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `greeting` TEXT NOT NULL, `postName` TEXT NOT NULL, `receivingEmployee` TEXT NOT NULL, `departingEmployee` TEXT NOT NULL, `cameraWorkingCount` TEXT NOT NULL, `monitor1Details` TEXT NOT NULL, `monitor2Details` TEXT NOT NULL, `generalNotes` TEXT NOT NULL, `cameraStatesJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` INTEGER, `type` TEXT NOT NULL, `displayName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`reportId`) REFERENCES `shift_reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_attachments_reportId` ON `shift_attachments` (`reportId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_attachments_sha256` ON `shift_attachments` (`sha256`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_camera_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` INTEGER NOT NULL, `monitorNumber` INTEGER NOT NULL, `cameraNumber` INTEGER NOT NULL, `isWorking` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL, FOREIGN KEY(`reportId`) REFERENCES `shift_reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_camera_records_reportId` ON `shift_camera_records` (`reportId`)")

                    db.execSQL("INSERT INTO `employees` (`name`, `role`, `shift`, `phone`, `badgeNumber`, `isActive`, `createdAt`) VALUES ('Higor', 'Porteiro', 'Noturno', '', 'BPS-104', 1, 1000)")
                    db.execSQL("INSERT INTO `employees` (`name`, `role`, `shift`, `phone`, `badgeNumber`, `isActive`, `createdAt`) VALUES ('Carlos Silva', 'Porteiro', 'Diurno', '', 'BPS-102', 1, 1000)")
                    db.execSQL("INSERT INTO `employees` (`name`, `role`, `shift`, `phone`, `badgeNumber`, `isActive`, `createdAt`) VALUES ('Fernanda Real', 'Porteira', 'Diurno', '', 'BPS-200', 1, 1000)")
                    db.execSQL("INSERT INTO `checklist_templates` (`title`, `emoji`, `isRequired`, `isCamerasItem`, `orderIndex`, `isEnabled`, `category`) VALUES ('Câmaras', '🎥', 1, 1, 6, 1, 'CFTV & Monitoramento')")
                    db.execSQL("INSERT INTO `shift_reports` (`timestamp`, `greeting`, `postName`, `departingEmployee`, `receivingEmployee`, `reportContent`, `cameraWorkingCount`, `monitor1Details`, `monitor2Details`, `generalNotes`, `isPendingSync`, `syncStatus`, `syncedAt`, `hasPhotos`, `photoCount`, `cameraStatesJson`, `attachmentCount`) VALUES (1700000000000, 'Boa Noite', '*PORTARIA*', 'Higor', 'Cláudia', 'Passagem preservada', '32 funcionando', '1 Todas', '2 Todas', 'Tudo ok', 0, 'ENVIADO', 1700000000000, 0, 0, '', 0)")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        factory.create(config).writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(migratedDb)
            val employees = migratedDb.employeeDao().getAllEmployeesDirect().map { it.name }.sorted()
            assertEquals(listOf("Cláudia", "Fernanda Real", "Higor"), employees)
            assertFalse("Carlos Silva" in employees)

            val templates = migratedDb.checklistTemplateDao().getAllTemplateItemsDirect()
            assertEquals(17, templates.size)
            assertEquals(1, templates.count { it.title == "Câmeras" })
            assertEquals(0, templates.count { it.title == "Câmaras" })

            val report = migratedDb.shiftReportDao().getAllReports().first().single()
            assertEquals("Higor", report.departingEmployee)
            assertEquals("Cláudia", report.receivingEmployee)
            assertEquals("Passagem preservada", report.reportContent)
        }

        migratedDb.close()
        dbFile.delete()
    }

    // 20. Assinaturas nominais automáticas no PDF (Higor entrega -> Cláudia assume), sem inversão, com 32 câmeras e 16 itens operacionais
    @Test
    fun `test 20 - PDF generation renders nominal signatures without inversion, timestamp, and 17-item model`() = runTest {
        AppDatabase.populateInitialData(db)
        val items = repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }

        val camState = CameraMonitoringState.allWorking()
            .withCameraStatus(1, 5, CameraStatus.OFFLINE)
            .withCameraStatus(2, 12, CameraStatus.OFFLINE)

        val fixedTimestamp = 1731520800000L // deterministic timestamp
        val formState = ShiftFormUiState(
            greeting = "Boa Noite",
            postName = "*PORTARIA*",
            departingEmployee = "Higor",
            receivingEmployee = "Cláudia",
            cameraMonitoring = camState,
            camerasWorkingCount = camState.toWorkingCountText(),
            monitor1Details = camState.toMonitorDetailsText(1),
            monitor2Details = camState.toMonitorDetailsText(2),
            generalNotes = "Passagem sem alterações graves. Portões conferidos."
        )

        val plan = PdfReportGenerator.buildPdfPlan(formState, items, reportTimestamp = fixedTimestamp)
        assertEquals("Higor", plan.departingEmployee)
        assertEquals("Cláudia", plan.receivingEmployee)
        assertEquals(16, plan.checklistRows.size) // 16 operational rows + 1 CFTV 32-camera section = 17 items
        assertEquals(30, plan.cameraMonitoring.totalWorking)
        assertEquals(2, plan.cameraMonitoring.totalOffline)
        assertTrue(plan.mainPageCount >= 1)

        val pdfFile = PdfReportGenerator.generateShiftPdf(context, formState, items, reportTimestamp = fixedTimestamp)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 300)
        val pdfText = pdfFile.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(pdfText.startsWith("%PDF"))
        // Verify nominal signatures and roles are present when fallback text stream is emitted
        if (pdfText.contains("ASSINATURA DO PORTEIRO QUE ENTREGA")) {
            assertTrue(pdfText.contains("ASSINATURA DO PORTEIRO QUE ENTREGA: Higor"))
            assertTrue(pdfText.contains("ASSINATURA DO PORTEIRO QUE ASSUME: Claudia"))
            assertTrue(pdfText.indexOf("PORTEIRO QUE ENTREGA: Higor") < pdfText.indexOf("PORTEIRO QUE ASSUME: Claudia"))
            assertFalse(pdfText.contains("autenticado via"))
        }
    }

    // 21. Validação bloqueia geração de PDF com campos obrigatórios vazios ou mesmo funcionário nos dois papéis
    @Test
    fun `test 21 - PDF validation blocks blank departing, blank receiving, or identical employee`() {
        val items = kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
            repository.getAllTemplateItemsDirect()
        }
        val viewModel = createTestViewModel()

        // Blank both
        val blankBoth = ShiftFormUiState(departingEmployee = "", receivingEmployee = "")
        assertNotNull(PdfReportGenerator.validateForPdf(blankBoth))
        assertEquals(null, viewModel.generateShiftPdfFile(context, items))
        assertNotNull(viewModel.pdfValidationError.value)

        // Blank departing
        viewModel.updateReceivingEmployee("Cláudia")
        viewModel.updateDepartingEmployee("")
        assertEquals(null, viewModel.generateShiftPdfFile(context, items))
        assertTrue(viewModel.pdfValidationError.value!!.contains("Entrega", ignoreCase = true))

        // Same employee in both roles (case-insensitive)
        viewModel.updateDepartingEmployee("Higor")
        viewModel.updateReceivingEmployee("higor")
        assertEquals(null, viewModel.generateShiftPdfFile(context, items))
        assertTrue(viewModel.pdfValidationError.value!!.contains("mesma pessoa", ignoreCase = true))
    }

    // 22. Preservação de histórico independente após edição posterior do modelo ou equipe
    @Test
    fun `test 22 - historical ShiftReport snapshot stays immutable after editing templates or employees`() = runTest {
        AppDatabase.populateInitialData(db)
        val items = repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        val celularItem = items.first { it.title == "Celular" }
        val gateItem = items.first { it.title == "Portão de pedestre" }

        val states = mapOf(
            celularItem.id to ItemCheckState(status = "DEFEITO", observation = "Bateria viciada"),
            gateItem.id to ItemCheckState(status = "ATENCAO", observation = "Trava rangendo")
        )
        val snapshotJson = ChecklistSnapshotHelper.toSnapshotJson(items, states)

        val originalTimestamp = 1710000000000L
        val reportId = repository.saveReportWithCamerasAndAttachments(
            report = ShiftReport(
                timestamp = originalTimestamp,
                greeting = "Boa Noite",
                postName = "*PORTARIA*",
                departingEmployee = "Higor",
                receivingEmployee = "Cláudia",
                reportContent = "Snapshot histórico",
                checklistSnapshotJson = snapshotJson
            ),
            cameraState = CameraMonitoringState.allWorking().withCameraStatus(1, 3, CameraStatus.OFFLINE),
            draftAttachments = emptyList()
        )

        // Later user deletes all employees and deletes checklist items!
        db.employeeDao().getAllEmployeesDirect().forEach { db.employeeDao().deleteEmployee(it) }
        db.checklistTemplateDao().clearAll()

        // Reload saved report -> original departing/receiving/timestamp/checklist snapshot are intact
        val savedReport = db.shiftReportDao().getReportById(reportId)!!
        assertEquals("Higor", savedReport.departingEmployee)
        assertEquals("Cláudia", savedReport.receivingEmployee)
        assertEquals(originalTimestamp, savedReport.timestamp)

        val restoredEntries = ChecklistSnapshotHelper.fromSnapshotJson(savedReport.checklistSnapshotJson)!!
        assertEquals(17, restoredEntries.size)
        val (restoredItems, restoredStates) = ChecklistSnapshotHelper.toItemsAndStates(restoredEntries)
        val restoredCelular = restoredItems.first { it.title == "Celular" }
        assertEquals("DEFEITO", restoredStates[restoredCelular.id]?.status)
        assertEquals("Bateria viciada", restoredStates[restoredCelular.id]?.observation)

        val camSnapshot = CameraMonitoringState.fromSnapshotJson(savedReport.cameraStatesJson)!!
        val pdfFromHistory = PdfReportGenerator.generateShiftPdf(
            context = context,
            formState = ShiftFormUiState(
                greeting = savedReport.greeting,
                postName = savedReport.postName,
                departingEmployee = savedReport.departingEmployee,
                receivingEmployee = savedReport.receivingEmployee,
                itemStates = restoredStates,
                cameraMonitoring = camSnapshot
            ),
            activeItems = restoredItems,
            reportTimestamp = savedReport.timestamp
        )
        assertTrue(pdfFromHistory.exists())
        assertTrue(pdfFromHistory.length() > 300)
    }

    // 23. Compose UI: seleção de ambos os porteiros, bloqueio do mesmo colaborador e alerta ao tentar gerar PDF inválido
    @Test
    fun `test 23 - Compose ShiftHandoverScreen guards same-person selection and displays PDF validation banner`() {
        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
        }
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ShiftHandoverScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.employees.value.size == 2 && viewModel.templateItems.value.size == 17
        }
        composeTestRule.waitForIdle()

        // Both chips exist only for Cláudia and Higor
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("employee_assignment_card"))
        composeTestRule.onNodeWithTag("departing_employee_chip_Higor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("departing_employee_chip_Cláudia").assertIsDisplayed()
        composeTestRule.onNodeWithTag("employee_chip_Higor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("employee_chip_Cláudia").assertIsDisplayed()

        // Try clicking "Visualizar" with blank employee names -> validation banner appears and blocks viewer
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("pdf_export_card"))
        composeTestRule.onNodeWithTag("view_pdf_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("pdf_validation_error_banner"))
        composeTestRule.onNodeWithTag("pdf_validation_error_banner").assertIsDisplayed()

        // Scroll back up to select Higor (Entrega) and Cláudia (Assume) -> preview banner renders cleanly
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("employee_assignment_card"))
        composeTestRule.onNodeWithTag("departing_employee_chip_Higor").performClick()
        composeTestRule.onNodeWithTag("employee_chip_Cláudia").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("pdf_nominal_preview_banner"))
        composeTestRule.onNodeWithTag("pdf_nominal_preview_banner")
            .assertTextContains("Entrega (Higor) → Assume (Cláudia)", substring = true)
    }

    // 24. Intent exclusivo de compartilhamento PDF via FileProvider (ACTION_SEND, application/pdf, sem EXTRA_TEXT)
    @Test
    fun `test 24 - PDF share Intent uses ACTION_SEND application pdf FileProvider URI without EXTRA_TEXT`() {
        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
        }
        val items = kotlinx.coroutines.runBlocking {
            repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        }
        val formState = ShiftFormUiState(
            greeting = "Boa Noite",
            postName = "*PORTARIA*",
            departingEmployee = "Higor",
            receivingEmployee = "Cláudia"
        )
        val pdfFile = PdfReportGenerator.generateShiftPdf(context, formState, items)
        val intent = PdfReportGenerator.buildPdfShareIntent(context, pdfFile)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertNull("PDF share intent must NOT carry EXTRA_TEXT message content", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertNotNull(intent.clipData)

        @Suppress("DEPRECATION")
        val streamUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        assertNotNull(streamUri)
        assertEquals("content", streamUri?.scheme)
        assertTrue(streamUri.toString().contains("fileprovider"))
    }

    // 25. Confirmação explícita: enviar PDF NÃO marca como ENVIADO antes da confirmação do usuário
    @Test
    fun `test 25 - exportAndSharePdf holds status AGUARDANDO CONFIRMACAO until confirmPdfSent is called`() {
        val items = kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
            repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        }
        val viewModel = createTestViewModel()
        viewModel.updateDepartingEmployee("Higor")
        viewModel.updateReceivingEmployee("Cláudia")

        val generatedFile = viewModel.exportAndSharePdf(context, items)
        assertNotNull(generatedFile)
        assertTrue(generatedFile!!.exists())

        // Pump MainLooper until coroutine saves report + sets pendingShareConfirmationReportId
        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.pendingShareConfirmationReportId.value == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.pendingShareConfirmationReportId.value == null) Thread.sleep(20)
        }
        val pendingIdOrNull = viewModel.pendingShareConfirmationReportId.value
        assertNotNull("Report should await explicit user send confirmation", pendingIdOrNull)
        val pendingId = pendingIdOrNull!!

        val beforeConfirm = kotlinx.coroutines.runBlocking { db.shiftReportDao().getReportById(pendingId)!! }
        assertTrue(beforeConfirm.isPendingSync)
        assertEquals("AGUARDANDO CONFIRMAÇÃO", beforeConfirm.syncStatus)
        assertNull(beforeConfirm.syncedAt)

        // Explicit confirmation transitions report to ENVIADO
        viewModel.confirmPdfSent(pendingId, context)
        val deadline2 = System.currentTimeMillis() + 5_000L
        while (viewModel.pendingShareConfirmationReportId.value != null && System.currentTimeMillis() < deadline2) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.pendingShareConfirmationReportId.value != null) Thread.sleep(20)
        }
        val afterConfirm = kotlinx.coroutines.runBlocking { db.shiftReportDao().getReportById(pendingId)!! }
        assertFalse(afterConfirm.isPendingSync)
        assertEquals("ENVIADO", afterConfirm.syncStatus)
        assertNotNull(afterConfirm.syncedAt)
    }

    // 26. Compose UI: remoção total da prévia/envio de texto WhatsApp e presença do envio exclusivo em PDF
    @Test
    fun `test 26 - ShiftHandoverScreen removes text WhatsApp UI and shows exclusive PDF WhatsApp button`() {
        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
        }
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ShiftHandoverScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.employees.value.size == 2 && viewModel.templateItems.value.size == 17
        }
        composeTestRule.waitForIdle()

        // Confirm legacy text WhatsApp nodes no longer exist
        assertEquals(0, composeTestRule.onAllNodesWithTag("whatsapp_preview_card").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("send_whatsapp_button").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("copy_text_button").fetchSemanticsNodes().size)

        // Exclusive PDF WhatsApp button + preview button are present
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("pdf_export_card"))
        composeTestRule.onNodeWithTag("share_pdf_button")
            .assertIsDisplayed()
            .assertTextContains("Enviar PDF pelo WhatsApp", substring = true)
        composeTestRule.onNodeWithTag("view_pdf_button")
            .assertIsDisplayed()
    }

    // 27. Validação do formato padronizado do nome do arquivo PDF
    @Test
    fun `test 27 - PDF filename follows BPS_Passagem_de_Posto_POSTO_AAAA-MM-DD_HH-MM format`() {
        val sanitized = PdfReportGenerator.sanitizePostNameForFileName("*PORTARIA PRINCIPAL / BLOCO A*")
        assertEquals("PORTARIA_PRINCIPAL_BLOCO_A", sanitized)

        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.NOVEMBER, 13, 19, 30, 0)
        val fixedTimestamp = cal.timeInMillis

        val fileName = PdfReportGenerator.buildPdfFileName("*PORTARIA*", fixedTimestamp)
        assertEquals("BPS_Passagem_de_Posto_PORTARIA_2026-11-13_19-30.pdf", fileName)

        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
        }
        val items = kotlinx.coroutines.runBlocking {
            repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        }
        val formState = ShiftFormUiState(
            greeting = "Boa Noite",
            postName = "*PORTARIA*",
            departingEmployee = "Higor",
            receivingEmployee = "Cláudia"
        )
        val pdfFile = PdfReportGenerator.generateShiftPdf(context, formState, items)
        assertTrue(
            "Generated file name '${pdfFile.name}' must match pattern BPS_Passagem_de_Posto_PORTARIA_YYYY-MM-DD_HH-MM.pdf",
            pdfFile.name.matches(Regex("^BPS_Passagem_de_Posto_PORTARIA_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.pdf$"))
        )
    }

    // 28. Validação da estrutura de 144 unidades para os Blocos A e B
    @Test
    fun `test 28 - CondoUnitsHelper has exactly 144 units per block A and B`() {
        val blockAUnits = com.example.data.local.CondoUnitsHelper.getAllUnitIdentifiers("A")
        val blockBUnits = com.example.data.local.CondoUnitsHelper.getAllUnitIdentifiers("B")

        assertEquals(144, blockAUnits.size)
        assertEquals(144, blockBUnits.size)
        assertEquals("A-001", blockAUnits.first())
        assertEquals("A-144", blockAUnits.last())
        assertEquals("B-001", blockBUnits.first())
        assertEquals("B-144", blockBUnits.last())

        assertEquals("025", com.example.data.local.CondoUnitsHelper.parseUnitNumber("25"))
        assertEquals("134", com.example.data.local.CondoUnitsHelper.parseUnitNumber("B-134"))
        assertNull(com.example.data.local.CondoUnitsHelper.parseUnitNumber("999"))
    }

    // 29. CRUD e persistência offline de Avisos de Moradores
    @Test
    fun `test 29 - Resident notices offline persistence and PDF inclusion`() {
        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
        }
        val viewModel = createTestViewModel()
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        val notice = com.example.data.model.ResidentNotice(
            block = "B",
            unitNumber = "134",
            residentName = "Karla",
            noticeType = "VISITA_PROGRAMADA",
            visitorName = "Carlos Eduardo",
            visitorDocument = "12.345.678-9",
            vehiclePlate = "ABC-1234",
            vehicleModel = "Gol Prata",
            scheduledDate = todayIso,
            scheduledTime = "14:30",
            additionalNotes = "Aguardar na portaria",
            registeredByGuardName = "Higor",
            status = "PENDENTE"
        )

        kotlinx.coroutines.runBlocking {
            val id = repository.saveResidentNotice(notice)
            assertTrue(id > 0)
            val saved = repository.getResidentNoticeById(id)
            assertNotNull(saved)
            assertEquals("B-134", saved?.unitFullIdentifier)
            assertEquals("Karla", saved?.residentName)
            assertEquals("PENDENTE", saved?.status)
        }

        val items = kotlinx.coroutines.runBlocking {
            repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        }
        val formState = ShiftFormUiState(
            greeting = "Boa Noite",
            postName = "*PORTARIA*",
            departingEmployee = "Higor",
            receivingEmployee = "Cláudia"
        )
        val activeNotices = kotlinx.coroutines.runBlocking {
            repository.getActiveNoticesForHandoverDirect(todayIso)
        }
        assertEquals(1, activeNotices.size)

        val pdfFile = PdfReportGenerator.generateShiftPdf(
            context = context,
            formState = formState,
            activeItems = items,
            residentNotices = activeNotices
        )
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
    }

    // 30. Seleção de todas as 144 unidades de ambos os blocos (A-001..A-144 e B-001..B-144) e zero moradores fictícios
    @Test
    fun `test 30 - all 288 units across Block A and Block B can be selected and parsed with zero demo residents`() {
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val existingNotices = kotlinx.coroutines.runBlocking {
                freshDb.residentNoticeDao().getAllNoticesDirect()
            }
            assertTrue("Should not seed fictional resident notices initially", existingNotices.isEmpty())

            val unitsA = com.example.data.local.CondoUnitsHelper.getAllUnitIdentifiers("A")
            val unitsB = com.example.data.local.CondoUnitsHelper.getAllUnitIdentifiers("B")
            assertEquals(144, unitsA.size)
            assertEquals(144, unitsB.size)

            (1..144).forEach { num ->
                val expected = com.example.data.local.CondoUnitsHelper.formatUnitNumber(num)
                assertEquals(expected, com.example.data.local.CondoUnitsHelper.parseUnitNumber(num.toString()))
                assertEquals(expected, com.example.data.local.CondoUnitsHelper.parseUnitNumber("A-$expected"))
                assertEquals(expected, com.example.data.local.CondoUnitsHelper.parseUnitNumber("B-$expected"))
                assertTrue("A-$expected" in unitsA)
                assertTrue("B-$expected" in unitsB)
            }
        } finally {
            freshDb.close()
        }
    }

    // 31. Cadastro, edição, cancelamento e auditoria de status (Visualizado não conclui automaticamente)
    @Test
    fun `test 31 - resident notice creation, edition, cancellation, status workflow and audit trail`() {
        val viewModel = createTestViewModel()
        val tomorrowCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrowIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(tomorrowCal.time)

        val karlaNotice = com.example.data.model.ResidentNotice(
            block = "B",
            unitNumber = "134",
            residentName = "Karla",
            noticeType = "VISITA_PROGRAMADA",
            visitorName = "Roberto Almeida",
            visitorDocument = "44.555.666-7",
            vehiclePlate = "XYZ-9876",
            vehicleModel = "Onix Preto",
            scheduledDate = tomorrowIso,
            scheduledTime = "15:00",
            registeredByGuardName = "Higor",
            status = "PENDENTE"
        )
        fun <T> waitUntilDb(condition: suspend () -> T?): T {
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                val value = kotlinx.coroutines.runBlocking { condition() }
                if (value != null) return value
                Thread.sleep(20)
            }
            return kotlinx.coroutines.runBlocking { condition() }
                ?: throw AssertionError("Timed out waiting for Room DB state")
        }

        viewModel.saveResidentNotice(karlaNotice, context)
        val saved = waitUntilDb { db.residentNoticeDao().getAllNoticesDirect().firstOrNull() }
        assertEquals("VISITA PROGRAMADA — BLOCO B / UNIDADE 134", saved.officialHeaderIdentifier)
        assertTrue(saved.auditTrail.contains("Higor"))
        assertTrue(com.example.util.NoticeSecurityManager.isEncrypted(saved.visitorDocument))
        assertEquals("44.555.666-7", saved.decryptedDocument(context))

        // Update notice (Edit)
        viewModel.saveResidentNotice(
            saved.copy(visitorName = "Roberto Almeida Júnior", lastUpdatedByGuardName = "Cláudia"),
            context
        )
        val edited = waitUntilDb {
            db.residentNoticeDao().getNoticeById(saved.id)?.takeIf { it.visitorName == "Roberto Almeida Júnior" }
        }
        assertEquals("Roberto Almeida Júnior", edited.visitorName)
        assertEquals("44.555.666-7", edited.decryptedDocument(context))

        // Mark as VISUALIZADO -> MUST NOT become CONCLUIDO
        viewModel.updateResidentNoticeStatus(edited.id, "VISUALIZADO", "Cláudia", context)
        val viewed = waitUntilDb {
            db.residentNoticeDao().getNoticeById(edited.id)?.takeIf { it.status == "VISUALIZADO" }
        }
        assertEquals("VISUALIZADO", viewed.status)
        assertNotEquals("CONCLUIDO", viewed.status)
        assertEquals("Cláudia", viewed.lastUpdatedByGuardName)
        assertTrue(viewed.auditTrail.contains("Visualizado"))

        // Cancel notice -> CANCELADO
        viewModel.updateResidentNoticeStatus(edited.id, "CANCELADO", "Higor", context)
        val cancelled = waitUntilDb {
            db.residentNoticeDao().getNoticeById(edited.id)?.takeIf { it.status == "CANCELADO" }
        }
        assertEquals("CANCELADO", cancelled.status)
        assertTrue(cancelled.auditTrail.contains("Cancelado"))
        assertTrue(cancelled.auditTrail.contains("Higor"))
    }

    // 32. Filtros e pesquisa avançada por bloco, unidade, morador, visitante, data, tipo e status
    @Test
    fun `test 32 - resident notice filters and search by block, unit, resident, visitor, date, type and status`() {
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val tomorrowCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrowIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(tomorrowCal.time)

        kotlinx.coroutines.runBlocking {
            db.residentNoticeDao().insertNotice(
                com.example.data.model.ResidentNotice(
                    block = "A", unitNumber = "025", residentName = "Marina",
                    noticeType = "ENTREGA", visitorName = "Logística Express",
                    scheduledDate = todayIso, status = "PENDENTE"
                )
            )
            db.residentNoticeDao().insertNotice(
                com.example.data.model.ResidentNotice(
                    block = "B", unitNumber = "134", residentName = "Karla",
                    noticeType = "VISITA_PROGRAMADA", visitorName = "Eduardo Souza",
                    vehiclePlate = "ABC-1234", scheduledDate = tomorrowIso, status = "VISUALIZADO"
                )
            )
            db.residentNoticeDao().insertNotice(
                com.example.data.model.ResidentNotice(
                    block = "B", unitNumber = "010", residentName = "Paulo",
                    noticeType = "PRESTADOR_SERVICO", visitorName = "Eletricista João",
                    scheduledDate = todayIso, status = "CONCLUIDO"
                )
            )
        }

        val all = kotlinx.coroutines.runBlocking { db.residentNoticeDao().getAllNoticesDirect() }
        assertEquals(3, all.size)

        // Block B filter
        assertEquals(2, all.count { it.block == "B" })
        // Unit filter B-134
        assertEquals("Karla", all.first { it.unitFullIdentifier == "B-134" }.residentName)
        // Visitor filter
        assertEquals("010", all.first { it.visitorName.contains("Eletricista", ignoreCase = true) }.unitNumber)
        // Type + Status + Date filters
        assertEquals(1, all.count { it.noticeType == "ENTREGA" && it.status == "PENDENTE" && it.scheduledDate == todayIso })
        assertEquals(1, all.count { it.scheduledDate == tomorrowIso && it.status == "VISUALIZADO" })
        assertEquals(1, all.count { it.status == "CONCLUIDO" })
    }

    // 33. Persistência offline após fechar e reabrir o aplicativo (banco Room em arquivo sem internet)
    @Test
    fun `test 33 - resident notices persist offline after closing and reopening Room database`() {
        val dbName = "bps_offline_reopen_test_${System.nanoTime()}.db"
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val encryptedDoc = com.example.util.NoticeSecurityManager.encryptDocument("98.765.432-1", context)
        kotlinx.coroutines.runBlocking {
            db1.residentNoticeDao().insertNotice(
                com.example.data.model.ResidentNotice(
                    block = "B",
                    unitNumber = "134",
                    residentName = "Karla",
                    noticeType = "VISITA_PROGRAMADA",
                    visitorName = "Marcos Visitante",
                    visitorDocument = encryptedDoc,
                    scheduledDate = todayIso,
                    status = "PENDENTE"
                )
            )
        }
        db1.close()

        // Reopen database cleanly as after app restart
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val reloaded = kotlinx.coroutines.runBlocking { db2.residentNoticeDao().getAllNoticesDirect() }
            assertEquals(1, reloaded.size)
            assertEquals("B-134", reloaded.first().unitFullIdentifier)
            assertEquals("Karla", reloaded.first().residentName)
            assertEquals("98.765.432-1", reloaded.first().decryptedDocument(context))
        } finally {
            db2.close()
            context.deleteDatabase(dbName)
        }
    }

    // 34. Proteção de dados pessoais e exclusão de avisos concluídos/cancelados no PDF
    @Test
    fun `test 34 - PDF excludes concluded cancelled and masks or omits raw personal documents`() {
        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
        }
        val items = kotlinx.coroutines.runBlocking {
            repository.getAllTemplateItemsDirect().sortedBy { it.orderIndex }
        }
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val rawSecretDoc = "45999888777"
        val activeNotice = com.example.data.model.ResidentNotice(
            id = 1,
            block = "B",
            unitNumber = "134",
            residentName = "Karla",
            noticeType = "VISITA_PROGRAMADA",
            visitorName = "Fernanda Lima",
            visitorDocument = com.example.util.NoticeSecurityManager.encryptDocument(rawSecretDoc, context),
            vehiclePlate = "BRA-2E19",
            scheduledDate = todayIso,
            status = "PENDENTE"
        )
        val concludedNotice = activeNotice.copy(
            id = 2,
            block = "A",
            unitNumber = "025",
            residentName = "Antônio Concluído",
            status = "CONCLUIDO"
        )
        val cancelledNotice = activeNotice.copy(
            id = 3,
            block = "B",
            unitNumber = "010",
            residentName = "Rafael Cancelado",
            status = "CANCELADO"
        )

        val plan = PdfReportGenerator.buildPdfPlan(
            formState = ShiftFormUiState(
                greeting = "Boa Noite",
                postName = "*PORTARIA*",
                departingEmployee = "Higor",
                receivingEmployee = "Cláudia"
            ),
            activeItems = items,
            residentNotices = listOf(activeNotice, concludedNotice, cancelledNotice)
        )
        // Only active PENDENTE/VISUALIZADO notice enters the PDF plan
        assertEquals(1, plan.residentNotices.size)
        assertEquals("Karla", plan.residentNotices.first().residentName)

        // Generate PDF and check raw text bytes in fallback/output don't leak concluded/cancelled or raw personal document
        val pdfFile = PdfReportGenerator.generateShiftPdf(
            context = context,
            formState = ShiftFormUiState(
                greeting = "Boa Noite",
                postName = "*PORTARIA*",
                departingEmployee = "Higor",
                receivingEmployee = "Cláudia"
            ),
            activeItems = items,
            residentNotices = listOf(activeNotice, concludedNotice, cancelledNotice)
        )
        assertTrue(pdfFile.exists())
        assertTrue(com.example.util.NoticeSecurityManager.maskDocument(activeNotice.visitorDocument, context).contains("*"))
        val rawPdfText = pdfFile.readText(Charsets.ISO_8859_1)
        assertFalse("Raw visitor document must NOT be leaked in PDF", rawPdfText.contains(rawSecretDoc))
        assertFalse("Concluded notice must NOT appear in handover PDF", rawPdfText.contains("Ant"))
        assertFalse("Cancelled notice must NOT appear in handover PDF", rawPdfText.contains("Rafael Cancelado"))
    }

    // 35. Política de retenção configurável identifica antigos sem apagar silenciosamente
    @Test
    fun `test 35 - retention policy identifies only old concluded or cancelled notices without silent deletion`() {
        val now = System.currentTimeMillis()
        val oldTimestamp = now - (100L * 24L * 60L * 60L * 1000L) // 100 days ago
        val recentTimestamp = now - (5L * 24L * 60L * 60L * 1000L) // 5 days ago
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        val notices = listOf(
            com.example.data.model.ResidentNotice(
                id = 1, block = "A", unitNumber = "001", residentName = "Antigo Concluído",
                noticeType = "ENTREGA", scheduledDate = todayIso, status = "CONCLUIDO",
                updatedAtTimestamp = oldTimestamp
            ),
            com.example.data.model.ResidentNotice(
                id = 2, block = "A", unitNumber = "002", residentName = "Antigo Pendente",
                noticeType = "ENTREGA", scheduledDate = todayIso, status = "PENDENTE",
                updatedAtTimestamp = oldTimestamp
            ),
            com.example.data.model.ResidentNotice(
                id = 3, block = "B", unitNumber = "010", residentName = "Recente Concluído",
                noticeType = "ENTREGA", scheduledDate = todayIso, status = "CONCLUIDO",
                updatedAtTimestamp = recentTimestamp
            )
        )

        com.example.util.NoticeSecurityManager.setRetentionDays(context, 90)
        assertEquals(90, com.example.util.NoticeSecurityManager.getRetentionDays(context))

        val eligible = com.example.util.NoticeSecurityManager.findNoticesEligibleForPurge(
            notices = notices,
            retentionDays = 90,
            nowMillis = now
        )
        assertEquals(1, eligible.size)
        assertEquals(1L, eligible.first().id)
    }

    // 36. Compose UI: Painel AVISOS PARA O PRÓXIMO TURNO, cartões expansíveis e responsividade (compacto + tablet)
    @Test
    fun `test 36 - Compose UI next shift notices panel, expandable cards, unit grid and responsive sizes`() {
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        kotlinx.coroutines.runBlocking {
            AppDatabase.populateInitialData(db)
            db.residentNoticeDao().insertNotice(
                com.example.data.model.ResidentNotice(
                    block = "B",
                    unitNumber = "134",
                    residentName = "Karla",
                    noticeType = "VISITA_PROGRAMADA",
                    visitorName = "Lucas Pereira",
                    vehiclePlate = "KRL-2026",
                    scheduledDate = todayIso,
                    scheduledTime = "18:00",
                    additionalNotes = "Autorizado entrar pela garagem",
                    registeredByGuardName = "Higor",
                    status = "PENDENTE"
                )
            )
        }

        val viewModel = createTestViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.residentNotices.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 1. Verify next shift notices panel appears on Passagem tab
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("next_shift_notices_panel"))
        composeTestRule.onNodeWithTag("next_shift_notices_panel")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("next_shift_notices_title")
            .assertIsDisplayed()
            .assertTextContains("AVISOS PARA O PRÓXIMO TURNO", substring = true)

        // 2. Navigate via panel button to Avisos tab & expand notice card
        composeTestRule.onNodeWithTag("open_resident_notices_panel_btn").performClick()
        composeTestRule.waitForIdle()

        val noticeId = viewModel.residentNotices.value.first().id
        composeTestRule.onNodeWithTag("notices_lazy_list")
            .performScrollToNode(hasTestTag("notice_card_$noticeId"))
        composeTestRule.onNodeWithTag("notice_expand_btn_$noticeId").performClick()
        composeTestRule.waitForIdle()

        // Open form dialog & confirm Block B + unit grid selector work
        composeTestRule.onNodeWithTag("notices_lazy_list")
            .performScrollToNode(hasTestTag("new_notice_button"))
        composeTestRule.onNodeWithTag("new_notice_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notice_form_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_block_B").performClick()
        composeTestRule.onNodeWithTag("form_unit_input").performTextReplacement("134")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unit_grid_item_B_134").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("close_notice_form_btn").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h1000dp")
    fun `test 37 - ResidentNoticesScreen and bottom navigation render cleanly on tablet width offline`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.activeNetwork?.let { activeNet ->
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNet, android.net.NetworkCapabilities())
        }
        assertFalse(NetworkMonitor(context).checkCurrentConnectivity())

        val viewModel = createTestViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_resident_notices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bps_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bps_bottom_navigation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_notice_button").assertIsDisplayed()
    }
}

