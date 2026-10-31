package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
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
import com.example.ui.viewmodel.BpsViewModel

@Composable
fun TemplateSettingsScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    val templateItems by viewModel.templateItems.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ChecklistTemplateItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ChecklistTemplateItem?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

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
                    text = "⚙️ Personalizar Modelo de Relatório",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Adicione ou remova itens e defina quais são obrigatórios ou opcionais na passagem de posto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Top action bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            itemToEdit = null
                            showEditDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_template_item_button"),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adicionar Item", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showResetConfirmDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("reset_template_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = BpsGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Padrão BPS", fontSize = 12.sp)
                    }
                }
            }

            item {
                Text(
                    text = "Itens do Modelo Cadastrados (${templateItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(templateItems, key = { it.id }) { item ->
                TemplateItemCard(
                    item = item,
                    onToggleRequired = { viewModel.toggleItemRequired(item) },
                    onToggleEnabled = { viewModel.toggleItemEnabled(item) },
                    onEdit = {
                        itemToEdit = item
                        showEditDialog = true
                    },
                    onDelete = {
                        itemToDelete = item
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Add / Edit Dialog
    if (showEditDialog) {
        TemplateItemEditDialog(
            item = itemToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { updatedItem ->
                viewModel.saveTemplateItem(updatedItem)
                showEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusError
                )
            },
            title = { Text("Remover item do modelo?") },
            text = { Text("Deseja realmente remover '${item.emoji} ${item.title}' do modelo de checklist?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTemplateItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Remover", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Reset Template to Default Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = BpsGold
                )
            },
            title = { Text("Restaurar modelo padrão BPS?") },
            text = {
                Text("Isso redefinirá a lista para todos os 14 itens originais (Celular, Carregador, Interfone, Livro de ocorrência, Rádio HTS, Cerca elétrica, Câmaras, Guarda chuva, Guarda Sol, Controle, Caixa d'água, Ar condicionado, Chaves, Extintor pó químico).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetTemplateToDefault()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BpsGoldDark)
                ) {
                    Text("Confirmar Restauração", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TemplateItemCard(
    item: ChecklistTemplateItem,
    onToggleRequired: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("template_item_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isRequired && item.isEnabled) BpsGold.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji icon container
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = item.emoji, fontSize = 22.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = item.category,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Edit and Delete buttons
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar item",
                        tint = BpsSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remover item",
                        tint = StatusError.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(10.dp))

            // Badges & Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mandatory vs Optional toggle chip
                FilterChip(
                    selected = item.isRequired,
                    onClick = onToggleRequired,
                    label = {
                        Text(
                            text = if (item.isRequired) "★ Obrigatório" else "☆ Opcional",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BpsGoldDark,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("toggle_required_${item.id}")
                )

                // Active toggle switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.isEnabled) "Ativo no relatório" else "Inativo",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = item.isEnabled,
                        onCheckedChange = { onToggleEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BpsGold,
                            checkedTrackColor = BpsGoldDark.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("toggle_active_${item.id}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateItemEditDialog(
    item: ChecklistTemplateItem?,
    onDismiss: () -> Unit,
    onSave: (ChecklistTemplateItem) -> Unit
) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var emoji by remember { mutableStateOf(item?.emoji ?: "📋") }
    var category by remember { mutableStateOf(item?.category ?: "Geral") }
    var isRequired by remember { mutableStateOf(item?.isRequired ?: true) }
    var isCamerasItem by remember { mutableStateOf(item?.isCamerasItem ?: false) }

    val quickEmojis = listOf("📱", "🔌", "📞", "📔", "📻", "🚧", "🎥", "☔️", "⛱️", "🕹️", "🌊", "💨", "🗝️", "🧯", "🔦", "🚪", "🚗", "🛡️", "📋", "🚨", "☕", "🖥️")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (item == null) "Novo Item no Modelo" else "Editar Item do Modelo",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome do Item") },
                    placeholder = { Text("Ex: Extintor pó químico, Celular...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_item_title_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji") },
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("dialog_item_emoji_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Escolha rápida de emoji:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            quickEmojis.take(12).forEach { e ->
                                Text(
                                    text = e,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .clickable { emoji = e }
                                        .padding(2.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria") },
                    placeholder = { Text("Ex: Segurança, Equipamentos...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Mandatory toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Item Obrigatório?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Porteiro deve confirmar no checklist",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRequired,
                        onCheckedChange = { isRequired = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BpsGold,
                            checkedTrackColor = BpsGoldDark.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("dialog_toggle_required")
                    )
                }

                // Cameras CFTV toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "CFTV / Monitoramento de Câmeras?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Exibe campos de Monitores 1 e 2",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isCamerasItem,
                        onCheckedChange = { isCamerasItem = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BpsGold,
                            checkedTrackColor = BpsGoldDark.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val newItem = item?.copy(
                            title = title.trim(),
                            emoji = if (emoji.isNotBlank()) emoji.trim() else "📋",
                            category = if (category.isNotBlank()) category.trim() else "Geral",
                            isRequired = isRequired,
                            isCamerasItem = isCamerasItem
                        ) ?: ChecklistTemplateItem(
                            title = title.trim(),
                            emoji = if (emoji.isNotBlank()) emoji.trim() else "📋",
                            category = if (category.isNotBlank()) category.trim() else "Geral",
                            isRequired = isRequired,
                            isCamerasItem = isCamerasItem,
                            orderIndex = 100
                        )
                        onSave(newItem)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BpsGoldDark),
                modifier = Modifier.testTag("dialog_save_item_button")
            ) {
                Text("Salvar Item", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
