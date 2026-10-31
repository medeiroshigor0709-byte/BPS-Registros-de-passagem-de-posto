package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BpsHeader
import com.example.ui.theme.BpsDarkSurface
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.viewmodel.BpsViewModel

enum class BpsNavTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val headerSubtitle: String
) {
    SHIFT_HANDOVER("Passagem", Icons.Default.AssignmentTurnedIn, "Registro de Passagem de Posto"),
    CUSTOM_TEMPLATE("Modelo", Icons.Default.Tune, "Personalização do Modelo de Checklist"),
    EMPLOYEES("Equipe", Icons.Default.Badge, "Cadastro e Gestão de Colaboradores"),
    HISTORY("Histórico", Icons.Default.History, "Histórico de Registros de Posto")
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                modifier = Modifier.testTag("bps_bottom_navigation")
            ) {
                BpsNavTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (tab == BpsNavTab.HISTORY && pendingCount > 0) {
                                androidx.compose.material3.BadgedBox(
                                    badge = {
                                        androidx.compose.material3.Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text(pendingCount.toString())
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
                                fontSize = 11.sp,
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
        ) {
            when (selectedTab) {
                BpsNavTab.SHIFT_HANDOVER -> ShiftHandoverScreen(viewModel = viewModel)
                BpsNavTab.CUSTOM_TEMPLATE -> TemplateSettingsScreen(viewModel = viewModel)
                BpsNavTab.EMPLOYEES -> EmployeeManagementScreen(viewModel = viewModel)
                BpsNavTab.HISTORY -> HistoryScreen(viewModel = viewModel)
            }
        }
    }
}
