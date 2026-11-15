package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BpsHeader
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.viewmodel.BpsViewModel

enum class BpsNavTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val headerSubtitle: String
) {
    SHIFT_HANDOVER("Passagem", Icons.Default.AssignmentTurnedIn, "Registro de Passagem de Posto"),
    IMAGE_TO_PDF("Img/PDF", Icons.Default.PictureAsPdf, "Conversor de Imagens para PDF"),
    CUSTOM_TEMPLATE("Modelo", Icons.Default.Tune, "Personalização do Modelo de Checklist"),
    EMPLOYEES("Equipe", Icons.Default.Badge, "Cadastro e Gestão de Colaboradores"),
    HISTORY("Histórico", Icons.Default.History, "Histórico de Registros de Posto"),
    RESIDENT_NOTICES("Avisos", Icons.Default.Campaign, "Avisos e Visitas de Moradores")
}

@Composable
fun MainAppScreen(
    viewModel: BpsViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(BpsNavTab.SHIFT_HANDOVER) }

    // If not on Home tab, back press navigates back to Home tab
    BackHandler(enabled = selectedTab != BpsNavTab.SHIFT_HANDOVER) {
        selectedTab = BpsNavTab.SHIFT_HANDOVER
    }

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val pendingNoticesCount by viewModel.pendingNoticesCount.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BpsDarkBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            BpsHeader(
                subtitle = selectedTab.headerSubtitle,
                isOnline = isOnline
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF070B14),
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                modifier = Modifier.testTag("bps_bottom_navigation")
            ) {
                BpsNavTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            val badgeCount = when (tab) {
                                BpsNavTab.HISTORY -> pendingCount
                                BpsNavTab.RESIDENT_NOTICES -> pendingNoticesCount
                                else -> 0
                            }
                            val badgeBg = if (tab == BpsNavTab.HISTORY) Color(0xFFEF4444) else Color(0xFFF59E0B)

                            if (badgeCount > 0) {
                                androidx.compose.material3.BadgedBox(
                                    badge = {
                                        androidx.compose.material3.Badge(
                                            containerColor = badgeBg,
                                            contentColor = Color.White
                                        ) {
                                            Text(badgeCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) BpsGold else Color(0xFF94A3B8)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) BpsGold else Color(0xFF94A3B8)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BpsGoldLight else Color(0xFF94A3B8)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            when (selectedTab) {
                BpsNavTab.SHIFT_HANDOVER -> ShiftHandoverScreen(
                    viewModel = viewModel,
                    onOpenResidentNotices = { selectedTab = BpsNavTab.RESIDENT_NOTICES }
                )
                BpsNavTab.IMAGE_TO_PDF -> ImageToPdfScreen(viewModel = viewModel)
                BpsNavTab.CUSTOM_TEMPLATE -> TemplateSettingsScreen(viewModel = viewModel)
                BpsNavTab.EMPLOYEES -> EmployeeManagementScreen(viewModel = viewModel)
                BpsNavTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                BpsNavTab.RESIDENT_NOTICES -> ResidentNoticesScreen(viewModel = viewModel)
            }
        }
    }
}
