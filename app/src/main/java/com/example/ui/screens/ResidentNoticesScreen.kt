package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CondoUnitsHelper
import com.example.data.model.ResidentNotice
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
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ResidentNoticesScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notices by viewModel.residentNotices.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val retentionDays by viewModel.noticeRetentionDays.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadNoticeRetentionDays(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTimeframeFilter by remember { mutableStateOf("TODOS") } // "TODOS", "HOJE", "PROXIMOS", "PENDENTES", "CONCLUIDOS"
    var selectedBlockFilter by remember { mutableStateOf("TODOS") } // "TODOS", "A", "B"
    var selectedUnitFilter by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("TODOS") }
    var selectedStatusFilter by remember { mutableStateOf("TODOS") }
    var selectedDateFilter by remember { mutableStateOf("") } // "" = Todas, or "AAAA-MM-DD"
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var showFilterDatePicker by remember { mutableStateOf(false) }

    var expandedNoticeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showFormDialog by remember { mutableStateOf(false) }
    var isDuplicateFormMode by remember { mutableStateOf(false) }
    var noticeToEdit by remember { mutableStateOf<ResidentNotice?>(null) }
    var noticeForDetails by remember { mutableStateOf<ResidentNotice?>(null) }
    var noticeToDuplicate by remember { mutableStateOf<ResidentNotice?>(null) }
    var noticeToDelete by remember { mutableStateOf<ResidentNotice?>(null) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val tomorrowIso = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    val filteredNotices = remember(
        notices,
        searchQuery,
        selectedTimeframeFilter,
        selectedBlockFilter,
        selectedUnitFilter,
        selectedTypeFilter,
        selectedStatusFilter,
        selectedDateFilter,
        todayIso
    ) {
        notices.filter { notice ->
            if (selectedBlockFilter != "TODOS" && notice.block != selectedBlockFilter) {
                return@filter false
            }
            if (selectedUnitFilter.isNotBlank()) {
                val cleanFilterUnit = CondoUnitsHelper.parseUnitNumber(selectedUnitFilter)
                    ?: selectedUnitFilter.trim()
                if (!notice.unitNumber.contains(cleanFilterUnit, ignoreCase = true) &&
                    !notice.unitFullIdentifier.contains(selectedUnitFilter.trim(), ignoreCase = true)
                ) {
                    return@filter false
                }
            }
            if (selectedTypeFilter != "TODOS" && notice.noticeType != selectedTypeFilter) {
                return@filter false
            }
            if (selectedStatusFilter != "TODOS" && notice.status != selectedStatusFilter) {
                return@filter false
            }
            if (selectedDateFilter.isNotBlank() && notice.scheduledDate != selectedDateFilter) {
                return@filter false
            }

            val matchesTimeframe = when (selectedTimeframeFilter) {
                "HOJE" -> notice.scheduledDate == todayIso
                "PROXIMOS" -> notice.scheduledDate > todayIso && notice.status != "CANCELADO"
                "PENDENTES" -> notice.status == "PENDENTE"
                "CONCLUIDOS" -> notice.status == "CONCLUIDO"
                else -> true
            }
            if (!matchesTimeframe) return@filter false

            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim()
                val match = notice.unitFullIdentifier.contains(q, ignoreCase = true) ||
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

    val hasAnyFilter = searchQuery.isNotBlank() ||
        selectedBlockFilter != "TODOS" ||
        selectedUnitFilter.isNotBlank() ||
        selectedTypeFilter != "TODOS" ||
        selectedStatusFilter != "TODOS" ||
        selectedDateFilter.isNotBlank() ||
        selectedTimeframeFilter != "TODOS"

    val todaySectionNotices = remember(filteredNotices, todayIso) {
        filteredNotices.filter { it.scheduledDate == todayIso && it.status != "CONCLUIDO" && it.status != "CANCELADO" }
    }
    val upcomingSectionNotices = remember(filteredNotices, todayIso) {
        filteredNotices.filter { it.scheduledDate > todayIso && it.status != "CONCLUIDO" && it.status != "CANCELADO" }
    }
    val pendingOverdueSectionNotices = remember(filteredNotices, todayIso) {
        filteredNotices.filter { it.scheduledDate < todayIso && (it.status == "PENDENTE" || it.status == "VISUALIZADO") }
    }
    val completedSectionNotices = remember(filteredNotices) {
        filteredNotices.filter { it.status == "CONCLUIDO" }
    }
    val cancelledSectionNotices = remember(filteredNotices) {
        filteredNotices.filter { it.status == "CANCELADO" }
    }

    val eligibleForPurge = remember(notices, retentionDays) {
        viewModel.getEligibleNoticesForRetentionPurge(retentionDays)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("notices_lazy_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            text = "Armazenamento local offline seguro • Blocos A e B (288 unidades)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            noticeToEdit = null
                            isDuplicateFormMode = false
                            showFormDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("new_notice_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF1E1400), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Novo Aviso", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Quick Search + Filter & Privacy Action Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar unidade (B-134, A-025), morador, visitante ou placa...") },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAdvancedFilters = !showAdvancedFilters },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (showAdvancedFilters) BpsGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.weight(1f).testTag("toggle_filters_btn")
                        ) {
                            Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showAdvancedFilters) "Ocultar Filtros" else "Filtros Avançados",
                                fontSize = 12.sp,
                                color = BpsGoldLight
                            )
                        }

                        OutlinedButton(
                            onClick = { showRetentionDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1f).testTag("privacy_retention_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Privacidade (${retentionDays}d)",
                                fontSize = 12.sp,
                                color = BpsGoldLight
                            )
                        }
                    }
                }
            }

            // Block Selector: [ TODOS ] [ BLOCO A ] [ BLOCO B ]
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedBlockFilter == "TODOS",
                        onClick = { selectedBlockFilter = "TODOS" },
                        label = { Text("Todos (288 un)", fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_block_all")
                    )
                    FilterChip(
                        selected = selectedBlockFilter == "A",
                        onClick = { selectedBlockFilter = "A" },
                        label = { Text("BLOCO A (144 un)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BpsGoldDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_block_A")
                    )
                    FilterChip(
                        selected = selectedBlockFilter == "B",
                        onClick = { selectedBlockFilter = "B" },
                        label = { Text("BLOCO B (144 un)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BpsGoldDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_block_B")
                    )
                }
            }

            // Section / Timeframe Filter Chips
            item {
                val countPending = notices.count { it.status == "PENDENTE" }
                val countToday = notices.count { it.scheduledDate == todayIso }
                val countUpcoming = notices.count { it.scheduledDate > todayIso && it.status != "CANCELADO" }
                val countDone = notices.count { it.status == "CONCLUIDO" }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                        label = { Text("Próximos ($countUpcoming)", fontSize = 11.5.sp) },
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
                    FilterChip(
                        selected = selectedTimeframeFilter == "CONCLUIDOS",
                        onClick = { selectedTimeframeFilter = "CONCLUIDOS" },
                        label = { Text("Concluídos ($countDone)", fontSize = 11.5.sp) },
                        modifier = Modifier.testTag("filter_timeframe_done")
                    )
                }
            }

            // Collapsible Advanced Filters (Unit, Date, Notice Type, Status)
            if (showAdvancedFilters) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("advanced_filters_panel")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Filtros Detalhados", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)
                                if (hasAnyFilter) {
                                    TextButton(
                                        onClick = {
                                            searchQuery = ""
                                            selectedBlockFilter = "TODOS"
                                            selectedUnitFilter = ""
                                            selectedTypeFilter = "TODOS"
                                            selectedStatusFilter = "TODOS"
                                            selectedDateFilter = ""
                                            selectedTimeframeFilter = "TODOS"
                                        },
                                        modifier = Modifier.testTag("clear_all_filters_btn")
                                    ) {
                                        Text("Limpar filtros", fontSize = 11.5.sp, color = BpsGold)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = selectedUnitFilter,
                                onValueChange = { selectedUnitFilter = it },
                                label = { Text("Filtrar Unidade (001 a 144 ou A-025 / B-134)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("filter_unit_input")
                            )

                            Text("Data programada:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = selectedDateFilter.isBlank(),
                                    onClick = { selectedDateFilter = "" },
                                    label = { Text("Todas", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("filter_date_all")
                                )
                                FilterChip(
                                    selected = selectedDateFilter == todayIso,
                                    onClick = { selectedDateFilter = todayIso },
                                    label = { Text("Hoje", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("filter_date_today")
                                )
                                FilterChip(
                                    selected = selectedDateFilter == tomorrowIso,
                                    onClick = { selectedDateFilter = tomorrowIso },
                                    label = { Text("Amanhã", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("filter_date_tomorrow")
                                )
                                OutlinedButton(
                                    onClick = { showFilterDatePicker = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("filter_date_calendar")
                                ) {
                                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = BpsGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedDateFilter.isNotBlank()) selectedDateFilter else "Calendário",
                                        fontSize = 11.sp,
                                        color = BpsGoldLight
                                    )
                                }
                            }

                            Text("Tipo de aviso:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "TODOS" to "Todos",
                                    "VISITA_PROGRAMADA" to "Visita",
                                    "PRESTADOR_SERVICO" to "Prestador",
                                    "ENTREGA" to "Entrega",
                                    "AUTORIZACAO_ENTRADA" to "Autorização",
                                    "MANUTENCAO" to "Manutenção",
                                    "OUTRO" to "Outro"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedTypeFilter == key,
                                        onClick = { selectedTypeFilter = key },
                                        label = { Text(label, fontSize = 11.sp) },
                                        modifier = Modifier.testTag("filter_type_$key")
                                    )
                                }
                            }

                            Text("Status:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "TODOS" to "Todos",
                                    "PENDENTE" to "🟡 Pendente",
                                    "VISUALIZADO" to "🔵 Visualizado",
                                    "CONCLUIDO" to "🟢 Concluído",
                                    "CANCELADO" to "🔴 Cancelado"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedStatusFilter == key,
                                        onClick = { selectedStatusFilter = key },
                                        label = { Text(label, fontSize = 11.sp) },
                                        modifier = Modifier.testTag("filter_status_$key")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filteredNotices.isEmpty()) {
                item {
                    EmptyNoticesCard(hasFilter = hasAnyFilter) {
                        noticeToEdit = null
                        isDuplicateFormMode = false
                        showFormDialog = true
                    }
                }
            } else if (selectedTimeframeFilter == "TODOS" && selectedStatusFilter == "TODOS") {
                val pendingTotal = filteredNotices.count { it.status == "PENDENTE" }
                if (pendingTotal > 0) {
                    item(key = "header_pending_summary") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF78350F).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("section_header_pending")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🟡 AVISOS PENDENTES ($pendingTotal)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFFCD34D)
                                )
                                TextButton(onClick = { selectedTimeframeFilter = "PENDENTES" }) {
                                    Text("Ver só pendentes", fontSize = 11.sp, color = BpsGoldLight)
                                }
                            }
                        }
                    }
                }

                if (todaySectionNotices.isNotEmpty()) {
                    item(key = "header_today") {
                        SectionHeaderBanner("📅 AVISOS DE HOJE (${todaySectionNotices.size})", BpsGoldLight, "section_header_today")
                    }
                    items(todaySectionNotices, key = { "today_${it.id}" }) { notice ->
                        ResidentNoticeCard(
                            notice = notice,
                            isExpanded = notice.id in expandedNoticeIds,
                            onToggleExpand = {
                                expandedNoticeIds = if (notice.id in expandedNoticeIds) {
                                    expandedNoticeIds - notice.id
                                } else {
                                    expandedNoticeIds + notice.id
                                }
                            },
                            onViewDetails = { noticeForDetails = notice },
                            onEdit = {
                                noticeToEdit = notice
                                isDuplicateFormMode = false
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

                if (upcomingSectionNotices.isNotEmpty()) {
                    item(key = "header_upcoming") {
                        SectionHeaderBanner("📆 PRÓXIMOS AVISOS (${upcomingSectionNotices.size})", Color(0xFF7DD3FC), "section_header_upcoming")
                    }
                    items(upcomingSectionNotices, key = { "upcoming_${it.id}" }) { notice ->
                        ResidentNoticeCard(
                            notice = notice,
                            isExpanded = notice.id in expandedNoticeIds,
                            onToggleExpand = {
                                expandedNoticeIds = if (notice.id in expandedNoticeIds) {
                                    expandedNoticeIds - notice.id
                                } else {
                                    expandedNoticeIds + notice.id
                                }
                            },
                            onViewDetails = { noticeForDetails = notice },
                            onEdit = {
                                noticeToEdit = notice
                                isDuplicateFormMode = false
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

                if (pendingOverdueSectionNotices.isNotEmpty()) {
                    item(key = "header_pending_overdue") {
                        SectionHeaderBanner("🟡 AVISOS PENDENTES ANTERIORES (${pendingOverdueSectionNotices.size})", Color(0xFFF59E0B), "section_header_pending_overdue")
                    }
                    items(pendingOverdueSectionNotices, key = { "overdue_${it.id}" }) { notice ->
                        ResidentNoticeCard(
                            notice = notice,
                            isExpanded = notice.id in expandedNoticeIds,
                            onToggleExpand = {
                                expandedNoticeIds = if (notice.id in expandedNoticeIds) {
                                    expandedNoticeIds - notice.id
                                } else {
                                    expandedNoticeIds + notice.id
                                }
                            },
                            onViewDetails = { noticeForDetails = notice },
                            onEdit = {
                                noticeToEdit = notice
                                isDuplicateFormMode = false
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

                if (completedSectionNotices.isNotEmpty()) {
                    item(key = "header_completed") {
                        SectionHeaderBanner("🟢 AVISOS CONCLUÍDOS (${completedSectionNotices.size})", StatusOk, "section_header_completed")
                    }
                    items(completedSectionNotices, key = { "done_${it.id}" }) { notice ->
                        ResidentNoticeCard(
                            notice = notice,
                            isExpanded = notice.id in expandedNoticeIds,
                            onToggleExpand = {
                                expandedNoticeIds = if (notice.id in expandedNoticeIds) {
                                    expandedNoticeIds - notice.id
                                } else {
                                    expandedNoticeIds + notice.id
                                }
                            },
                            onViewDetails = { noticeForDetails = notice },
                            onEdit = {
                                noticeToEdit = notice
                                isDuplicateFormMode = false
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

                if (cancelledSectionNotices.isNotEmpty()) {
                    item(key = "header_cancelled") {
                        SectionHeaderBanner("🔴 AVISOS CANCELADOS (${cancelledSectionNotices.size})", StatusError, "section_header_cancelled")
                    }
                    items(cancelledSectionNotices, key = { "cancel_${it.id}" }) { notice ->
                        ResidentNoticeCard(
                            notice = notice,
                            isExpanded = notice.id in expandedNoticeIds,
                            onToggleExpand = {
                                expandedNoticeIds = if (notice.id in expandedNoticeIds) {
                                    expandedNoticeIds - notice.id
                                } else {
                                    expandedNoticeIds + notice.id
                                }
                            },
                            onViewDetails = { noticeForDetails = notice },
                            onEdit = {
                                noticeToEdit = notice
                                isDuplicateFormMode = false
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
            } else {
                item(key = "header_filtered") {
                    val sectionLabel = when (selectedTimeframeFilter) {
                        "HOJE" -> "📅 AVISOS DE HOJE (${filteredNotices.size})"
                        "PROXIMOS" -> "📆 PRÓXIMOS AVISOS (${filteredNotices.size})"
                        "PENDENTES" -> "🟡 AVISOS PENDENTES (${filteredNotices.size})"
                        "CONCLUIDOS" -> "🟢 AVISOS CONCLUÍDOS (${filteredNotices.size})"
                        else -> "📋 AVISOS FILTRADOS (${filteredNotices.size})"
                    }
                    SectionHeaderBanner(sectionLabel, BpsGoldLight, "section_header_filtered")
                }
                items(filteredNotices, key = { "filtered_${it.id}" }) { notice ->
                    ResidentNoticeCard(
                        notice = notice,
                        isExpanded = notice.id in expandedNoticeIds,
                        onToggleExpand = {
                            expandedNoticeIds = if (notice.id in expandedNoticeIds) {
                                expandedNoticeIds - notice.id
                            } else {
                                expandedNoticeIds + notice.id
                            }
                        },
                        onViewDetails = { noticeForDetails = notice },
                        onEdit = {
                            noticeToEdit = notice
                            isDuplicateFormMode = false
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

    if (showFilterDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showFilterDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            selectedDateFilter = sdf.format(Date(millis))
                        }
                        showFilterDatePicker = false
                    }
                ) {
                    Text("Filtrar Data", color = BpsGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Modal de Cadastro / Edição / Duplicação Revisada
    if (showFormDialog) {
        ResidentNoticeFormDialog(
            initialNotice = noticeToEdit,
            isDuplicateDraft = isDuplicateFormMode,
            employees = employees,
            onDismiss = {
                showFormDialog = false
                noticeToEdit = null
                isDuplicateFormMode = false
            },
            onSave = { notice ->
                viewModel.saveResidentNotice(notice, context)
                showFormDialog = false
                noticeToEdit = null
                isDuplicateFormMode = false
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
                isDuplicateFormMode = false
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
                    "Deseja reutilizar as informações do aviso da unidade ${notice.unitFullIdentifier} (${notice.residentName}) para criar um novo registro?"
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
                    Text("Duplicar p/ Amanhã", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            noticeToEdit = notice
                            isDuplicateFormMode = true
                            noticeToDuplicate = null
                            showFormDialog = true
                        },
                        modifier = Modifier.testTag("customize_duplicate_btn")
                    ) {
                        Text("Editar antes", color = BpsGoldLight)
                    }
                    TextButton(onClick = { noticeToDuplicate = null }) {
                        Text("Cancelar")
                    }
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

    if (showRetentionDialog) {
        RetentionPolicyDialog(
            currentRetentionDays = retentionDays,
            eligibleNotices = eligibleForPurge,
            onSelectRetentionDays = { days -> viewModel.updateNoticeRetentionDays(context, days) },
            onRequestPurge = {
                showRetentionDialog = false
                showPurgeConfirmDialog = true
            },
            onDismiss = { showRetentionDialog = false }
        )
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            modifier = Modifier.testTag("confirm_purge_dialog"),
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusError) },
            title = { Text("Confirmar Limpeza de Retenção?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Deseja excluir permanentemente ${eligibleForPurge.size} aviso(s) concluído(s) ou cancelado(s) com mais de $retentionDays dias?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purgeExpiredNoticesConfirmed(context, eligibleForPurge)
                        showPurgeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    modifier = Modifier.testTag("confirm_purge_btn")
                ) {
                    Text("Remover Antigos", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Manter")
                }
            }
        )
    }
}

@Composable
private fun SectionHeaderBanner(title: String, accentColor: Color, tag: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp).testTag(tag)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResidentNoticeCard(
    notice: ResidentNotice,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onQuickStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

            if (notice.visitorName.isNotBlank()) {
                Text(
                    text = "Visitante: ${notice.visitorName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
            }

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

            if (notice.visitorDocument.isNotBlank()) {
                Text(
                    text = "Documento: ${notice.maskedDocument(context)}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                    maxLines = if (isExpanded) 10 else 2
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Text(
                        text = notice.officialHeaderIdentifier,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsGold
                    )
                    if (notice.registeredByGuardName.isNotBlank()) {
                        Text(
                            text = "Cadastrado por: ${notice.registeredByGuardName}",
                            fontSize = 11.5.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                    if (notice.auditTrail.isNotBlank()) {
                        Text(
                            text = "Histórico:\n${notice.auditTrail}",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (notice.status == "PENDENTE") {
                            OutlinedButton(
                                onClick = { onQuickStatusChange("VISUALIZADO") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("card_mark_viewed_btn_${notice.id}")
                            ) {
                                Text("🔵 Visualizado", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }
                        if (notice.status in setOf("PENDENTE", "VISUALIZADO")) {
                            OutlinedButton(
                                onClick = { onQuickStatusChange("CONCLUIDO") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("card_mark_done_btn_${notice.id}")
                            ) {
                                Text("🟢 Concluído", fontSize = 11.sp, color = StatusOk)
                            }
                            OutlinedButton(
                                onClick = { onQuickStatusChange("CANCELADO") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("card_mark_cancel_btn_${notice.id}")
                            ) {
                                Text("🔴 Cancelar", fontSize = 11.sp, color = StatusError)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onToggleExpand,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("notice_expand_btn_${notice.id}")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isExpanded) "Recolher" else "Expandir", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("notice_detail_btn_${notice.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Detalhes", fontSize = 11.sp)
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
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Atendido", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp).testTag("notice_duplicate_btn_${notice.id}")) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicar aviso", tint = BpsGold, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp).testTag("notice_edit_btn_${notice.id}")) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar aviso", tint = BpsSilver, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("notice_delete_btn_${notice.id}")) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir aviso", tint = StatusError.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF1E1400), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cadastrar Primeiro Aviso", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
            }
        }
    }
}
