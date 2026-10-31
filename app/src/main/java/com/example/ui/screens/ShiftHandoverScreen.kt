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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChecklistTemplateItem
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldDark
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.BpsSilver
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOk
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.BpsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftHandoverScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val templateItems by viewModel.templateItems.collectAsStateWithLifecycle()

    val activeItems = remember(templateItems) {
        templateItems.filter { it.isEnabled }.sortedBy { it.orderIndex }
    }

    val formattedMessage = remember(formState, activeItems) {
        viewModel.buildFormattedMessage(activeItems)
    }

    val missingValidation by remember(formState, activeItems) {
        derivedStateOf { viewModel.validateRequiredItems(activeItems) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
                text = "Preencha o checklist para gerar a mensagem formatada para o WhatsApp da empresa",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        // 2. Employee Assignment Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_assignment_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                            text = "Porteiro que Recebe o Posto",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Selecione o funcionário cadastrado ou digite o nome:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick employee chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        employees.forEach { emp ->
                            val isSelected = formState.receivingEmployee.equals(emp.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateReceivingEmployee(emp.name) },
                                label = { Text(emp.name) },
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

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = formState.receivingEmployee,
                        onValueChange = { viewModel.updateReceivingEmployee(it) },
                        label = { Text("Nome do Porteiro que assume") },
                        placeholder = { Text("Ex: Higor") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receiving_employee_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BpsGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formState.departingEmployee,
                        onValueChange = { viewModel.updateDepartingEmployee(it) },
                        label = { Text("Porteiro que está passando o posto (Opcional)") },
                        placeholder = { Text("Ex: Carlos Silva") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("departing_employee_input"),
                        singleLine = true
                    )
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
                // Specialized Camera CFTV Card
                CamerasChecklistCard(
                    item = item,
                    workingCount = formState.camerasWorkingCount,
                    mon1 = formState.monitor1Details,
                    mon2 = formState.monitor2Details,
                    onUpdate = { wc, m1, m2 ->
                        viewModel.updateCamerasInfo(wc, m1, m2)
                    }
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

        // 4. General Observations & Notes
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
                }
            }
        }

        // 5. WhatsApp Message Live Preview
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("whatsapp_preview_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0C1322)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, WhatsAppGreen.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = WhatsAppGreen,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pré-visualização do WhatsApp",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "${formattedMessage.length} caracteres",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated WhatsApp speech bubble
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = formattedMessage,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }
            }
        }

        // 6. Action Buttons
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Send to WhatsApp Button (Primary)
                Button(
                    onClick = {
                        viewModel.sendToWhatsApp(context, formattedMessage)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("send_whatsapp_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enviar no WhatsApp da Empresa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Copy message button
                    OutlinedButton(
                        onClick = {
                            viewModel.copyToClipboard(context, formattedMessage)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_text_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BpsSilver
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Texto", fontWeight = FontWeight.SemiBold)
                    }

                    // Save report only
                    OutlinedButton(
                        onClick = {
                            viewModel.saveCurrentReportToHistory(formattedMessage)
                            android.widget.Toast.makeText(context, "Registro salvo no histórico!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
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
                        Text("Salvar Registro", fontWeight = FontWeight.SemiBold)
                    }
                }

                // PDF Export & Share Card (E-mail, WhatsApp as PDF document, Local Save)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_export_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF131C31)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, BpsGold.copy(alpha = 0.5f))
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
                                    text = "Exportar Relatório em PDF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Salvar em arquivo ou enviar por E-mail e WhatsApp",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.exportAndSharePdf(context, activeItems)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("share_pdf_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BpsGold,
                                    contentColor = Color(0xFF1E1400)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Enviar PDF / E-mail",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.exportAndViewPdf(context, activeItems)
                                },
                                modifier = Modifier
                                    .weight(0.7f)
                                    .height(46.dp)
                                    .testTag("view_pdf_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                            ) {
                                Text(
                                    text = "Visualizar",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // OK Button
                FilterChip(
                    selected = currentState.status == "OK",
                    onClick = { onStatusChange("OK", "") },
                    label = { Text("OK", fontSize = 12.sp) },
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

                // Atenção Button
                FilterChip(
                    selected = currentState.status == "ATENCAO",
                    onClick = { onStatusChange("ATENCAO", obsText) },
                    label = { Text("Atenção", fontSize = 12.sp) },
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

                // Defeito / Fora do ar Button
                FilterChip(
                    selected = currentState.status == "DEFEITO",
                    onClick = { onStatusChange("DEFEITO", obsText) },
                    label = { Text("Defeito", fontSize = 12.sp) },
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
                                    .height(42.dp)
                                    .testTag("take_photo_btn_${item.id}"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = BpsGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tirar Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BpsGoldLight)
                            }

                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("gallery_photo_btn_${item.id}"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeria", fontSize = 12.sp)
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
                                    modifier = Modifier.testTag("remove_photo_btn_${item.id}")
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPhotoPreviewDialog = false },
            title = {
                Text(
                    text = "Foto: ${item.emoji} ${item.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Obs: $obsText",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPhotoPreviewDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
fun CamerasChecklistCard(
    item: ChecklistTemplateItem,
    workingCount: String,
    mon1: String,
    mon2: String,
    onUpdate: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cameras_cftv_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, BpsGold.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = item.emoji, fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CFTV & Monitoramento de Câmeras",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Status de funcionamento e câmeras fora do ar",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets for quick selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onUpdate(
                            "28 funcionando",
                            "1 Câmera 15 fora do ar",
                            "2 Câmeras 11,15 e 16 fora do ar"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Padrão BPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onUpdate(
                            "Todas 32 funcionando",
                            "1 Todas operantes",
                            "2 Todas operantes"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Todas 100% OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = workingCount,
                onValueChange = { onUpdate(it, mon1, mon2) },
                label = { Text("Total de Câmeras Funcionando") },
                placeholder = { Text("Ex: 28 funcionando") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cameras_working_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = mon1,
                onValueChange = { onUpdate(workingCount, it, mon2) },
                label = { Text("Monitor 1") },
                placeholder = { Text("Ex: 1 Câmera 15 fora do ar") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monitor_1_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = mon2,
                onValueChange = { onUpdate(workingCount, mon1, it) },
                label = { Text("Monitor 2") },
                placeholder = { Text("Ex: 2 Câmeras 11,15 e 16 fora do ar") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monitor_2_input"),
                singleLine = true
            )
        }
    }
}
