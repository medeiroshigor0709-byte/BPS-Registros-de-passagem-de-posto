package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.CameraMonitoringState
import com.example.data.model.CameraStatus
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ResidentNotice
import com.example.data.model.ShiftReport
import com.example.data.repository.BpsRepository
import com.example.ui.components.CameraMonitoringCard
import com.example.ui.components.PdfViewerDialog
import com.example.ui.screens.BpsNavTab
import com.example.ui.screens.EmployeeEditDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MainAppScreen
import com.example.ui.screens.ResidentNoticeDetailDialog
import com.example.ui.screens.RetentionPolicyDialog
import com.example.ui.screens.TemplateItemEditDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BpsViewModel
import com.example.ui.viewmodel.BpsViewModelFactory
import com.example.util.AttachmentStorageManager
import com.example.util.NetworkMonitor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BpsGlobalResponsivenessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: BpsRepository
    private val viewModelStores = mutableListOf<ViewModelStore>()
    private val tempFiles = mutableListOf<File>()

    private fun createTestViewModel(): BpsViewModel {
        val store = ViewModelStore()
        viewModelStores.add(store)
        return ViewModelProvider(
            store,
            BpsViewModelFactory(repository, NetworkMonitor(context))
        )[BpsViewModel::class.java]
    }

    private fun waitUntilReady(timeoutMillis: Long = 15_000L, condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

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
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        viewModelStores.forEach { it.clear() }
        viewModelStores.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(50)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
        tempFiles.forEach { it.delete() }
        AttachmentStorageManager.getAttachmentsDir(context).deleteRecursively()
        File(context.filesDir, "generated_pdfs").deleteRecursively()
    }

    private fun createTempPdf(name: String): File {
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
        """.trimIndent()
        FileOutputStream(file).use { it.write(minimalPdf.toByteArray()) }
        tempFiles.add(file)
        return file
    }

    private fun assertFitsHorizontally(tag: String, maxWidthDp: Float) {
        val bounds = composeTestRule.onNodeWithTag(tag).getBoundsInRoot()
        assertTrue(
            "Node '$tag' left (${bounds.left.value}) clipped past 0dp",
            bounds.left.value >= -0.5f
        )
        assertTrue(
            "Node '$tag' right (${bounds.right.value}) overflows ${maxWidthDp}dp",
            bounds.right.value <= maxWidthDp + 0.5f
        )
    }

    private fun assertMinTouchHeight(tag: String, minDp: Float = 47.5f) {
        val bounds = composeTestRule.onNodeWithTag(tag).getBoundsInRoot()
        val height = bounds.bottom.value - bounds.top.value
        assertTrue(
            "Node '$tag' height ($height dp) must be >= $minDp dp",
            height >= minDp
        )
    }

    // 1. Todas as 6 abas + header + bottom navigation em tela ultra-compacta 320x533 com fontScale = 1.3f
    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h533dp")
    fun `global 01 - all 6 tabs, header and bottom bar fit 320x533 with fontScale 1_3 without horizontal overflow`() {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        runBlocking {
            AppDatabase.populateInitialData(db)
            db.shiftReportDao().insertReport(
                ShiftReport(
                    id = 10L,
                    timestamp = System.currentTimeMillis(),
                    greeting = "Boa Noite",
                    postName = "*PORTARIA PRINCIPAL*",
                    departingEmployee = "Higor",
                    receivingEmployee = "Cláudia",
                    reportContent = "Passagem de turno realizada sem alterações críticas.",
                    isPendingSync = true,
                    syncStatus = "AGUARDANDO CONFIRMAÇÃO"
                )
            )
            db.residentNoticeDao().insertNotice(
                ResidentNotice(
                    id = 20L,
                    block = "B",
                    unitNumber = "134",
                    residentName = "Karla Fernanda Guimarães",
                    noticeType = "VISITA_PROGRAMADA",
                    visitorName = "Eduardo Alcântara",
                    scheduledDate = todayIso,
                    scheduledTime = "14:00",
                    status = "PENDENTE"
                )
            )
        }

        val viewModel = createTestViewModel()
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                MyApplicationTheme(darkTheme = true) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
        waitUntilReady {
            viewModel.templateItems.value.isNotEmpty() &&
                viewModel.employees.value.isNotEmpty() &&
                viewModel.residentNotices.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Verify header & all 6 bottom navigation tabs fit cleanly within 320dp
        composeTestRule.onNodeWithTag("bps_header_card").assertIsDisplayed()
        assertFitsHorizontally("bps_header_card", 320f)
        composeTestRule.onNodeWithTag("bps_bottom_navigation").assertIsDisplayed()
        assertFitsHorizontally("bps_bottom_navigation", 320f)

        BpsNavTab.values().forEach { tab ->
            assertFitsHorizontally("nav_tab_${tab.name.lowercase()}", 320f)
        }

        // Tab 1: Passagem (pending banner + handover list)
        assertFitsHorizontally("pending_sync_banner", 320f)
        composeTestRule.onNodeWithTag("shift_handover_list")
            .performScrollToNode(hasTestTag("pdf_export_card"))
        composeTestRule.waitForIdle()
        assertFitsHorizontally("share_pdf_button", 320f)
        assertMinTouchHeight("share_pdf_button", 48f)
        assertFitsHorizontally("view_pdf_button", 320f)
        assertMinTouchHeight("view_pdf_button", 47.5f)
        assertFitsHorizontally("save_report_button", 320f)
        assertFitsHorizontally("save_offline_room_button", 320f)

        // Tab 2: Img/PDF
        composeTestRule.onNodeWithTag("nav_tab_image_to_pdf").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("img_to_pdf_selection_card").assertIsDisplayed()
        assertFitsHorizontally("img_to_pdf_selection_card", 320f)
        composeTestRule.onNodeWithTag("select_images_gallery_btn").assertIsDisplayed()
        assertFitsHorizontally("select_images_gallery_btn", 320f)
        assertMinTouchHeight("select_images_gallery_btn", 47.5f)

        // Tab 3: Modelo
        composeTestRule.onNodeWithTag("nav_tab_custom_template").performClick()
        composeTestRule.waitForIdle()
        assertFitsHorizontally("add_template_item_button", 320f)
        assertMinTouchHeight("add_template_item_button", 47.5f)
        assertFitsHorizontally("reset_template_button", 320f)
        assertMinTouchHeight("reset_template_button", 47.5f)
        val firstTemplateId = viewModel.templateItems.value.first().id
        composeTestRule.onNodeWithTag("edit_item_$firstTemplateId")
            .performScrollTo()
            .assertIsDisplayed()
        assertFitsHorizontally("template_item_card_$firstTemplateId", 320f)
        assertMinTouchHeight("edit_item_$firstTemplateId", 47.5f)
        assertMinTouchHeight("delete_item_$firstTemplateId", 47.5f)

        // Tab 4: Equipe
        composeTestRule.onNodeWithTag("nav_tab_employees").performClick()
        composeTestRule.waitForIdle()
        assertFitsHorizontally("add_employee_button", 320f)
        assertMinTouchHeight("add_employee_button", 47.5f)
        val firstEmpId = viewModel.employees.value.first().id
        composeTestRule.onNodeWithTag("employee_card_$firstEmpId").performScrollTo()
        assertFitsHorizontally("employee_card_$firstEmpId", 320f)

        // Tab 5: Histórico
        composeTestRule.onNodeWithTag("nav_tab_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_report_card_10")
            .performScrollTo()
            .assertIsDisplayed()
        assertFitsHorizontally("history_report_card_10", 320f)
        composeTestRule.onNodeWithTag("history_share_pdf_btn_10").performScrollTo()
        assertFitsHorizontally("history_share_pdf_btn_10", 320f)
        assertFitsHorizontally("history_confirm_sent_btn_10", 320f)
        assertFitsHorizontally("history_view_btn_10", 320f)
        assertMinTouchHeight("history_share_pdf_btn_10", 47.5f)

        // Tab 6: Avisos
        composeTestRule.onNodeWithTag("nav_tab_resident_notices").performClick()
        composeTestRule.waitForIdle()
        assertFitsHorizontally("new_notice_button", 320f)
        assertMinTouchHeight("new_notice_button", 47.5f)
        assertFitsHorizontally("toggle_filters_btn", 320f)
        assertFitsHorizontally("privacy_retention_btn", 320f)
        composeTestRule.onNodeWithTag("notices_lazy_list")
            .performScrollToNode(hasTestTag("notice_card_20"))
        composeTestRule.waitForIdle()
        assertFitsHorizontally("notice_card_20", 320f)
        assertFitsHorizontally("notice_expand_btn_20", 320f)
        assertFitsHorizontally("notice_detail_btn_20", 320f)
        assertFitsHorizontally("notice_quick_done_btn_20", 320f)
        assertFitsHorizontally("notice_duplicate_btn_20", 320f)
        assertFitsHorizontally("notice_edit_btn_20", 320f)
        assertFitsHorizontally("notice_delete_btn_20", 320f)
        assertMinTouchHeight("notice_delete_btn_20", 47.5f)
    }

    // 2. Tablet 840x1000: largura do conteúdo principal centralizada até 840dp + navegação acessível
    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h1000dp")
    fun `global 02 - MainAppScreen caps content width cleanly on 840x1000 tablet`() {
        runBlocking { AppDatabase.populateInitialData(db) }
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppScreen(viewModel = viewModel)
            }
        }
        waitUntilReady { viewModel.templateItems.value.isNotEmpty() }
        composeTestRule.waitForIdle()

        val rootBounds = composeTestRule.onRoot().getBoundsInRoot()
        val listBounds = composeTestRule.onNodeWithTag("shift_handover_list").getBoundsInRoot()
        assertTrue("Root width should be 840dp", rootBounds.right.value <= 841f)
        assertTrue(
            "Tablet list width (${listBounds.right.value - listBounds.left.value}) must be <= 840dp",
            (listBounds.right.value - listBounds.left.value) <= 841f
        )
        composeTestRule.onNodeWithTag("bps_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bps_bottom_navigation").assertIsDisplayed()
    }

    // 3. Card de CFTV 32 câmeras com câmeras fora do ar em 320x533 e fontScale = 1.3f
    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h533dp")
    fun `global 03 - CameraMonitoringCard with offline badges and quick presets fits 320dp with fontScale 1_3`() {
        val offlineCameras = CameraMonitoringState.defaultBpsPreset().let { base ->
            base.copy(
                monitor1 = base.monitor1.map {
                    if (it.cameraNumber in 1..4) it.copy(status = CameraStatus.OFFLINE) else it
                }
            )
        }
        val item = ChecklistTemplateItem(
            id = 7L,
            title = "Monitoramento de Câmeras CFTV",
            emoji = "🎥",
            isRequired = true,
            isCamerasItem = true
        )

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                MyApplicationTheme(darkTheme = true) {
                    CameraMonitoringCard(
                        item = item,
                        cameraState = offlineCameras,
                        onSetCameraStatus = { _, _, _ -> },
                        onSetAllMonitorCameras = { _, _ -> },
                        onApplyAllWorking = {},
                        onApplyDefaultBpsPreset = {},
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertFitsHorizontally("cameras_cftv_card", 320f)
        composeTestRule.onNodeWithTag("preset_all_cameras_ok").performScrollTo()
        assertFitsHorizontally("preset_all_cameras_ok", 320f)
        assertMinTouchHeight("preset_all_cameras_ok", 47.5f)
        composeTestRule.onNodeWithTag("preset_bps_default").performScrollTo()
        assertFitsHorizontally("preset_bps_default", 320f)
        assertMinTouchHeight("preset_bps_default", 47.5f)
        composeTestRule.onNodeWithTag("monitor_1_all_ok_btn").performScrollTo()
        assertFitsHorizontally("monitor_1_all_ok_btn", 320f)
        assertMinTouchHeight("monitor_1_all_ok_btn", 47.5f)
        composeTestRule.onNodeWithTag("monitor_1_cam_01").performScrollTo()
        assertFitsHorizontally("monitor_1_cam_01", 320f)
        assertMinTouchHeight("monitor_1_cam_01_working", 47.5f)
        assertMinTouchHeight("monitor_1_cam_01_offline", 47.5f)
    }

    // 4. Visualizador de PDF interno (PdfViewerDialog) em 320x533 e com barras do sistema em 360x640
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp")
    fun `global 04 - PdfViewerDialog respects system bars insets and keeps footer buttons accessible`() {
        val pdfFile = createTempPdf("BPS_Teste_Responsivo.pdf")
        var statusTopPx = 0
        var navBottomPx = 0
        var statusTopDp = 0f
        var navBottomDp = 0f

        composeTestRule.setContent {
            val density = LocalDensity.current
            statusTopPx = with(density) { 28.dp.roundToPx() }
            navBottomPx = with(density) { 48.dp.roundToPx() }
            statusTopDp = with(density) { statusTopPx.toDp().value }
            navBottomDp = with(density) { navBottomPx.toDp().value }

            MyApplicationTheme(darkTheme = true) {
                PdfViewerDialog(
                    pdfFile = pdfFile,
                    onDismiss = {},
                    onSaveClick = {},
                    onShareClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val dialogWindow = ShadowDialog.getLatestDialog()?.window
        assertNotNull("Expected PdfViewerDialog window", dialogWindow)
        composeTestRule.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusTopPx, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBottomPx))
                .build()
            ViewCompat.dispatchApplyWindowInsets(dialogWindow!!.decorView, insets)
        }
        composeTestRule.waitForIdle()

        val bounds = composeTestRule.onNodeWithTag("pdf_viewer_dialog").getBoundsInRoot()
        assertTrue("PdfViewerDialog top (${bounds.top.value}) clears status bar ($statusTopDp)", bounds.top.value >= statusTopDp - 1f)
        assertTrue("PdfViewerDialog bottom (${bounds.bottom.value}) clears nav bar (${640f - navBottomDp})", bounds.bottom.value <= (640f - navBottomDp + 1f))
        assertFitsHorizontally("pdf_viewer_dialog", 360f)
        assertMinTouchHeight("close_pdf_viewer_button", 47.5f)
        val saveBounds = composeTestRule.onNode(hasText("Salvar") and hasClickAction()).getBoundsInRoot()
        assertTrue("PdfViewerDialog Salvar height (${saveBounds.bottom.value - saveBounds.top.value}) >= 47.5dp", (saveBounds.bottom.value - saveBounds.top.value) >= 47.5f)
        val shareBounds = composeTestRule.onNode(hasText("Enviar PDF") and hasClickAction()).getBoundsInRoot()
        assertTrue("PdfViewerDialog Enviar PDF height (${shareBounds.bottom.value - shareBounds.top.value}) >= 47.5dp", (shareBounds.bottom.value - shareBounds.top.value) >= 47.5f)
    }

    // 5. Modal de detalhes do Histórico (ShiftReportDetailDialog) com barras + IME em 360x640
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp")
    fun `global 05 - History ShiftReportDetailDialog adapts to system bars and virtual keyboard IME`() {
        val sampleReport = ShiftReport(
            id = 55L,
            timestamp = System.currentTimeMillis(),
            greeting = "Boa Noite",
            postName = "*PORTARIA*",
            departingEmployee = "Higor",
            receivingEmployee = "Cláudia",
            generalNotes = "Chave reserva da torre B entregue.",
            reportContent = "Resumo completo do turno noturno.",
            isPendingSync = true,
            syncStatus = "AGUARDANDO CONFIRMAÇÃO"
        )
        runBlocking {
            AppDatabase.populateInitialData(db)
            db.shiftReportDao().insertReport(sampleReport)
        }
        val viewModel = createTestViewModel()
        var statusTopPx = 0
        var navBottomPx = 0
        var imeBottomPx = 0
        var imeBottomDp = 0f

        composeTestRule.setContent {
            val density = LocalDensity.current
            statusTopPx = with(density) { 24.dp.roundToPx() }
            navBottomPx = with(density) { 48.dp.roundToPx() }
            imeBottomPx = with(density) { 230.dp.roundToPx() }
            imeBottomDp = with(density) { imeBottomPx.toDp().value }

            MyApplicationTheme(darkTheme = true) {
                HistoryScreen(viewModel = viewModel)
            }
        }
        waitUntilReady { viewModel.reportsHistory.value.isNotEmpty() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("history_view_btn_55").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_detail_dialog_55").assertIsDisplayed()

        val dialogWindow = ShadowDialog.getLatestDialog()?.window
        assertNotNull("Expected ShiftReportDetailDialog window", dialogWindow)
        composeTestRule.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusTopPx, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBottomPx))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottomPx))
                .build()
            ViewCompat.dispatchApplyWindowInsets(dialogWindow!!.decorView, insets)
        }
        composeTestRule.waitForIdle()

        val detailBounds = composeTestRule.onNodeWithTag("history_detail_dialog_55").getBoundsInRoot()
        assertTrue(
            "History detail dialog bottom (${detailBounds.bottom.value}) stays above IME (${640f - imeBottomDp})",
            detailBounds.bottom.value <= (640f - imeBottomDp + 1f)
        )
        assertFitsHorizontally("history_detail_dialog_55", 360f)
        assertMinTouchHeight("close_history_detail_btn", 47.5f)
        composeTestRule.onNodeWithText("Enviar PDF pelo WhatsApp").assertIsDisplayed()
    }

    // 6. Modais de Edição de Modelo e Funcionário em 320x533 com fontScale 1.3f (cabeçalho/rodapé fixos >= 48dp)
    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h533dp")
    fun `global 06 - TemplateItemEditDialog fits 320x533 with fontScale 1_3 and fixed footer buttons`() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                MyApplicationTheme(darkTheme = true) {
                    TemplateItemEditDialog(
                        item = ChecklistTemplateItem(id = 1L, title = "Rádio HTS", emoji = "📻"),
                        onDismiss = {},
                        onSave = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertFitsHorizontally("template_item_edit_dialog", 320f)
        val bounds = composeTestRule.onNodeWithTag("template_item_edit_dialog").getBoundsInRoot()
        assertTrue("Modal bottom (${bounds.bottom.value}) fits 533dp", bounds.bottom.value <= 533.5f)
        composeTestRule.onNodeWithTag("dialog_cancel_item_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_save_item_button").assertIsDisplayed()
        assertMinTouchHeight("dialog_cancel_item_button", 47.5f)
        assertMinTouchHeight("dialog_save_item_button", 47.5f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h533dp")
    fun `global 07 - EmployeeEditDialog fits 320x533 with fontScale 1_3 and fixed footer buttons`() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                MyApplicationTheme(darkTheme = true) {
                    EmployeeEditDialog(
                        employee = Employee(
                            id = 1L,
                            name = "Higor",
                            role = "Porteiro",
                            shift = "Noturno (19h às 07h)"
                        ),
                        onDismiss = {},
                        onSave = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertFitsHorizontally("employee_edit_dialog", 320f)
        val bounds = composeTestRule.onNodeWithTag("employee_edit_dialog").getBoundsInRoot()
        assertTrue("Modal bottom (${bounds.bottom.value}) fits 533dp", bounds.bottom.value <= 533.5f)
        composeTestRule.onNodeWithTag("employee_dialog_cancel_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("employee_dialog_save_button").assertIsDisplayed()
        assertMinTouchHeight("employee_dialog_cancel_button", 47.5f)
        assertMinTouchHeight("employee_dialog_save_button", 47.5f)
    }

    // 7. Modais de Detalhes de Aviso e Política de Retenção em 320x533 com fontScale = 1.3f
    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h533dp")
    fun `global 08 - ResidentNoticeDetailDialog fits 320x533 with fontScale 1_3 and wraps footer cleanly`() {
        val notice = ResidentNotice(
            id = 99L,
            block = "B",
            unitNumber = "134",
            residentName = "Karla Fernanda",
            noticeType = "PRESTADOR_SERVICO",
            visitorName = "Técnico Ar Condicionado",
            vehiclePlate = "BPS-2026",
            scheduledDate = "2026-11-20",
            scheduledTime = "09:30",
            status = "PENDENTE"
        )
        val employees = listOf(
            Employee(id = 1L, name = "Higor", role = "Porteiro", shift = "Noturno (19h às 07h)"),
            Employee(id = 2L, name = "Cláudia", role = "Porteiro", shift = "Diurno (07h às 19h)")
        )

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                MyApplicationTheme(darkTheme = true) {
                    ResidentNoticeDetailDialog(
                        notice = notice,
                        employees = employees,
                        onDismiss = {},
                        onStatusChange = { _, _ -> },
                        onEdit = {},
                        onDuplicate = {},
                        onDelete = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertFitsHorizontally("notice_detail_dialog_99", 320f)
        val bounds = composeTestRule.onNodeWithTag("notice_detail_dialog_99").getBoundsInRoot()
        assertTrue("Detail dialog bottom (${bounds.bottom.value}) fits 533dp", bounds.bottom.value <= 533.5f)
        assertFitsHorizontally("detail_duplicate_btn", 320f)
        assertFitsHorizontally("detail_edit_btn", 320f)
        assertFitsHorizontally("detail_delete_btn", 320f)
        assertMinTouchHeight("detail_duplicate_btn", 47.5f)
        assertMinTouchHeight("detail_edit_btn", 47.5f)
        assertMinTouchHeight("detail_delete_btn", 47.5f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h533dp")
    fun `global 09 - RetentionPolicyDialog fits 320x533 with fontScale 1_3 and keeps Concluir fixed visible`() {
        val eligible = listOf(
            ResidentNotice(
                id = 5L,
                block = "A",
                unitNumber = "010",
                residentName = "Marcos",
                noticeType = "ENTREGA",
                scheduledDate = "2026-01-01",
                status = "CONCLUIDO"
            )
        )

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                MyApplicationTheme(darkTheme = true) {
                    RetentionPolicyDialog(
                        currentRetentionDays = 90,
                        eligibleNotices = eligible,
                        onSelectRetentionDays = {},
                        onRequestPurge = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertFitsHorizontally("retention_policy_dialog", 320f)
        val bounds = composeTestRule.onNodeWithTag("retention_policy_dialog").getBoundsInRoot()
        assertTrue("Retention dialog bottom (${bounds.bottom.value}) fits 533dp", bounds.bottom.value <= 533.5f)
        composeTestRule.onNodeWithTag("retention_done_btn").assertIsDisplayed()
        assertMinTouchHeight("retention_done_btn", 47.5f)
        composeTestRule.onNodeWithTag("purge_expired_notices_btn").performScrollTo().assertIsDisplayed()
        assertMinTouchHeight("purge_expired_notices_btn", 47.5f)
    }
}
