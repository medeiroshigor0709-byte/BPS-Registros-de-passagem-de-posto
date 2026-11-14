package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CondoUnitsHelper
import com.example.data.model.ResidentNotice
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldDark
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.BpsSilver
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOk
import com.example.ui.viewmodel.BpsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ResidentNoticesScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notices by viewModel.residentNotices.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTimeframeFilter by remember { mutableStateOf("TODOS") } // "TODOS", "HOJE", "PROXIMOS", "PENDENTES", "CONCLUIDOS"
    var selectedBlockFilter by remember { mutableStateOf("TODOS") } // "TODOS", "A", "B"
    var selectedTypeFilter by remember { mutableStateOf("TODOS") }

    var showFormDialog by remember { mutableStateOf(false) }
    var noticeToEdit by remember { mutableStateOf<ResidentNotice?>(null) }
    var noticeForDetails by remember { mutableStateOf<ResidentNotice?>(null) }
    var noticeToDuplicate by remember { mutableStateOf<ResidentNotice?>(null) }
    var noticeToDelete by remember { mutableStateOf<ResidentNotice?>(null) }

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    val filteredNotices = remember(
        notices,
        searchQuery,
        selectedTimeframeFilter,
        selectedBlockFilter,
        selectedTypeFilter,
        todayIso
    ) {
        notices.filter { notice ->
            // Block filter
            if (selectedBlockFilter != "TODOS" && notice.block != selectedBlockFilter) {
                return@filter false
            }

            // Type filter
            if (selectedTypeFilter != "TODOS" && notice.noticeType != selectedTypeFilter) {
                return@filter false
            }

            // Timeframe / Status filter
            val matchesTimeframe = when (selectedTimeframeFilter) {
                "HOJE" -> notice.scheduledDate == todayIso
                "PROXIMOS" -> notice.scheduledDate > todayIso && notice.status != "CANCELADO"
                "PENDENTES" -> notice.status == "PENDENTE"
                "CONCLUIDOS" -> notice.status == "CONCLUIDO"
                else -> true
            }
            if (!matchesTimeframe) return@filter false

            // Query filter
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim()
                val unitFull = "${notice.block}-${notice.unitNumber}"
                val match = unitFull.contains(q, ignoreCase = true) ||
                    notice.unitNumber.contains(q, ignoreCase = true) ||
                    notice.residentName.contains(q, ignoreCase = true) ||
                    notice.visitorName.contains(q, ignoreCase = true) ||
                    notice.vehiclePlate.contains(q, ignoreCase = true) ||
                    notice.additionalNotes.contains(q, ignoreCase = true)
                if (!match) return@filter false
            }

            true
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📢 Avisos de Moradores",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Visitas programadas, entregas, prestadores e recados dos Blocos A e B.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            noticeToEdit = null
                            showFormDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("new_notice_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF1E1400), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Novo Aviso", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Quick Search
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar unidade (ex: B-134), morador, visitante...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = BpsGold)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notice_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Block filter: [ TODOS ] [ BLOCO A ] [ BLOCO B ]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedBlockFilter == "TODOS",
                        onClick = { selectedBlockFilter = "TODOS" },
                        label = { Text("Todos os Blocos (288 un)", fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_block_all")
                    )
                    FilterChip(
                        selected = selectedBlockFilter == "A",
                        onClick = { selectedBlockFilter = "A" },
                        label = { Text("Bloco A (144 un)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BpsGoldDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_block_A")
                    )
                    FilterChip(
                        selected = selectedBlockFilter == "B",
                        onClick = { selectedBlockFilter = "B" },
                        label = { Text("Bloco B (144 un)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BpsGoldDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_block_B")
                    )
                }
            }

            // Timeframe / Status filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val countPending = notices.count { it.status == "PENDENTE" }
                    val countToday = notices.count { it.scheduledDate == todayIso }

                    FilterChip(
                        selected = selectedTimeframeFilter == "TODOS",
                        onClick = { selectedTimeframeFilter = "TODOS" },
                        label = { Text("Todos (${notices.size})", fontSize = 11.5.sp) },
                        modifier = Modifier.testTag("filter_timeframe_all")
                    )
                    FilterChip(
                        selected = selectedTimeframeFilter == "HOJE",
                        onClick = { selectedTimeframeFilter = "HOJE" },
                        label = { Text("Hoje ($countToday)", fontSize = 11.5.sp) },
                        modifier = Modifier.testTag("filter_timeframe_today")
                    )
                    FilterChip(
                        selected = selectedTimeframeFilter == "PROXIMOS",
                        onClick = { selectedTimeframeFilter = "PROXIMOS" },
                        label = { Text("Próximos", fontSize = 11.5.sp) },
                        modifier = Modifier.testTag("filter_timeframe_upcoming")
                    )
                    FilterChip(
                        selected = selectedTimeframeFilter == "PENDENTES",
                        onClick = { selectedTimeframeFilter = "PENDENTES" },
                        label = {
                            Text(
                                "Pendentes ($countPending)",
                                fontSize = 11.5.sp,
                                fontWeight = if (countPending > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (countPending > 0) Color(0xFFF59E0B) else Color.Unspecified
                            )
                        },
                        modifier = Modifier.testTag("filter_timeframe_pending")
                    )
                }
            }

            // Results count
            item {
                Text(
                    text = "Avisos Registrados (${filteredNotices.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (filteredNotices.isEmpty()) {
                item {
                    EmptyNoticesCard(hasFilter = searchQuery.isNotBlank() || selectedBlockFilter != "TODOS" || selectedTimeframeFilter != "TODOS") {
                        noticeToEdit = null
                        showFormDialog = true
                    }
                }
            } else {
                items(filteredNotices, key = { it.id }) { notice ->
                    ResidentNoticeCard(
                        notice = notice,
                        onViewDetails = { noticeForDetails = notice },
                        onEdit = {
                            noticeToEdit = notice
                            showFormDialog = true
                        },
                        onDuplicate = { noticeToDuplicate = notice },
                        onQuickStatusChange = { newStatus ->
                            val currentGuard = employees.firstOrNull()?.name ?: "Porteiro"
                            viewModel.updateResidentNoticeStatus(notice.id, newStatus, currentGuard, context)
                        },
                        onDelete = { noticeToDelete = notice }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal de Cadastro / Edição
    if (showFormDialog) {
        ResidentNoticeFormDialog(
            initialNotice = noticeToEdit,
            employees = employees,
            onDismiss = {
                showFormDialog = false
                noticeToEdit = null
            },
            onSave = { notice ->
                viewModel.saveResidentNotice(notice, context)
                showFormDialog = false
                noticeToEdit = null
            }
        )
    }

    // Modal de Detalhes
    noticeForDetails?.let { initialNotice ->
        val latestNotice = notices.firstOrNull { it.id == initialNotice.id } ?: initialNotice
        ResidentNoticeDetailDialog(
            notice = latestNotice,
            employees = employees,
            onDismiss = { noticeForDetails = null },
            onStatusChange = { newStatus, guardName ->
                viewModel.updateResidentNoticeStatus(latestNotice.id, newStatus, guardName, context)
            },
            onEdit = {
                noticeForDetails = null
                noticeToEdit = latestNotice
                showFormDialog = true
            },
            onDuplicate = {
                noticeForDetails = null
                noticeToDuplicate = latestNotice
            },
            onDelete = {
                noticeForDetails = null
                noticeToDelete = latestNotice
            }
        )
    }

    // Diálogo de Confirmação para Duplicar
    noticeToDuplicate?.let { notice ->
        AlertDialog(
            onDismissRequest = { noticeToDuplicate = null },
            icon = { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = BpsGold) },
            title = { Text("Duplicar Aviso?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Deseja reutilizar as informações do aviso da unidade ${notice.unitFullIdentifier} (${notice.residentName}) para criar um novo registro para amanhã?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentGuard = employees.firstOrNull()?.name ?: "Porteiro"
                        viewModel.duplicateResidentNotice(notice, null, currentGuard, context)
                        noticeToDuplicate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                    modifier = Modifier.testTag("confirm_duplicate_btn")
                ) {
                    Text("Duplicar", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noticeToDuplicate = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Exclusão
    noticeToDelete?.let { notice ->
        AlertDialog(
            onDismissRequest = { noticeToDelete = null },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusError) },
            title = { Text("Excluir Aviso?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente remover o aviso da unidade ${notice.unitFullIdentifier} (${notice.residentName})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteResidentNotice(notice, context)
                        noticeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    modifier = Modifier.testTag("confirm_delete_notice_btn")
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { noticeToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ResidentNoticeCard(
    notice: ResidentNotice,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onQuickStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (notice.status) {
        "PENDENTE" -> Color(0xFFF59E0B)
        "VISUALIZADO" -> Color(0xFF38BDF8)
        "CONCLUIDO" -> StatusOk
        "CANCELADO" -> StatusError
        else -> Color.Gray
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notice_card_${notice.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Unit Badge + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Unit Badge (ex.: B-134)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.2.dp, BpsGold),
                    modifier = Modifier.testTag("notice_unit_${notice.id}")
                ) {
                    Text(
                        text = notice.unitFullIdentifier,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsGoldLight,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = notice.statusDisplayName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Type tag + Resident name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${notice.noticeTypeEmoji} ${notice.noticeTypeDisplayName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BpsGoldLight
                )
                Text(text = " • ", color = Color.Gray, fontSize = 12.sp)
                Text(
                    text = "Morador(a): ${notice.residentName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Visitor info if applicable
            if (notice.visitorName.isNotBlank()) {
                Text(
                    text = "Visitante: ${notice.visitorName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
            }

            // Vehicle plate if applicable
            if (notice.vehiclePlate.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = BpsSilver, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Veículo: ${notice.vehiclePlate} ${if (notice.vehicleModel.isNotBlank()) "(${notice.vehicleModel})" else ""}",
                        fontSize = 12.sp,
                        color = BpsSilver
                    )
                }
            }

            // Masked document if applicable
            if (notice.visitorDocument.isNotBlank()) {
                Text(
                    text = "Documento: ${notice.maskedDocument()}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scheduled Date & Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = BpsGold, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Data prevista: ${notice.formattedScheduledDate()}${if (notice.scheduledTime.isNotBlank()) " às ${notice.scheduledTime}" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BpsGoldLight
                )
            }

            if (notice.additionalNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Obs: ${notice.additionalNotes}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("notice_detail_btn_${notice.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Detalhes", fontSize = 11.5.sp)
                    }

                    if (notice.status == "PENDENTE") {
                        Button(
                            onClick = { onQuickStatusChange("CONCLUIDO") },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusOk),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("notice_quick_done_btn_${notice.id}")
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atendido", fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp).testTag("notice_duplicate_btn_${notice.id}")) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicar aviso", tint = BpsGold, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).testTag("notice_edit_btn_${notice.id}")) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar aviso", tint = BpsSilver, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).testTag("notice_delete_btn_${notice.id}")) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir aviso", tint = StatusError.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ResidentNoticeFormDialog(
    initialNotice: ResidentNotice?,
    employees: List<com.example.data.model.Employee>,
    onDismiss: () -> Unit,
    onSave: (ResidentNotice) -> Unit
) {
    var block by remember { mutableStateOf(initialNotice?.block ?: "A") }
    var unitNumberInput by remember { mutableStateOf(initialNotice?.unitNumber ?: "001") }
    var residentName by remember { mutableStateOf(initialNotice?.residentName ?: "") }
    var noticeType by remember { mutableStateOf(initialNotice?.noticeType ?: "VISITA_PROGRAMADA") }

    var visitorName by remember { mutableStateOf(initialNotice?.visitorName ?: "") }
    var visitorDocument by remember { mutableStateOf(initialNotice?.visitorDocument ?: "") }
    var vehiclePlate by remember { mutableStateOf(initialNotice?.vehiclePlate ?: "") }
    var vehicleModel by remember { mutableStateOf(initialNotice?.vehicleModel ?: "") }

    val defaultDateIso = remember {
        initialNotice?.scheduledDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var scheduledDateIso by remember { mutableStateOf(defaultDateIso) }
    var scheduledTime by remember { mutableStateOf(initialNotice?.scheduledTime ?: "") }
    var additionalNotes by remember { mutableStateOf(initialNotice?.additionalNotes ?: "") }

    var guardName by remember {
        mutableStateOf(
            initialNotice?.registeredByGuardName?.ifBlank { null }
                ?: employees.firstOrNull()?.name
                ?: "Porteiro"
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }

    val noticeTypes = listOf(
        "VISITA_PROGRAMADA" to "Visita programada",
        "PRESTADOR_SERVICO" to "Prestador de serviço",
        "ENTREGA" to "Entrega",
        "AUTORIZACAO_ENTRADA" to "Autorização de entrada",
        "MANUTENCAO" to "Manutenção",
        "OUTRO" to "Outro"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .testTag("notice_form_dialog"),
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = BpsGold, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (initialNotice == null) "Novo Aviso de Morador" else "Editar Aviso #${initialNotice.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_notice_form_btn")) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (validationError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusError.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, StatusError)
                        ) {
                            Text(
                                text = validationError!!,
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // 1. Bloco e Unidade
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "1. Bloco e Unidade *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(
                                    selected = block == "A",
                                    onClick = { block = "A" },
                                    label = { Text("BLOCO A (144 un)", fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BpsGoldDark,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("form_block_A")
                                )
                                FilterChip(
                                    selected = block == "B",
                                    onClick = { block = "B" },
                                    label = { Text("BLOCO B (144 un)", fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BpsGoldDark,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("form_block_B")
                                )
                            }

                            // Quick unit input & suggested shortcuts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = unitNumberInput,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(3)
                                        unitNumberInput = digits
                                    },
                                    label = { Text("Número da Unidade (001 a 144)") },
                                    placeholder = { Text("Ex: 134") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("form_unit_input")
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, BpsGold)
                                ) {
                                    val formatted = CondoUnitsHelper.parseUnitNumber(unitNumberInput) ?: "---"
                                    Text(
                                        text = "$block-$formatted",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BpsGoldLight,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Identificação do Morador Responsável
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "2. Morador(a) Responsável *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            OutlinedTextField(
                                value = residentName,
                                onValueChange = { residentName = it },
                                placeholder = { Text("Nome do morador que autorizou (ex: Karla)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_resident_input")
                            )
                        }
                    }

                    // 3. Tipo de Aviso
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "3. Tipo de Ocorrência / Aviso *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                noticeTypes.forEach { (typeKey, typeLabel) ->
                                    val isSelected = noticeType == typeKey
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { noticeType = typeKey },
                                        label = { Text(typeLabel, fontSize = 11.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BpsGoldDark,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("form_type_$typeKey")
                                    )
                                }
                            }
                        }
                    }

                    // 4. Informações do Visitante / Prestador / Veículo (Opcional)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "4. Informações do Visitante / Veículo (Opcional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            OutlinedTextField(
                                value = visitorName,
                                onValueChange = { visitorName = it },
                                label = { Text("Nome completo do visitante / prestador") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_visitor_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = visitorDocument,
                                    onValueChange = { visitorDocument = it },
                                    label = { Text("Doc (RG / CPF)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("form_document_input")
                                )

                                OutlinedTextField(
                                    value = vehiclePlate,
                                    onValueChange = { vehiclePlate = it.uppercase() },
                                    label = { Text("Placa Veículo") },
                                    placeholder = { Text("ABC-1234") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("form_plate_input")
                                )
                            }

                            OutlinedTextField(
                                value = vehicleModel,
                                onValueChange = { vehicleModel = it },
                                label = { Text("Modelo / Cor do veículo (ex: Gol Branco)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_vehicle_model_input")
                            )
                        }
                    }

                    // 5. Programação (Data e Horário)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "5. Data e Horário Previsto *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            val todayCal = Calendar.getInstance()
                            val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(todayCal.time)
                            val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrowCal.time)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = scheduledDateIso == todayStr,
                                    onClick = { scheduledDateIso = todayStr },
                                    label = { Text("Hoje") }
                                )
                                FilterChip(
                                    selected = scheduledDateIso == tomorrowStr,
                                    onClick = { scheduledDateIso = tomorrowStr },
                                    label = { Text("Amanhã") }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = scheduledDateIso,
                                    onValueChange = { scheduledDateIso = it },
                                    label = { Text("Data (AAAA-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("form_date_input")
                                )

                                OutlinedTextField(
                                    value = scheduledTime,
                                    onValueChange = { scheduledTime = it },
                                    label = { Text("Horário (HH:mm)") },
                                    placeholder = { Text("14:00") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("form_time_input")
                                )
                            }

                            OutlinedTextField(
                                value = additionalNotes,
                                onValueChange = { additionalNotes = it },
                                label = { Text("Observações adicionais para a portaria") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_notes_input")
                            )
                        }
                    }

                    // 6. Porteiro Responsável pelo Cadastro
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "6. Porteiro Responsável pelo Registro", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                employees.forEach { emp ->
                                    val isSelected = guardName == emp.name
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { guardName = emp.name },
                                        label = { Text(emp.name) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BpsGoldDark,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Save Action
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                val cleanUnit = CondoUnitsHelper.parseUnitNumber(unitNumberInput)
                                if (cleanUnit == null) {
                                    validationError = "Informe uma unidade válida de 001 a 144 para o Bloco $block."
                                    return@Button
                                }
                                if (residentName.trim().isBlank()) {
                                    validationError = "Informe o nome do morador responsável pelo aviso."
                                    return@Button
                                }
                                if (scheduledDateIso.trim().isBlank()) {
                                    validationError = "Informe a data prevista para o aviso."
                                    return@Button
                                }

                                validationError = null
                                val notice = (initialNotice ?: ResidentNotice(
                                    block = block,
                                    unitNumber = cleanUnit,
                                    residentName = residentName.trim(),
                                    noticeType = noticeType,
                                    scheduledDate = scheduledDateIso.trim()
                                )).copy(
                                    block = block,
                                    unitNumber = cleanUnit,
                                    residentName = residentName.trim(),
                                    noticeType = noticeType,
                                    visitorName = visitorName.trim(),
                                    visitorDocument = visitorDocument.trim(),
                                    vehiclePlate = vehiclePlate.trim(),
                                    vehicleModel = vehicleModel.trim(),
                                    scheduledDate = scheduledDateIso.trim(),
                                    scheduledTime = scheduledTime.trim(),
                                    additionalNotes = additionalNotes.trim(),
                                    registeredByGuardName = guardName,
                                    lastUpdatedByGuardName = guardName,
                                    updatedAtTimestamp = System.currentTimeMillis()
                                )
                                onSave(notice)
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("form_save_notice_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BpsGold)
                        ) {
                            Text("Salvar Aviso", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResidentNoticeDetailDialog(
    notice: ResidentNotice,
    employees: List<com.example.data.model.Employee>,
    onDismiss: () -> Unit,
    onStatusChange: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showFullDocument by remember { mutableStateOf(false) }
    var selectedGuard by remember {
        mutableStateOf(employees.firstOrNull()?.name ?: "Porteiro")
    }

    val createdFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
    val createdStr = createdFormat.format(Date(notice.createdAtTimestamp))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .testTag("notice_detail_dialog_${notice.id}"),
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.2.dp, BpsGold)
                            ) {
                                Text(
                                    text = notice.unitFullIdentifier,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BpsGoldLight,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Aviso #${notice.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_notice_detail_btn")) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status row
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Alterar Status do Aviso", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "PENDENTE" to "🟡 Pendente",
                                    "VISUALIZADO" to "🔵 Visualizado",
                                    "CONCLUIDO" to "🟢 Concluído",
                                    "CANCELADO" to "🔴 Cancelado"
                                ).forEach { (stKey, stLabel) ->
                                    val isSelected = notice.status == stKey
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onStatusChange(stKey, selectedGuard) },
                                        label = { Text(stLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        modifier = Modifier.testTag("detail_status_$stKey")
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Porteiro atuando: ", fontSize = 12.sp, color = Color.Gray)
                                employees.forEach { emp ->
                                    val isCur = selectedGuard == emp.name
                                    Text(
                                        text = emp.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCur) BpsGoldLight else Color.Gray,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp)
                                            .clickable { selectedGuard = emp.name }
                                    )
                                }
                            }
                        }
                    }

                    // Notice Details
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${notice.noticeTypeEmoji} ${notice.noticeTypeDisplayName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = BpsGoldLight
                            )

                            HorizontalDivider(color = Color(0xFF283548), modifier = Modifier.padding(vertical = 4.dp))

                            Text("👤 Morador(a): ${notice.residentName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

                            if (notice.visitorName.isNotBlank()) {
                                Text("🚶 Visitante / Prestador: ${notice.visitorName}", fontSize = 14.sp, color = Color(0xFFE2E8F0))
                            }

                            if (notice.visitorDocument.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🪪 Documento: ${if (showFullDocument) notice.visitorDocument else notice.maskedDocument()}",
                                        fontSize = 13.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    IconButton(
                                        onClick = { showFullDocument = !showFullDocument },
                                        modifier = Modifier.size(24.dp).testTag("reveal_document_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (showFullDocument) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Alternar visibilidade do documento",
                                            tint = BpsGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (notice.vehiclePlate.isNotBlank()) {
                                Text(
                                    text = "🚗 Veículo: ${notice.vehiclePlate} ${if (notice.vehicleModel.isNotBlank()) "(${notice.vehicleModel})" else ""}",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }

                            Text(
                                text = "📅 Data prevista: ${notice.formattedScheduledDate()}${if (notice.scheduledTime.isNotBlank()) " às ${notice.scheduledTime}" else ""}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BpsGold
                            )

                            if (notice.additionalNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "📝 Observações: ${notice.additionalNotes}",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }

                    // Audit info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Registro & Auditoria:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(
                                text = "• Cadastrado em: $createdStr ${if (notice.registeredByGuardName.isNotBlank()) "por ${notice.registeredByGuardName}" else ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            if (notice.lastUpdatedByGuardName.isNotBlank() && notice.updatedAtTimestamp != notice.createdAtTimestamp) {
                                val updatedFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
                                val updatedStr = updatedFormat.format(Date(notice.updatedAtTimestamp))
                                Text(
                                    text = "• Última alteração em: $updatedStr por ${notice.lastUpdatedByGuardName}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Footer Actions
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDuplicate,
                            modifier = Modifier.weight(1f).testTag("detail_duplicate_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Duplicar", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f).testTag("detail_edit_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, BpsGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar", color = BpsGoldLight, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onDelete,
                            modifier = Modifier.weight(0.8f).testTag("detail_delete_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusError.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Excluir", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNoticesCard(hasFilter: Boolean, onNewNotice: () -> Unit) {
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
                    Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = BpsGold, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (hasFilter) "Nenhum aviso encontrado para este filtro" else "Nenhum aviso de morador cadastrado",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasFilter) "Tente buscar por outro bloco ou termo de pesquisa." else "Cadastre visitas programadas, entregas e prestadores de serviço para o próximo turno.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNewNotice,
                colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF1E1400), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cadastrar Primeiro Aviso", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
            }
        }
    }
}
