package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CondoUnitsHelper
import com.example.data.model.Employee
import com.example.data.model.ResidentNotice
import com.example.ui.components.BpsResponsiveDialog
import com.example.ui.components.BpsResponsiveModalScaffold
import com.example.ui.components.ConfigureResponsiveDialogWindow
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldDark
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.StatusError
import com.example.util.NoticeSecurityManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ResidentNoticeFormDialog(
    initialNotice: ResidentNotice?,
    isDuplicateDraft: Boolean = false,
    employees: List<Employee>,
    onDismiss: () -> Unit,
    onSave: (ResidentNotice) -> Unit
) {
    val context = LocalContext.current

    var block by remember(initialNotice) { mutableStateOf(initialNotice?.block ?: "A") }
    var unitNumberInput by remember(initialNotice) { mutableStateOf(initialNotice?.unitNumber ?: "001") }
    var showUnitGrid by remember { mutableStateOf(true) }
    var residentName by remember(initialNotice) { mutableStateOf(initialNotice?.residentName ?: "") }
    var noticeType by remember(initialNotice) { mutableStateOf(initialNotice?.noticeType ?: "VISITA_PROGRAMADA") }

    var visitorName by remember(initialNotice) { mutableStateOf(initialNotice?.visitorName ?: "") }
    var visitorDocument by remember(initialNotice) {
        mutableStateOf(initialNotice?.decryptedDocument(context) ?: "")
    }
    var vehiclePlate by remember(initialNotice) { mutableStateOf(initialNotice?.vehiclePlate ?: "") }
    var vehicleModel by remember(initialNotice) { mutableStateOf(initialNotice?.vehicleModel ?: "") }

    val defaultDateIso = remember(initialNotice, isDuplicateDraft) {
        if (isDuplicateDraft) {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        } else {
            initialNotice?.scheduledDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
    }
    var scheduledDateIso by remember(initialNotice, isDuplicateDraft) { mutableStateOf(defaultDateIso) }
    var scheduledTime by remember(initialNotice) { mutableStateOf(initialNotice?.scheduledTime ?: "") }
    var additionalNotes by remember(initialNotice) { mutableStateOf(initialNotice?.additionalNotes ?: "") }

    var guardName by remember(initialNotice) {
        mutableStateOf(
            initialNotice?.registeredByGuardName?.ifBlank { null }
                ?: employees.firstOrNull()?.name
                ?: "Porteiro"
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val allBlockUnits = remember { CondoUnitsHelper.getAllUnits() }
    val filteredUnits = remember(unitNumberInput, allBlockUnits) {
        val cleanDigits = unitNumberInput.trim().uppercase()
            .removePrefix("A-").removePrefix("B-").removePrefix("A").removePrefix("B")
        if (cleanDigits.isBlank()) {
            allBlockUnits
        } else {
            val noLeadingZeros = cleanDigits.trimStart('0')
            allBlockUnits.filter { unit ->
                unit.contains(cleanDigits) || (noLeadingZeros.isNotEmpty() && unit.trimStart('0').contains(noLeadingZeros))
            }.ifEmpty { allBlockUnits }
        }
    }

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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureResponsiveDialogWindow()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            val isCompactHeight = maxHeight < 620.dp
            val isCompactWidth = maxWidth < 360.dp
            val isUltraNarrow = maxWidth < 340.dp
            val outerHorizontalPad = if (isCompactWidth) 8.dp else 14.dp
            val outerVerticalPad = if (isCompactHeight) 6.dp else 12.dp
            val availableModalHeight = (maxHeight - outerVerticalPad * 2).coerceAtLeast(220.dp)
            val contentPadding = if (isCompactHeight || isCompactWidth) 10.dp else 14.dp
            val sectionSpacing = if (isCompactHeight) 8.dp else 12.dp
            val cardPadding = if (isCompactHeight || isCompactWidth) 10.dp else 12.dp
            val unitGridHeight = when {
                maxHeight < 420.dp -> 108.dp
                isCompactHeight -> 128.dp
                else -> 156.dp
            }
            val unitGridColumns = if (isUltraNarrow) 3 else 4

            Surface(
                modifier = Modifier
                    .padding(horizontal = outerHorizontalPad, vertical = outerVerticalPad)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = availableModalHeight)
                    .testTag("notice_form_dialog"),
                shape = RoundedCornerShape(18.dp),
                color = BpsDarkBg,
                border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. CABEÇALHO FIXO
                    Surface(
                        color = Color(0xFF070B14),
                        modifier = Modifier.testTag("form_header_container")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = contentPadding,
                                        vertical = if (isCompactHeight) 8.dp else 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = "Aviso de Morador",
                                        tint = BpsGold,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            isDuplicateDraft -> "Duplicar Aviso (Confirmar Dados)"
                                            initialNotice == null -> "Novo Aviso de Morador"
                                            else -> "Editar Aviso #${initialNotice.id}"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("close_notice_form_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar",
                                        tint = Color.White
                                    )
                                }
                            }
                            HorizontalDivider(color = BpsGold.copy(alpha = 0.25f))
                        }
                    }

                    // 2. CONTEÚDO COM ROLAGEM INDEPENDENTE
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(contentPadding)
                            .testTag("form_scroll_content"),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                    ) {
                        if (validationError != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusError.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, StatusError),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_validation_error")
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

                        // 1. Bloco e Unidade (001 a 144)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            border = BorderStroke(1.dp, Color(0xFF283548)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "1. Bloco e Unidade *",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BpsGoldLight
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = block == "A",
                                        onClick = { block = "A" },
                                        label = {
                                            Text(
                                                text = "BLOCO A (144 un)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (isCompactWidth) 11.sp else 12.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BpsGoldDark,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("form_block_A")
                                    )
                                    FilterChip(
                                        selected = block == "B",
                                        onClick = { block = "B" },
                                        label = {
                                            Text(
                                                text = "BLOCO B (144 un)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (isCompactWidth) 11.sp else 12.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BpsGoldDark,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("form_block_B")
                                    )
                                }

                                // Direct unit search / input (accepts "134", "025", "A-025", "B-134")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = unitNumberInput,
                                        onValueChange = { input ->
                                            val upper = input.trim().uppercase()
                                            if (upper.startsWith("A")) block = "A"
                                            else if (upper.startsWith("B")) block = "B"
                                            val filteredChars = upper
                                                .filter { it.isDigit() || it == 'A' || it == 'B' || it == '-' }
                                                .take(5)
                                            unitNumberInput = filteredChars
                                        },
                                        label = {
                                            Text(
                                                text = "Apartamento (ex: B-134 ou 134)",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        placeholder = { Text("Ex: 134, A-025, B-010") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("form_unit_input")
                                    )

                                    val formatted = CondoUnitsHelper.parseUnitNumber(unitNumberInput) ?: "---"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.dp, BpsGold)
                                    ) {
                                        Text(
                                            text = "$block-$formatted",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = BpsGoldLight,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Selecionar na lista (${CondoUnitsHelper.UNITS_PER_BLOCK} unidades do Bloco $block):",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { showUnitGrid = !showUnitGrid },
                                        modifier = Modifier.testTag("toggle_unit_grid_btn")
                                    ) {
                                        Text(
                                            text = if (showUnitGrid) "Ocultar lista" else "Mostrar 001–144",
                                            color = BpsGold,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (showUnitGrid) {
                                    val currentParsed = CondoUnitsHelper.parseUnitNumber(unitNumberInput)
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(unitGridColumns),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(unitGridHeight)
                                            .testTag("unit_selector_grid"),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(filteredUnits, key = { "$block-$it" }) { unit ->
                                            val isUnitSelected = currentParsed == unit
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isUnitSelected) BpsGold else Color(0xFF0F172A),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isUnitSelected) BpsGoldLight else Color(0xFF334155)
                                                ),
                                                modifier = Modifier
                                                    .clickable { unitNumberInput = unit }
                                                    .testTag("unit_grid_item_${block}_$unit")
                                            ) {
                                                Text(
                                                    text = "$block-$unit",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUnitSelected) Color(0xFF1E1400) else Color(0xFFE2E8F0),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                                )
                                            }
                                        }
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
                            Column(
                                modifier = Modifier.padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "2. Morador(a) Responsável *",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BpsGoldLight
                                )

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
                            Column(
                                modifier = Modifier.padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "3. Tipo de Ocorrência / Aviso *",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BpsGoldLight
                                )

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
                            Column(
                                modifier = Modifier.padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "4. Informações do Visitante / Veículo (Opcional)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BpsGoldLight
                                )

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

                                if (isUltraNarrow) {
                                    OutlinedTextField(
                                        value = visitorDocument,
                                        onValueChange = { visitorDocument = it },
                                        label = { Text("Doc (RG / CPF)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("form_document_input")
                                    )
                                    OutlinedTextField(
                                        value = vehiclePlate,
                                        onValueChange = { vehiclePlate = it.uppercase() },
                                        label = { Text("Placa Veículo") },
                                        placeholder = { Text("ABC-1234") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("form_plate_input")
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = visitorDocument,
                                            onValueChange = { visitorDocument = it },
                                            label = { Text("Doc (RG / CPF)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("form_document_input")
                                        )

                                        OutlinedTextField(
                                            value = vehiclePlate,
                                            onValueChange = { vehiclePlate = it.uppercase() },
                                            label = { Text("Placa Veículo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("ABC-1234") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("form_plate_input")
                                        )
                                    }
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

                        // 5. Programação (Data com Calendário e Horário com Seletor)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            border = BorderStroke(1.dp, Color(0xFF283548)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "5. Data e Horário Previsto *",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BpsGoldLight
                                )

                                val todayCal = Calendar.getInstance()
                                val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(todayCal.time)
                                val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrowCal.time)

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = scheduledDateIso == todayStr,
                                        onClick = { scheduledDateIso = todayStr },
                                        label = { Text("Hoje") },
                                        modifier = Modifier.testTag("form_quick_today")
                                    )
                                    FilterChip(
                                        selected = scheduledDateIso == tomorrowStr,
                                        onClick = { scheduledDateIso = tomorrowStr },
                                        label = { Text("Amanhã") },
                                        modifier = Modifier.testTag("form_quick_tomorrow")
                                    )
                                    OutlinedButton(
                                        onClick = { showDatePicker = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("open_date_picker_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = BpsGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Calendário", fontSize = 12.sp, color = BpsGoldLight)
                                    }
                                    OutlinedButton(
                                        onClick = { showTimePicker = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("open_time_picker_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = BpsGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Horário", fontSize = 12.sp, color = BpsGoldLight)
                                    }
                                }

                                if (isUltraNarrow) {
                                    OutlinedTextField(
                                        value = scheduledDateIso,
                                        onValueChange = { scheduledDateIso = it },
                                        label = { Text("Data (AAAA-MM-DD)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("form_date_input")
                                    )
                                    OutlinedTextField(
                                        value = scheduledTime,
                                        onValueChange = { scheduledTime = it },
                                        label = { Text("Horário (HH:mm)") },
                                        placeholder = { Text("14:00") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("form_time_input")
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = scheduledDateIso,
                                            onValueChange = { scheduledDateIso = it },
                                            label = { Text("Data (AAAA-MM-DD)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("form_date_input")
                                        )

                                        OutlinedTextField(
                                            value = scheduledTime,
                                            onValueChange = { scheduledTime = it },
                                            label = { Text("Horário (HH:mm)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("14:00") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("form_time_input")
                                        )
                                    }
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
                            Column(
                                modifier = Modifier.padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "6. Porteiro Responsável pelo Registro",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BpsGoldLight
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    employees.forEach { emp ->
                                        val isSelected = guardName == emp.name
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { guardName = emp.name },
                                            label = { Text(emp.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BpsGoldDark,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.testTag("form_guard_${emp.name}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. RODAPÉ FIXO (Sempre visível acima da barra do Android e teclado)
                    Surface(
                        color = Color(0xFF070B14),
                        modifier = Modifier.testTag("form_footer_container")
                    ) {
                        Column {
                            HorizontalDivider(color = BpsGold.copy(alpha = 0.25f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = contentPadding,
                                        vertical = if (isCompactHeight) 8.dp else 10.dp
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("form_cancel_notice_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF475569))
                                ) {
                                    Text(
                                        text = "Cancelar",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                        val dateClean = scheduledDateIso.trim()
                                        if (!dateClean.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                                            validationError = "Informe uma data válida no formato AAAA-MM-DD."
                                            return@Button
                                        }

                                        validationError = null
                                        val now = System.currentTimeMillis()
                                        val base = if (isDuplicateDraft || initialNotice == null) {
                                            ResidentNotice(
                                                block = block,
                                                unitNumber = cleanUnit,
                                                residentName = residentName.trim(),
                                                noticeType = noticeType,
                                                scheduledDate = dateClean,
                                                createdAtTimestamp = now
                                            )
                                        } else {
                                            initialNotice
                                        }

                                        val notice = base.copy(
                                            block = block,
                                            unitNumber = cleanUnit,
                                            residentName = residentName.trim(),
                                            noticeType = noticeType,
                                            visitorName = visitorName.trim(),
                                            visitorDocument = visitorDocument.trim(),
                                            vehiclePlate = vehiclePlate.trim(),
                                            vehicleModel = vehicleModel.trim(),
                                            scheduledDate = dateClean,
                                            scheduledTime = scheduledTime.trim(),
                                            additionalNotes = additionalNotes.trim(),
                                            registeredByGuardName = if (isDuplicateDraft || initialNotice == null) guardName else base.registeredByGuardName,
                                            lastUpdatedByGuardName = guardName,
                                            updatedAtTimestamp = now
                                        )
                                        onSave(notice)
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .heightIn(min = 48.dp)
                                        .testTag("form_save_notice_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BpsGold)
                                ) {
                                    Text(
                                        text = if (isDuplicateDraft) "Confirmar & Salvar" else "Salvar Aviso",
                                        color = Color(0xFF1E1400),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(scheduledDateIso) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(scheduledDateIso)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            scheduledDateIso = sdf.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Selecionar", color = BpsGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val parts = scheduledTime.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 14
        val initMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Selecionar Horário Previsto", fontWeight = FontWeight.Bold) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scheduledTime = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("Aplicar", color = BpsGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResidentNoticeDetailDialog(
    notice: ResidentNotice,
    employees: List<Employee>,
    onDismiss: () -> Unit,
    onStatusChange: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showFullDocument by remember { mutableStateOf(false) }
    var selectedGuard by remember {
        mutableStateOf(employees.firstOrNull()?.name ?: "Porteiro")
    }

    val createdFormat = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")) }
    val createdStr = createdFormat.format(Date(notice.createdAtTimestamp))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureResponsiveDialogWindow()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            val isCompactHeight = maxHeight < 620.dp
            val isCompactWidth = maxWidth < 360.dp
            val outerHorizontalPad = if (isCompactWidth) 8.dp else 14.dp
            val outerVerticalPad = if (isCompactHeight) 6.dp else 12.dp
            val availableModalHeight = (maxHeight - outerVerticalPad * 2).coerceAtLeast(220.dp)

            Surface(
                modifier = Modifier
                    .padding(horizontal = outerHorizontalPad, vertical = outerVerticalPad)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = availableModalHeight)
                    .testTag("notice_detail_dialog_${notice.id}"),
                shape = RoundedCornerShape(18.dp),
                color = BpsDarkBg,
                border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            Column {
                                Text(
                                    text = "Aviso #${notice.id}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = notice.officialHeaderIdentifier,
                                    fontSize = 10.sp,
                                    color = BpsGoldLight
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp).testTag("close_notice_detail_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
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
                            Text(text = "Controle de Status (Registra Porteiro e Hora)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BpsGoldLight)

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Porteiro autorizado atuando: ", fontSize = 12.sp, color = Color.Gray)
                                employees.forEach { emp ->
                                    val isCur = selectedGuard == emp.name
                                    Text(
                                        text = emp.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCur) BpsGoldLight else Color.Gray,
                                        modifier = Modifier
                                            .clickable { selectedGuard = emp.name }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                text = notice.officialHeaderIdentifier,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = BpsGold
                            )
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
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = BpsGold, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Documento: ${if (showFullDocument) notice.decryptedDocument(context) else notice.maskedDocument(context)}",
                                                fontSize = 13.sp,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                        IconButton(
                                            onClick = { showFullDocument = !showFullDocument },
                                            modifier = Modifier.size(48.dp).testTag("reveal_document_btn")
                                        ) {
                                            Icon(
                                                imageVector = if (showFullDocument) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Alternar visibilidade do documento",
                                                tint = BpsGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BpsGoldLight
                            )
                            Text(
                                text = "• Cadastrado em: $createdStr ${if (notice.registeredByGuardName.isNotBlank()) "por ${notice.registeredByGuardName}" else ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            if (notice.lastUpdatedByGuardName.isNotBlank() && notice.updatedAtTimestamp != notice.createdAtTimestamp) {
                                val updatedStr = createdFormat.format(Date(notice.updatedAtTimestamp))
                                Text(
                                    text = "• Última alteração em: $updatedStr por ${notice.lastUpdatedByGuardName}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (notice.auditTrail.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notice.auditTrail,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFCBD5E1),
                                    modifier = Modifier.testTag("notice_audit_trail_text")
                                )
                            }
                        }
                    }
                }

                // Footer Actions
                Surface(color = Color(0xFF070B14)) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        val useWrapFooter = this.maxWidth < 340.dp
                        if (useWrapFooter) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDuplicate,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("detail_duplicate_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Duplicar", fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = onEdit,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("detail_edit_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, BpsGold),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Editar", color = BpsGoldLight, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = onDelete,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("detail_delete_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusError.copy(alpha = 0.85f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Excluir", color = Color.White, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDuplicate,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_duplicate_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Duplicar", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = onEdit,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_edit_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, BpsGold),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = BpsGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Editar", color = BpsGoldLight, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = onDelete,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(0.85f).heightIn(min = 48.dp).testTag("detail_delete_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusError.copy(alpha = 0.85f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Excluir", color = Color.White, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetentionPolicyDialog(
    currentRetentionDays: Int,
    eligibleNotices: List<ResidentNotice>,
    onSelectRetentionDays: (Int) -> Unit,
    onRequestPurge: () -> Unit,
    onDismiss: () -> Unit
) {
    BpsResponsiveDialog(
        onDismissRequest = onDismiss,
        dialogTestTag = "retention_policy_dialog",
        dialogMaxWidth = 560.dp
    ) { metrics ->
        BpsResponsiveModalScaffold(
            title = "Privacidade & Política de Retenção",
            headerIcon = Icons.Default.Security,
            onDismiss = onDismiss,
            closeButtonTestTag = "close_retention_dialog_btn",
            metrics = metrics,
            footerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("retention_done_btn")
                    ) {
                        Text(
                            text = "Concluir",
                            color = Color(0xFF1E1400),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        ) {
            Text(
                text = "• Documentos de visitantes são criptografados localmente (AES-256-GCM) e mascarados nas listas.\n• Os avisos permanecem exclusivamente offline neste aparelho sem compartilhamento automático com terceiros.",
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1)
            )

            HorizontalDivider(color = Color(0xFF283548))

            Text(
                text = "Prazo para retenção de avisos concluídos ou cancelados:",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = BpsGoldLight
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NoticeSecurityManager.RETENTION_OPTIONS_DAYS.forEach { days ->
                    FilterChip(
                        selected = currentRetentionDays == days,
                        onClick = { onSelectRetentionDays(days) },
                        label = { Text("$days dias", fontSize = 11.5.sp, maxLines = 1, softWrap = false) },
                        modifier = Modifier.testTag("retention_days_chip_$days")
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (eligibleNotices.isEmpty()) {
                            "Nenhum aviso concluído/cancelado ultrapassou o prazo de $currentRetentionDays dias."
                        } else {
                            "${eligibleNotices.size} aviso(s) concluído(s)/cancelado(s) ultrapassaram $currentRetentionDays dias. Nenhuma exclusão ocorre de forma silenciosa."
                        },
                        fontSize = 11.5.sp,
                        color = if (eligibleNotices.isNotEmpty()) Color(0xFFFCD34D) else Color(0xFF94A3B8)
                    )
                    if (eligibleNotices.isNotEmpty()) {
                        Button(
                            onClick = onRequestPurge,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("purge_expired_notices_btn")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Revisar e Remover Antigos (${eligibleNotices.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
