package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.ShiftAttachment
import com.example.data.model.ShiftReport
import com.example.data.repository.BpsRepository
import com.example.ui.components.BpsHeader
import com.example.ui.components.CameraMonitoringCard
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BpsViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BpsRepository(db)
    }

    @After
    fun teardown() {
        db.close()
        tempFiles.forEach { it.delete() }
        AttachmentStorageManager.getAttachmentsDir(context).deleteRecursively()
        File(context.filesDir, "generated_pdfs").deleteRecursively()
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

    // 14. Room Migration 2 -> 3 preserves existing reports and employees
    @Test
    fun `test 14 - Room migration 2 to 3 preserves existing data`() {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.delete()

        // Create a v2 database manually with legacy schema and insert data
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `employees` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `shift` TEXT NOT NULL, `phone` TEXT NOT NULL, `badgeNumber` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `isRequired` INTEGER NOT NULL, `isCamerasItem` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `category` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `greeting` TEXT NOT NULL, `postName` TEXT NOT NULL, `departingEmployee` TEXT NOT NULL, `receivingEmployee` TEXT NOT NULL, `reportContent` TEXT NOT NULL, `cameraWorkingCount` TEXT NOT NULL, `monitor1Details` TEXT NOT NULL, `monitor2Details` TEXT NOT NULL, `generalNotes` TEXT NOT NULL, `isPendingSync` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `syncedAt` INTEGER, `hasPhotos` INTEGER NOT NULL, `photoCount` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shift_drafts` (`id` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `greeting` TEXT NOT NULL, `postName` TEXT NOT NULL, `receivingEmployee` TEXT NOT NULL, `departingEmployee` TEXT NOT NULL, `cameraWorkingCount` TEXT NOT NULL, `monitor1Details` TEXT NOT NULL, `monitor2Details` TEXT NOT NULL, `generalNotes` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("INSERT INTO `shift_reports` (`timestamp`, `greeting`, `postName`, `departingEmployee`, `receivingEmployee`, `reportContent`, `cameraWorkingCount`, `monitor1Details`, `monitor2Details`, `generalNotes`, `isPendingSync`, `syncStatus`, `syncedAt`, `hasPhotos`, `photoCount`) VALUES (1700000000000, 'Boa Noite', '*PORTARIA*', 'Carlos', 'Higor', 'Relatório legado', '28 funcionando', '1 Câmera 15 fora do ar', '2 Câmeras 11,15 e 16 fora do ar', 'Nota antiga', 0, 'ENVIADO', NULL, 0, 0)")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val legacyHelper = factory.create(config)
        legacyHelper.writableDatabase.close()

        // Open with Room v3 + MIGRATION_2_3
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // Trigger open & verify legacy record survived intact
        val cursor = migratedDb.openHelper.readableDatabase.query("SELECT receivingEmployee, generalNotes FROM shift_reports")
        assertTrue(cursor.moveToFirst())
        assertEquals("Higor", cursor.getString(0))
        assertEquals("Nota antiga", cursor.getString(1))
        cursor.close()

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
        val networkMonitor = NetworkMonitor(context)
        val viewModel = BpsViewModel(repository, networkMonitor)
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

        // Switch across all 5 tabs and confirm UI renders cleanly
        listOf(
            "nav_tab_image_to_pdf",
            "nav_tab_custom_template",
            "nav_tab_employees",
            "nav_tab_history",
            "nav_tab_shift_handover"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("bps_header_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("bps_bottom_navigation").assertIsDisplayed()
        }
    }
}
