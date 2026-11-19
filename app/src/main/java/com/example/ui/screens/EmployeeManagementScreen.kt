package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Employee
import com.example.ui.components.BpsResponsiveDialog
import com.example.ui.components.BpsResponsiveModalScaffold
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldDark
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.BpsSilver
import com.example.ui.theme.StatusError
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.BpsViewModel

@Composable
fun EmployeeManagementScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val employees by viewModel.employees.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }

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
                    text = "👥 Cadastro de Colaboradores BPS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Gerencie a equipe de porteiros, vigilantes e supervisores para seleção rápida na passagem de posto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Top add button
            item {
                Button(
                    onClick = {
                        employeeToEdit = null
                        showEditDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("add_employee_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BpsGoldDark,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cadastrar Novo Funcionário",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                Text(
                    text = "Equipe Ativa (${employees.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(employees, key = { it.id }) { emp ->
                EmployeeCard(
                    employee = emp,
                    onEdit = {
                        employeeToEdit = emp
                        showEditDialog = true
                    },
                    onDelete = {
                        employeeToDelete = emp
                    },
                    onWhatsAppClick = { phone ->
                        try {
                            val cleanNumber = phone.replace(Regex("[^0-9]"), "")
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Add / Edit Employee Dialog
    if (showEditDialog) {
        EmployeeEditDialog(
            employee = employeeToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.saveEmployee(updated)
                showEditDialog = false
            }
        )
    }

    // Delete confirmation dialog
    employeeToDelete?.let { emp ->
        AlertDialog(
            onDismissRequest = { employeeToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusError
                )
            },
            title = { Text("Excluir colaborador?") },
            text = { Text("Deseja realmente remover o colaborador '${emp.name}' da equipe?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEmployee(emp)
                        employeeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { employeeToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmployeeCard(
    employee: Employee,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onWhatsAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("employee_card_${employee.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar badge with initials
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.5.dp, BpsGold),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = employee.name.take(2).uppercase(),
                            color = BpsGoldLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = employee.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = employee.role,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BpsGoldLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Edit & Delete actions (>= 48dp touch targets)
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = BpsSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = StatusError.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            // Details FlowRow (Turno, Matrícula, WhatsApp) - wraps gracefully on narrow widths
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = employee.shift,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                if (employee.badgeNumber.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = employee.badgeNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                if (employee.phone.isNotBlank()) {
                    Surface(
                        onClick = { onWhatsAppClick(employee.phone) },
                        shape = RoundedCornerShape(6.dp),
                        color = WhatsAppGreen.copy(alpha = 0.15f),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "WhatsApp",
                                tint = WhatsAppGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "WhatsApp",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WhatsAppGreen,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeEditDialog(
    employee: Employee?,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit
) {
    var name by remember { mutableStateOf(employee?.name ?: "") }
    var role by remember { mutableStateOf(employee?.role ?: "Porteiro") }
    var shift by remember { mutableStateOf(employee?.shift ?: "Noturno (19h às 07h)") }
    var phone by remember { mutableStateOf(employee?.phone ?: "") }
    var badgeNumber by remember { mutableStateOf(employee?.badgeNumber ?: "") }

    val roles = listOf("Porteiro", "Controlador de Acesso", "Vigilante", "Folguista", "Supervisor Operacional", "Líder de Equipe")
    val shifts = listOf("Noturno (19h às 07h)", "Diurno (07h às 19h)", "Escala 12x36", "Comercial (08h às 18h)", "Geral")

    var roleExpanded by remember { mutableStateOf(false) }
    var shiftExpanded by remember { mutableStateOf(false) }

    BpsResponsiveDialog(
        onDismissRequest = onDismiss,
        dialogTestTag = "employee_edit_dialog",
        dialogMaxWidth = 560.dp
    ) { metrics ->
        BpsResponsiveModalScaffold(
            title = if (employee == null) "Novo Funcionário BPS" else "Editar Funcionário",
            headerIcon = Icons.Default.Person,
            onDismiss = onDismiss,
            closeButtonTestTag = "close_employee_dialog_btn",
            footerTestTag = "employee_dialog_footer",
            metrics = metrics,
            footerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("employee_dialog_cancel_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text(
                            text = "Cancelar",
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val emp = employee?.copy(
                                    name = name.trim(),
                                    role = role,
                                    shift = shift,
                                    phone = phone.trim(),
                                    badgeNumber = badgeNumber.trim()
                                ) ?: Employee(
                                    name = name.trim(),
                                    role = role,
                                    shift = shift,
                                    phone = phone.trim(),
                                    badgeNumber = badgeNumber.trim()
                                )
                                onSave(emp)
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BpsGoldDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("employee_dialog_save_button")
                    ) {
                        Text(
                            text = "Salvar",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome Completo") },
                placeholder = { Text("Ex: Higor") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_name_dialog_input"),
                singleLine = true
            )

            // Role Dropdown
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = { roleExpanded = !roleExpanded }
            ) {
                OutlinedTextField(
                    value = role,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cargo / Função") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = roleExpanded,
                    onDismissRequest = { roleExpanded = false }
                ) {
                    roles.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r) },
                            onClick = {
                                role = r
                                roleExpanded = false
                            }
                        )
                    }
                }
            }

            // Shift Dropdown
            ExposedDropdownMenuBox(
                expanded = shiftExpanded,
                onExpandedChange = { shiftExpanded = !shiftExpanded }
            ) {
                OutlinedTextField(
                    value = shift,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Turno / Escala") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shiftExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = shiftExpanded,
                    onDismissRequest = { shiftExpanded = false }
                ) {
                    shifts.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = {
                                shift = s
                                shiftExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefone / WhatsApp (Opcional)") },
                placeholder = { Text("Ex: (11) 98765-4321") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = badgeNumber,
                onValueChange = { badgeNumber = it },
                label = { Text("Matrícula / Crachá (Opcional)") },
                placeholder = { Text("Ex: BPS-104") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
