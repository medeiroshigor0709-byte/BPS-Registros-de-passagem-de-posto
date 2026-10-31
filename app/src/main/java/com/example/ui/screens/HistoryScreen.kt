package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ShiftReport
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.BpsSilver
import com.example.ui.theme.StatusError
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.BpsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                report.reportContent.contains(searchQuery, ignoreCase = true)

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
                    text = "Consulte relatórios arquivados, sincronize checklists salvos offline e reenvie para o WhatsApp.",
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

                    androidx.compose.material3.FilterChip(
                        selected = filterSyncStatus == "TODOS",
                        onClick = { filterSyncStatus = "TODOS" },
                        label = { Text("Todos (${reports.size})", fontSize = 12.sp) }
                    )

                    androidx.compose.material3.FilterChip(
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

                    androidx.compose.material3.FilterChip(
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
                            viewModel.sendToWhatsApp(context, report.reportContent)
                        },
                        onCopy = {
                            viewModel.copyToClipboard(context, report.reportContent)
                        },
                        onViewDetails = {
                            reportToView = report
                        },
                        onExportPdf = {
                            val activeItems = viewModel.templateItems.value.filter { it.isEnabled }
                            val tempState = com.example.ui.viewmodel.ShiftFormUiState(
                                greeting = report.greeting,
                                postName = report.postName,
                                receivingEmployee = report.receivingEmployee,
                                departingEmployee = report.departingEmployee,
                                camerasWorkingCount = report.cameraWorkingCount,
                                monitor1Details = report.monitor1Details,
                                monitor2Details = report.monitor2Details,
                                generalNotes = report.generalNotes
                            )
                            val file = com.example.util.PdfReportGenerator.generateShiftPdf(
                                context = context,
                                formState = tempState,
                                activeItems = activeItems
                            )
                            com.example.util.PdfReportGenerator.sharePdf(context, file)
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

    // View Details Modal
    reportToView?.let { report ->
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        val formattedDate = dateFormat.format(Date(report.timestamp))

        AlertDialog(
            onDismissRequest = { reportToView = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = BpsGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registro de Passagem",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📅 Data/Hora: $formattedDate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BpsGoldLight
                    )
                    Text(
                        text = "👤 Passo o posto para: ${report.receivingEmployee}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (report.departingEmployee.isNotBlank()) {
                        Text(
                            text = "🚪 Porteiro saindo: ${report.departingEmployee}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Texto formatado do WhatsApp:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = report.reportContent,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val activeItems = viewModel.templateItems.value.filter { it.isEnabled }
                            val tempState = com.example.ui.viewmodel.ShiftFormUiState(
                                greeting = report.greeting,
                                postName = report.postName,
                                receivingEmployee = report.receivingEmployee,
                                departingEmployee = report.departingEmployee,
                                camerasWorkingCount = report.cameraWorkingCount,
                                monitor1Details = report.monitor1Details,
                                monitor2Details = report.monitor2Details,
                                generalNotes = report.generalNotes
                            )
                            val file = com.example.util.PdfReportGenerator.generateShiftPdf(
                                context = context,
                                formState = tempState,
                                activeItems = activeItems
                            )
                            com.example.util.PdfReportGenerator.sharePdf(context, file)
                        }
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = BpsGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp, color = BpsGoldLight)
                    }

                    Button(
                        onClick = {
                            viewModel.sendToWhatsApp(context, report.reportContent)
                            reportToView = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reenviar", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToView = null }) {
                    Text("Fechar")
                }
            }
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
                        viewModel.deleteReport(report)
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_report_card_${report.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Send to WhatsApp
                    Button(
                        onClick = onSendWhatsApp,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Copy
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 12.sp)
                    }

                    // View details
                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver", fontSize = 12.sp)
                    }

                    // PDF export
                    OutlinedButton(
                        onClick = onExportPdf,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
