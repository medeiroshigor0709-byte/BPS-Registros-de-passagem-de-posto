package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AttachmentType
import com.example.data.model.CameraChannelState
import com.example.data.model.CameraMonitoringState
import com.example.data.model.ShiftReport
import com.example.ui.components.ObservationAttachmentsSection
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.BpsSilver
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOk
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.BpsViewModel
import com.example.ui.viewmodel.ShiftFormUiState
import com.example.util.PdfReportGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reports by viewModel.reportsHistory.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    var reportToView by remember { mutableStateOf<ShiftReport?>(null) }
    var reportToDelete by remember { mutableStateOf<ShiftReport?>(null) }

    var filterSyncStatus by remember { mutableStateOf("TODOS") }

    val filteredReports = remember(reports, searchQuery, filterSyncStatus) {
        reports.filter { report ->
            val matchesQuery = searchQuery.isBlank() ||
                report.receivingEmployee.contains(searchQuery, ignoreCase = true) ||
                report.departingEmployee.contains(searchQuery, ignoreCase = true) ||
                report.postName.contains(searchQuery, ignoreCase = true) ||
                report.reportContent.contains(searchQuery, ignoreCase = true) ||
                report.generalNotes.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterSyncStatus) {
                "PENDENTES" -> report.isPendingSync
                "ENVIADOS" -> !report.isPendingSync
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📁 Histórico & Sincronização Room",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Consulte passagens anteriores, os 32 status de câmeras de cada turno e gerencie anexos vinculados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Search input
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por porteiro, posto ou ocorrência...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = BpsGold)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Sync Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pendingCount = reports.count { it.isPendingSync }
                    val sentCount = reports.count { !it.isPendingSync }

                    FilterChip(
                        selected = filterSyncStatus == "TODOS",
                        onClick = { filterSyncStatus = "TODOS" },
                        label = { Text("Todos (${reports.size})", fontSize = 12.sp) }
                    )

                    FilterChip(
                        selected = filterSyncStatus == "PENDENTES",
                        onClick = { filterSyncStatus = "PENDENTES" },
                        label = {
                            Text(
                                "⏳ Pendentes ($pendingCount)",
                                fontSize = 12.sp,
                                fontWeight = if (pendingCount > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (pendingCount > 0) Color(0xFFF59E0B) else Color.Unspecified
                            )
                        }
                    )

                    FilterChip(
                        selected = filterSyncStatus == "ENVIADOS",
                        onClick = { filterSyncStatus = "ENVIADOS" },
                        label = { Text("✅ Enviados ($sentCount)", fontSize = 12.sp) }
                    )
                }
            }

            if (filteredReports.isEmpty()) {
                item {
                    EmptyHistoryCard(hasQuery = searchQuery.isNotBlank())
                }
            } else {
                item {
                    Text(
                        text = "Registros Arquivados (${filteredReports.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(filteredReports, key = { it.id }) { report ->
                    HistoryReportCard(
                        report = report,
                        onSendWhatsApp = {
                            viewModel.sendToWhatsApp(context, report.reportContent, saveHistory = false)
                        },
                        onCopy = {
                            viewModel.copyToClipboard(context, report.reportContent)
                        },
                        onViewDetails = {
                            reportToView = report
                        },
                        onExportPdf = {
                            scope.launch {
                                val activeItems = viewModel.templateItems.value.filter { it.isEnabled }
                                val camSnapshot = viewModel.getCameraStateForReport(report)
                                val attachments = viewModel.getAttachmentsForReportDirect(report.id)
                                val tempState = ShiftFormUiState(
                                    greeting = report.greeting,
                                    postName = report.postName,
                                    receivingEmployee = report.receivingEmployee,
                                    departingEmployee = report.departingEmployee,
                                    cameraMonitoring = camSnapshot,
                                    camerasWorkingCount = report.cameraWorkingCount,
                                    monitor1Details = report.monitor1Details,
                                    monitor2Details = report.monitor2Details,
                                    generalNotes = report.generalNotes,
                                    observationAttachments = attachments
                                )
                                val file = PdfReportGenerator.generateShiftPdf(
                                    context = context,
                                    formState = tempState,
                                    activeItems = activeItems
                                )
                                PdfReportGenerator.sharePdf(context, file)
                            }
                        },
                        onDelete = {
                            reportToDelete = report
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // View & Edit Details Modal (with independent 32-camera snapshot + editable attachments)
    reportToView?.let { initialReport ->
        val latestReport = reports.firstOrNull { it.id == initialReport.id } ?: initialReport
        ShiftReportDetailDialog(
            report = latestReport,
            viewModel = viewModel,
            onDismiss = { reportToView = null }
        )
    }

    // Delete Confirmation
    reportToDelete?.let { report ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            icon = {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusError)
            },
            title = { Text("Excluir registro?") },
            text = { Text("Deseja realmente remover este registro de passagem de posto do histórico?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReport(report, context)
                        reportToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftReportDetailDialog(
    report: ShiftReport,
    viewModel: BpsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    val formattedDate = dateFormat.format(Date(report.timestamp))

    var cameraSnapshot by remember(report.id) {
        mutableStateOf(
            CameraMonitoringState.fromSnapshotJson(report.cameraStatesJson)
                ?: CameraMonitoringState.fromLegacyStrings(report.monitor1Details, report.monitor2Details)
        )
    }
    LaunchedEffect(report.id) {
        cameraSnapshot = viewModel.getCameraStateForReport(report)
    }

    val attachmentsFlow = remember(report.id) { viewModel.observeAttachmentsForReport(report.id) }
    val attachments by attachmentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editableNotes by remember(report.id) { mutableStateOf(report.generalNotes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .testTag("history_detail_dialog_${report.id}"),
            shape = RoundedCornerShape(18.dp),
            color = BpsDarkBg,
            border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = BpsGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Registro de Passagem #${report.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "📅 $formattedDate • ${report.postName.replace("*", "")}",
                                fontSize = 11.sp,
                                color = BpsGoldLight
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_history_detail_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Metadata
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(1.dp, Color(0xFF283548))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "👤 Passo o posto para: ${report.receivingEmployee}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (report.departingEmployee.isNotBlank()) {
                                Text(
                                    text = "🚪 Porteiro saindo: ${report.departingEmployee}",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            Text(
                                text = "📌 Status: ${report.syncStatus}",
                                fontSize = 12.sp,
                                color = BpsGoldLight
                            )
                        }
                    }

                    // 32-Camera Snapshot Recorded in THIS Handover (Immutable historical snapshot)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_cameras_snapshot_${report.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = BpsGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Monitoramento das 32 Câmeras (Registro Histórico)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BpsGoldLight
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Geral: ${cameraSnapshot.totalCameras} câmeras • 🟢 ${cameraSnapshot.totalWorking} funcionando • 🔴 ${cameraSnapshot.totalOffline} fora do ar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HistoricalMonitorGrid(
                                monitorNumber = 1,
                                working = cameraSnapshot.monitor1Summary.working,
                                offline = cameraSnapshot.monitor1Summary.offline,
                                channels = cameraSnapshot.monitor1
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HistoricalMonitorGrid(
                                monitorNumber = 2,
                                working = cameraSnapshot.monitor2Summary.working,
                                offline = cameraSnapshot.monitor2Summary.offline,
                                channels = cameraSnapshot.monitor2
                            )
                        }
                    }

                    // Editable Observations & Persistent Attachments
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(1.dp, Color(0xFF283548))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📝 Observações & Anexos do Registro",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BpsGoldLight
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editableNotes,
                                onValueChange = { editableNotes = it },
                                label = { Text("Observações deste turno") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_edit_notes_input"),
                                minLines = 2,
                                maxLines = 4
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateSavedReportNotes(report.id, editableNotes, context)
                                },
                                modifier = Modifier.testTag("history_save_notes_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BpsGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvar Observações", fontSize = 12.sp, color = BpsGoldLight)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFF283548))
                            Spacer(modifier = Modifier.height(10.dp))

                            ObservationAttachmentsSection(
                                attachments = attachments,
                                onAddPhotos = { uris ->
                                    viewModel.attachFilesToSavedReport(context, report.id, uris, AttachmentType.PHOTO)
                                },
                                onAddPdfs = { uris ->
                                    viewModel.attachFilesToSavedReport(context, report.id, uris, AttachmentType.PDF)
                                },
                                onRemoveAttachment = { att ->
                                    viewModel.removeAttachmentFromSavedReport(context, att)
                                },
                                testTagPrefix = "history_obs"
                            )
                        }
                    }

                    // Formatted WhatsApp Message
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Texto formatado do WhatsApp:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = report.reportContent,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                // Footer actions
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val activeItems = viewModel.templateItems.value.filter { it.isEnabled }
                                val tempState = ShiftFormUiState(
                                    greeting = report.greeting,
                                    postName = report.postName,
                                    receivingEmployee = report.receivingEmployee,
                                    departingEmployee = report.departingEmployee,
                                    cameraMonitoring = cameraSnapshot,
                                    camerasWorkingCount = report.cameraWorkingCount,
                                    monitor1Details = report.monitor1Details,
                                    monitor2Details = report.monitor2Details,
                                    generalNotes = editableNotes,
                                    observationAttachments = attachments
                                )
                                val file = PdfReportGenerator.generateShiftPdf(
                                    context = context,
                                    formState = tempState,
                                    activeItems = activeItems
                                )
                                PdfReportGenerator.sharePdf(context, file)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = BpsGold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar PDF", fontSize = 12.sp, color = BpsGoldLight)
                        }

                        Button(
                            onClick = {
                                viewModel.sendToWhatsApp(context, report.reportContent, saveHistory = false)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reenviar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoricalMonitorGrid(
    monitorNumber: Int,
    working: Int,
    offline: Int,
    channels: List<CameraChannelState>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1322), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "MONITOR $monitorNumber — 16 câmeras (🟢 $working OK | 🔴 $offline fora do ar)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE2E8F0)
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            channels.forEach { ch ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (ch.isWorking) StatusOk.copy(alpha = 0.2f) else StatusError.copy(alpha = 0.25f),
                    border = BorderStroke(
                        1.dp,
                        if (ch.isWorking) StatusOk.copy(alpha = 0.5f) else StatusError
                    )
                ) {
                    Text(
                        text = "${if (ch.isWorking) "🟢" else "🔴"} C${ch.cameraNumber.toString().padStart(2, '0')}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ch.isWorking) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryReportCard(
    report: ShiftReport,
    onSendWhatsApp: () -> Unit,
    onCopy: () -> Unit,
    onViewDetails: () -> Unit,
    onExportPdf: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale("pt", "BR"))
    val formattedDate = dateFormat.format(Date(report.timestamp))
    val camSnapshot = remember(report.cameraStatesJson, report.monitor1Details, report.monitor2Details) {
        CameraMonitoringState.fromSnapshotJson(report.cameraStatesJson)
            ?: CameraMonitoringState.fromLegacyStrings(report.monitor1Details, report.monitor2Details)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_report_card_${report.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = BpsGold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsGoldLight
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = report.postName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsSilver,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${report.greeting} passo o posto para: ${report.receivingEmployee}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Camera & Attachment badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0E1B2E),
                    border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = BpsGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${camSnapshot.totalWorking}/32 OK • ${camSnapshot.totalOffline} fora",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                if (report.attachmentCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = BpsGoldLight,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${report.attachmentCount} anexo(s)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BpsGoldLight
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Short snippet
            Text(
                text = report.reportContent,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onSendWhatsApp,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("history_view_btn_${report.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onExportPdf,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp), tint = BpsGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp, color = BpsGoldLight)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = StatusError.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryCard(hasQuery: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = BpsGold,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (hasQuery) "Nenhum resultado encontrado" else "Nenhuma passagem arquivada",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasQuery) "Tente buscar com outros termos." else "Os registros de passagem enviados pelo app serão guardados aqui automaticamente.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
