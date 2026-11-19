package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CameraChannelState
import com.example.data.model.CameraMonitoringState
import com.example.data.model.CameraStatus
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.MonitorSummary
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOk

@Composable
fun CameraMonitoringCard(
    item: ChecklistTemplateItem,
    cameraState: CameraMonitoringState,
    onSetCameraStatus: (monitorNumber: Int, cameraNumber: Int, status: CameraStatus) -> Unit,
    onSetAllMonitorCameras: (monitorNumber: Int, status: CameraStatus) -> Unit,
    onApplyAllWorking: () -> Unit,
    onApplyDefaultBpsPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var monitor1Expanded by remember { mutableStateOf(true) }
    var monitor2Expanded by remember { mutableStateOf(true) }

    val m1Summary = cameraState.monitor1Summary
    val m2Summary = cameraState.monitor2Summary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cameras_cftv_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, BpsGold.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.size(38.dp)
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
                        text = "Monitor 1 (16 câmeras) e Monitor 2 (16 câmeras)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // General 32-Camera Summary Indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0C1322),
                border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cameras_general_summary")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "INDICADOR GERAL DE CÂMERAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsGoldLight,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatPill(
                            label = "Total",
                            value = "${cameraState.totalCameras}",
                            accentColor = BpsGold,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overall_total_cameras")
                        )
                        SummaryStatPill(
                            label = "Funcionando",
                            value = "${cameraState.totalWorking}",
                            accentColor = StatusOk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overall_working_cameras")
                        )
                        SummaryStatPill(
                            label = "Fora do ar",
                            value = "${cameraState.totalOffline}",
                            accentColor = StatusError,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overall_offline_cameras")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onApplyAllWorking,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("preset_all_cameras_ok"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Todas 32 OK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onApplyDefaultBpsPreset,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("preset_bps_default"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Padrão BPS (28 OK)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MONITOR 1 PANEL
            MonitorSectionPanel(
                monitorNumber = 1,
                summary = m1Summary,
                channels = cameraState.monitor1,
                isExpanded = monitor1Expanded,
                onToggleExpand = { monitor1Expanded = !monitor1Expanded },
                onSetCameraStatus = { camNum, status -> onSetCameraStatus(1, camNum, status) },
                onMarkAllWorking = { onSetAllMonitorCameras(1, CameraStatus.WORKING) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // MONITOR 2 PANEL
            MonitorSectionPanel(
                monitorNumber = 2,
                summary = m2Summary,
                channels = cameraState.monitor2,
                isExpanded = monitor2Expanded,
                onToggleExpand = { monitor2Expanded = !monitor2Expanded },
                onSetCameraStatus = { camNum, status -> onSetCameraStatus(2, camNum, status) },
                onMarkAllWorking = { onSetAllMonitorCameras(2, CameraStatus.WORKING) }
            )
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF162033),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonitorSectionPanel(
    monitorNumber: Int,
    summary: MonitorSummary,
    channels: List<CameraChannelState>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSetCameraStatus: (cameraNumber: Int, status: CameraStatus) -> Unit,
    onMarkAllWorking: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E1626),
        border = BorderStroke(1.dp, Color(0xFF283548)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monitor_${monitorNumber}_panel")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Monitor Header & Summary Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = BpsGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "MONITOR $monitorNumber",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BpsGoldLight
                        )
                        Text(
                            text = "Total: ${summary.total} • 🟢 ${summary.working} funcionando • 🔴 ${summary.offline} fora do ar",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.testTag("monitor_${monitorNumber}_summary_text")
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Recolher Monitor $monitorNumber" else "Expandir Monitor $monitorNumber",
                    tint = BpsGoldLight
                )
            }

            // Quick Summary Chips inside Monitor Panel (wraps cleanly on compact screens)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MonitorBadge("Total: ${summary.total}", Color(0xFF334155), Color.White)
                    MonitorBadge("Funcionando: ${summary.working}", StatusOk.copy(alpha = 0.2f), Color(0xFF86EFAC))
                    MonitorBadge("Fora do ar: ${summary.offline}", StatusError.copy(alpha = 0.2f), Color(0xFFFCA5A5))
                }
                if (summary.offline > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onMarkAllWorking,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("monitor_${monitorNumber}_all_ok_btn")
                    ) {
                        Text(
                            text = "Todas OK",
                            fontSize = 11.sp,
                            color = BpsGold,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(2.dp))
                    channels.forEach { channel ->
                        CameraChannelRow(
                            channel = channel,
                            onSelectStatus = { newStatus ->
                                onSetCameraStatus(channel.cameraNumber, newStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorBadge(text: String, bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CameraChannelRow(
    channel: CameraChannelState,
    onSelectStatus: (CameraStatus) -> Unit
) {
    val isWorking = channel.isWorking
    val rowBg = if (isWorking) Color(0xFF131D2E) else Color(0xFF2B1218)
    val borderColor = if (isWorking) Color(0xFF243247) else StatusError.copy(alpha = 0.6f)
    val camTagPrefix = "monitor_${channel.monitorNumber}_cam_${channel.cameraNumber.toString().padStart(2, '0')}"

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = rowBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(camTagPrefix)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isWorking) StatusOk else StatusError,
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = channel.cameraName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isWorking) "🟢 Funcionando normalmente" else "🔴 Fora do ar",
                        fontSize = 10.sp,
                        color = if (isWorking) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 2-button status selector with >= 48dp accessible height
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CameraStatusButton(
                    selected = isWorking,
                    label = "OK",
                    selectedColor = StatusOk,
                    icon = Icons.Default.CheckCircle,
                    contentDescription = "${channel.cameraName} funcionando normalmente",
                    onClick = { onSelectStatus(CameraStatus.WORKING) },
                    modifier = Modifier.testTag("${camTagPrefix}_working")
                )
                CameraStatusButton(
                    selected = !isWorking,
                    label = "Fora",
                    selectedColor = StatusError,
                    icon = Icons.Default.VideocamOff,
                    contentDescription = "${channel.cameraName} fora do ar",
                    onClick = { onSelectStatus(CameraStatus.OFFLINE) },
                    modifier = Modifier.testTag("${camTagPrefix}_offline")
                )
            }
        }
    }
}

@Composable
private fun CameraStatusButton(
    selected: Boolean,
    label: String,
    selectedColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) selectedColor else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (selected) selectedColor else Color(0xFF334155)),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFFCBD5E1),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
