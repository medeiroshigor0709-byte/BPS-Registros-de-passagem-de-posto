package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AttachmentType
import com.example.data.model.ChecklistTemplateItem
import com.example.ui.components.BpsResponsiveDialog
import com.example.ui.components.BpsResponsiveModalScaffold
import com.example.ui.components.CameraMonitoringCard
import com.example.ui.components.ObservationAttachmentsSection
import com.example.ui.components.PdfViewerDialog
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldDark
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOk
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.BpsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftHandoverScreen(
    viewModel: BpsViewModel,
    onOpenResidentNotices: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val templateItems by viewModel.templateItems.collectAsStateWithLifecycle()
    val residentNotices by viewModel.residentNotices.collectAsStateWithLifecycle()
    val attachmentFeedback by viewModel.attachmentFeedback.collectAsStateWithLifecycle()
    val pdfValidationError by viewModel.pdfValidationError.collectAsStateWithLifecycle()

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingReports by viewModel.pendingReports.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val pendingConfirmReportId by viewModel.pendingShareConfirmationReportId.collectAsStateWithLifecycle()
    var generatedPdfPreview by remember { mutableStateOf<File?>(null) }

    val activeItems = remember(templateItems) {
        templateItems.filter { it.isEnabled }.sortedBy { it.orderIndex }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("shift_handover_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "📋 Registro de Passagem de Posto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Preencha o checklist para gerar e enviar o relatório em PDF pelo WhatsApp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Offline alert banner if offline
        if (!isOnline) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("offline_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3F171B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Modo Offline Ativo (Sem Conexão)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Preencha normalmente. O checklist será salvo localmente no Room e o PDF poderá ser enviado pelo WhatsApp quando a conexão retornar.",
                                fontSize = 11.sp,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                    }
                }
            }
        }

        // Pending sync queue banner if there are offline saved reports
        if (pendingCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("pending_sync_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2638)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "⏳", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$pendingCount Checklist(s) Aguardando Envio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = BpsGoldLight
                                )
                                Text(
                                    text = "Salvos no banco Room sem internet",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        val firstPending = pendingReports.firstOrNull()
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (firstPending?.syncStatus == "AGUARDANDO CONFIRMAÇÃO") {
                                OutlinedButton(
                                    onClick = { viewModel.confirmPdfSent(firstPending.id, context) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Confirmar",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BpsGoldLight,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    firstPending?.let { report ->
                                        viewModel.shareSavedReportPdf(report, context)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Enviar PDF",
                                    fontSize = 12.sp,
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

        // 1. Post & Greeting Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_info_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Saudação & Posto de Serviço",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = BpsGoldLight
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Greeting chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Boa Noite", "Bom Dia", "Boa Tarde").forEach { greeting ->
                            val selected = formState.greeting == greeting
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateGreeting(greeting) },
                                label = { Text(greeting, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BpsGoldDark,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("greeting_chip_$greeting")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = formState.postName,
                        onValueChange = { viewModel.updatePostName(it) },
                        label = { Text("Nome do Posto (Ex: *PORTARIA*)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("post_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BpsGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        // 2. Employee Assignment Card (Nominal Identification for PDF & Handover)
        item {
            val departingTrim = formState.departingEmployee.trim()
            val receivingTrim = formState.receivingEmployee.trim()
            val isSamePerson = departingTrim.isNotBlank() &&
                receivingTrim.isNotBlank() &&
                departingTrim.equals(receivingTrim, ignoreCase = true)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_assignment_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSamePerson) StatusError.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BpsGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Identificação dos Porteiros (Obrigatório para PDF)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Os nomes informados abaixo preenchem automaticamente as assinaturas nominais no relatório PDF.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Porteiro que ENTREGA
                    Text(
                        text = "1. Porteiro que Entrega o Posto *",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = BpsGoldLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        employees.forEach { emp ->
                            val isSelected = departingTrim.equals(emp.name.trim(), ignoreCase = true)
                            val isAlreadyReceiving = receivingTrim.equals(emp.name.trim(), ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                enabled = !isAlreadyReceiving,
                                onClick = {
                                    if (!isAlreadyReceiving) {
                                        viewModel.updateDepartingEmployee(emp.name)
                                    }
                                },
                                label = {
                                    Text(
                                        if (isAlreadyReceiving) "${emp.name} (Assumindo)" else emp.name
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BpsGold,
                                    selectedLabelColor = Color(0xFF1E1400)
                                ),
                                modifier = Modifier.testTag("departing_employee_chip_${emp.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formState.departingEmployee,
                        onValueChange = { viewModel.updateDepartingEmployee(it) },
                        label = { Text("Nome do Porteiro que Entrega *") },
                        placeholder = { Text("Ex: Higor") },
                        isError = isSamePerson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("departing_employee_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BpsGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Porteiro que RECEBE / ASSUME
                    Text(
                        text = "2. Porteiro que Assume (Recebe) o Posto *",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = BpsGoldLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        employees.forEach { emp ->
                            val isSelected = receivingTrim.equals(emp.name.trim(), ignoreCase = true)
                            val isAlreadyDeparting = departingTrim.equals(emp.name.trim(), ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                enabled = !isAlreadyDeparting,
                                onClick = {
                                    if (!isAlreadyDeparting) {
                                        viewModel.updateReceivingEmployee(emp.name)
                                    }
                                },
                                label = {
                                    Text(
                                        if (isAlreadyDeparting) "${emp.name} (Entregando)" else emp.name
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BpsGold,
                                    selectedLabelColor = Color(0xFF1E1400)
                                ),
                                modifier = Modifier.testTag("employee_chip_${emp.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formState.receivingEmployee,
                        onValueChange = { viewModel.updateReceivingEmployee(it) },
                        label = { Text("Nome do Porteiro que Assume *") },
                        placeholder = { Text("Ex: Cláudia") },
                        isError = isSamePerson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receiving_employee_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BpsGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (isSamePerson) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusError.copy(alpha = 0.16f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("same_employee_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "O Porteiro que Entrega e o Porteiro que Assume não podem ser a mesma pessoa.",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Checklist Items
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Itens da Lista de Verificação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${activeItems.size} itens",
                    style = MaterialTheme.typography.labelMedium,
                    color = BpsGold
                )
            }
        }

        // Iterate through active items
        items(activeItems, key = { it.id }) { item ->
            if (item.isCamerasItem) {
                // Specialized 32-Camera CFTV Monitoring Card (Monitor 1: 16 + Monitor 2: 16)
                CameraMonitoringCard(
                    item = item,
                    cameraState = formState.cameraMonitoring,
                    onSetCameraStatus = { monitorNum, camNum, status ->
                        viewModel.setCameraStatus(monitorNum, camNum, status)
                    },
                    onSetAllMonitorCameras = { monitorNum, status ->
                        viewModel.setAllMonitorCameras(monitorNum, status)
                    },
                    onApplyAllWorking = { viewModel.applyAllCamerasWorking() },
                    onApplyDefaultBpsPreset = { viewModel.applyDefaultBpsCameraPreset() }
                )
            } else {
                StandardChecklistItemCard(
                    item = item,
                    currentState = formState.itemStates[item.id] ?: com.example.ui.viewmodel.ItemCheckState("OK"),
                    onStatusChange = { newStatus, obs ->
                        viewModel.updateItemStatus(item.id, newStatus, obs)
                    },
                    onAttachPhoto = { path ->
                        viewModel.attachPhotoToItem(item.id, path)
                    },
                    onRemovePhoto = {
                        viewModel.removePhotoFromItem(item.id)
                    },
                    onSaveBitmap = { bitmap ->
                        viewModel.saveBitmapToLocalCache(context, bitmap)
                    },
                    onCopyUri = { uri ->
                        viewModel.copyUriToLocalCache(context, uri)
                    }
                )
            }
        }

        // 4. General Observations & Notes + Persistent Attachments (Photos & PDFs)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("general_notes_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📝 Ocorrências / Observações Gerais",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.generalNotes,
                        onValueChange = { viewModel.updateGeneralNotes(it) },
                        placeholder = { Text("Registrar recados, visitas da supervisão, manutenções agendadas...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("general_notes_input"),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    ObservationAttachmentsSection(
                        attachments = formState.observationAttachments,
                        onAddPhotos = { uris ->
                            viewModel.attachObservationFiles(context, uris, AttachmentType.PHOTO)
                        },
                        onAddPdfs = { uris ->
                            viewModel.attachObservationFiles(context, uris, AttachmentType.PDF)
                        },
                        onRemoveAttachment = { att ->
                            viewModel.removeDraftAttachment(context, att)
                        },
                        feedback = attachmentFeedback,
                        onDismissFeedback = { viewModel.clearAttachmentFeedback() },
                        testTagPrefix = "obs"
                    )
                }
            }
        }

        // 4.5. Resident Notices Summary Panel (AVISOS PARA O PRÓXIMO TURNO)
        item {
            val todayCal = remember { java.util.Calendar.getInstance() }
            val tomorrowCal = remember {
                java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            }
            val dateFormatter = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US) }
            val todayIso = remember { dateFormatter.format(todayCal.time) }
            val tomorrowIso = remember { dateFormatter.format(tomorrowCal.time) }

            val activeHandoverNotices = remember(residentNotices, todayIso) {
                residentNotices
                    .filter { it.status in setOf("PENDENTE", "VISUALIZADO") && it.scheduledDate >= todayIso }
                    .sortedWith(compareBy<com.example.data.model.ResidentNotice> { it.scheduledDate }.thenBy { it.scheduledTime })
            }
            val pendingNotices = activeHandoverNotices.count { it.status == "PENDENTE" }
            val tomorrowVisits = activeHandoverNotices.count {
                it.scheduledDate == tomorrowIso && it.noticeType == "VISITA_PROGRAMADA"
            }
            val serviceProviders = activeHandoverNotices.count { it.noticeType == "PRESTADOR_SERVICO" }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("next_shift_notices_panel"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10192D)),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, BpsGold.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📢 AVISOS PARA O PRÓXIMO TURNO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BpsGoldLight,
                                modifier = Modifier.testTag("next_shift_notices_title")
                            )
                            Text(
                                text = if (activeHandoverNotices.isEmpty()) {
                                    "Nenhum aviso ativo pendente para esta passagem de turno."
                                } else {
                                    "Incluídos automaticamente no relatório PDF da passagem"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenResidentNotices,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("open_resident_notices_panel_btn")
                        ) {
                            Text(
                                text = "Ver Avisos",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BpsGoldLight,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (activeHandoverNotices.isNotEmpty()) {
                        HorizontalDivider(color = BpsGold.copy(alpha = 0.2f))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (pendingNotices > 0) {
                                Text(
                                    text = "• $pendingNotices aviso(s) pendente(s).",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFCD34D)
                                )
                            }
                            if (tomorrowVisits > 0) {
                                Text(
                                    text = "• $tomorrowVisits visita(s) programada(s) para amanhã.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                            if (serviceProviders > 0) {
                                Text(
                                    text = "• $serviceProviders prestador(es) de serviço aguardando atendimento.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                            val visualizadosCount = activeHandoverNotices.count { it.status == "VISUALIZADO" }
                            if (visualizadosCount > 0 && pendingNotices == 0) {
                                Text(
                                    text = "• $visualizadosCount aviso(s) visualizado(s) aguardando conclusão.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7DD3FC)
                                )
                            }
                        }

                        activeHandoverNotices.take(3).forEach { notice ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0B1220),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "[${notice.unitFullIdentifier}] ${notice.noticeTypeDisplayName} • ${notice.residentName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val extra = buildList {
                                            add("Data: ${notice.formattedScheduledDate()}${if (notice.scheduledTime.isNotBlank()) " ${notice.scheduledTime}" else ""}")
                                            if (notice.visitorName.isNotBlank()) add("Visitante: ${notice.visitorName}")
                                        }.joinToString(" • ")
                                        Text(
                                            text = extra,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = notice.statusDisplayName.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (notice.status == "PENDENTE") Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Exclusive PDF Export & WhatsApp PDF Send + Local Save Actions
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Exclusive PDF Export & Share Card (WhatsApp PDF document)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_export_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF131C31)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BpsGold.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BpsGoldDark,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Relatório Oficial em PDF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Envio exclusivo em PDF pelo WhatsApp • Assinatura nominal automática",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        val departingOk = formState.departingEmployee.trim()
                        val receivingOk = formState.receivingEmployee.trim()
                        val isNominalValid = departingOk.isNotBlank() &&
                            receivingOk.isNotBlank() &&
                            !departingOk.equals(receivingOk, ignoreCase = true)

                        Spacer(modifier = Modifier.height(10.dp))

                        if (pdfValidationError != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusError.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_validation_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pdfValidationError!!,
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        } else if (isNominalValid) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusOk.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusOk.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("pdf_nominal_preview_banner")
                            ) {
                                Text(
                                    text = "Assinaturas no PDF: Entrega ($departingOk) → Assume ($receivingOk)",
                                    color = Color(0xFF86EFAC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Preencha quem entrega e quem assume o posto acima para liberar as assinaturas automáticas do PDF.",
                                    color = BpsGoldLight,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.exportAndSharePdf(context, activeItems)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag("share_pdf_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhatsAppGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enviar PDF pelo WhatsApp",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    generatedPdfPreview = viewModel.generateShiftPdfFile(context, activeItems)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("view_pdf_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BpsGoldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BpsGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Visualizar PDF antes de enviar",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Local save actions
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackSaveButtons = maxWidth < 310.dp
                    if (stackSaveButtons) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveCurrentReportToHistory(activeItems)
                                    android.widget.Toast.makeText(context, "Registro salvo no histórico!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("save_report_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BpsGoldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = BpsGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvar Registro", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    viewModel.saveReportOffline(context, activeItems)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("save_offline_room_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = BpsGoldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, BpsGold.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "Salvar Offline",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveCurrentReportToHistory(activeItems)
                                    android.widget.Toast.makeText(context, "Registro salvo no histórico!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_report_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BpsGoldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BpsGold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salvar Registro", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    viewModel.saveReportOffline(context, activeItems)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_offline_room_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = BpsGoldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, BpsGold.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "Salvar Offline",
                                    fontSize = 12.sp,
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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    generatedPdfPreview?.let { pdfFile ->
        PdfViewerDialog(
            pdfFile = pdfFile,
            title = pdfFile.name,
            onDismiss = { generatedPdfPreview = null },
            onShareClick = {
                generatedPdfPreview = null
                viewModel.exportAndSharePdf(context, activeItems)
            }
        )
    }

    pendingConfirmReportId?.let { reportId ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissShareConfirmation() },
            modifier = Modifier.testTag("confirm_pdf_sent_dialog"),
            title = {
                Text(
                    text = "Confirmar envio do PDF?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "O compartilhamento do arquivo PDF foi aberto. Confirme apenas após concluir o envio do PDF pelo WhatsApp para marcar este registro como enviado."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPdfSent(reportId, context) },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    modifier = Modifier.testTag("confirm_pdf_sent_btn")
                ) {
                    Text("Confirmar Envio", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissShareConfirmation() },
                    modifier = Modifier.testTag("dismiss_confirm_pdf_btn")
                ) {
                    Text("Ainda não enviado")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StandardChecklistItemCard(
    item: ChecklistTemplateItem,
    currentState: com.example.ui.viewmodel.ItemCheckState,
    onStatusChange: (String, String) -> Unit,
    onAttachPhoto: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    onSaveBitmap: (android.graphics.Bitmap) -> String,
    onCopyUri: (android.net.Uri) -> String?,
    modifier: Modifier = Modifier
) {
    var obsText by remember(currentState) { mutableStateOf(currentState.observation) }
    var showPhotoPreviewDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val path = onSaveBitmap(bitmap)
            onAttachPhoto(path)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = onCopyUri(uri)
            if (path != null) {
                onAttachPhoto(path)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("checklist_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (currentState.status) {
                "OK" -> StatusOk.copy(alpha = 0.4f)
                "ATENCAO" -> StatusWarning.copy(alpha = 0.6f)
                "DEFEITO" -> StatusError.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.emoji,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.isRequired) {
                            Text(
                                text = "Obrigatório",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BpsGold
                            )
                        } else {
                            Text(
                                text = "Opcional",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Current status badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (currentState.status) {
                        "OK" -> StatusOk.copy(alpha = 0.2f)
                        "ATENCAO" -> StatusWarning.copy(alpha = 0.2f)
                        "DEFEITO" -> StatusError.copy(alpha = 0.2f)
                        else -> Color(0xFF334155)
                    }
                ) {
                    Text(
                        text = when (currentState.status) {
                            "OK" -> "OK"
                            "ATENCAO" -> "Atenção"
                            "DEFEITO" -> "Defeito"
                            else -> "N/A"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (currentState.status) {
                            "OK" -> StatusOk
                            "ATENCAO" -> StatusWarning
                            "DEFEITO" -> StatusError
                            else -> Color(0xFFCBD5E1)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Selector Chips
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useWrapChips = maxWidth < 330.dp
                if (useWrapChips) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = currentState.status == "OK",
                            onClick = { onStatusChange("OK", "") },
                            label = { Text("OK", fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusOk,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = currentState.status == "ATENCAO",
                            onClick = { onStatusChange("ATENCAO", obsText) },
                            label = { Text("Atenção", fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusWarning,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = currentState.status == "DEFEITO",
                            onClick = { onStatusChange("DEFEITO", obsText) },
                            label = { Text("Defeito", fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusError,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = currentState.status == "OK",
                            onClick = { onStatusChange("OK", "") },
                            label = { Text("OK", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusOk,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentState.status == "ATENCAO",
                            onClick = { onStatusChange("ATENCAO", obsText) },
                            label = { Text("Atenção", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusWarning,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentState.status == "DEFEITO",
                            onClick = { onStatusChange("DEFEITO", obsText) },
                            label = { Text("Defeito", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusError,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Observation & Photo field when status is not OK (or irregularity)
            AnimatedVisibility(visible = currentState.status != "OK") {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = obsText,
                        onValueChange = {
                            obsText = it
                            onStatusChange(currentState.status, it)
                        },
                        label = { Text("Detalhes / Observação da irregularidade") },
                        placeholder = { Text("Ex: fora de funcionamento, necessita bateria...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("obs_input_${item.id}"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "📸 Foto da Irregularidade:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsGoldLight
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentState.photoPath == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("take_photo_btn_${item.id}"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = BpsGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tirar Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BpsGoldLight, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }

                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("gallery_photo_btn_${item.id}"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeria", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        // Attached photo card with preview
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                        .clickable { showPhotoPreviewDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = File(currentState.photoPath),
                                        contentDescription = "Foto da irregularidade",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Foto Anexada",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Incluída no anexo do PDF",
                                        fontSize = 11.sp,
                                        color = BpsGoldLight
                                    )
                                    Text(
                                        text = "Toque para ampliar",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                IconButton(
                                    onClick = onRemovePhoto,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("remove_photo_btn_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remover foto",
                                        tint = StatusError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPhotoPreviewDialog && currentState.photoPath != null) {
        BpsResponsiveDialog(
            onDismissRequest = { showPhotoPreviewDialog = false },
            dialogTestTag = "checklist_photo_preview_dialog_${item.id}",
            dialogMaxWidth = 560.dp
        ) { metrics ->
            BpsResponsiveModalScaffold(
                title = "Foto: ${item.emoji} ${item.title}",
                onDismiss = { showPhotoPreviewDialog = false },
                metrics = metrics,
                footerContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showPhotoPreviewDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Fechar", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = (metrics.availableHeight * 0.55f).coerceAtLeast(180.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(currentState.photoPath),
                        contentDescription = "Foto ampliada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                if (obsText.isNotBlank()) {
                    Text(
                        text = "Obs: $obsText",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

