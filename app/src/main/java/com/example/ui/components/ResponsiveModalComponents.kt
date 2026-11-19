package com.example.ui.components

import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight

/**
 * Ensures Android Dialog windows use edge-to-edge full-screen coordinates so Jetpack Compose
 * can apply WindowInsets.safeDrawing (status bar, display cutout, nav bar, and IME keyboard)
 * without bottom buttons getting obscured or clipped.
 */
@Composable
fun ConfigureResponsiveDialogWindow() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

data class ResponsiveModalMetrics(
    val isCompactHeight: Boolean,
    val isCompactWidth: Boolean,
    val isUltraNarrow: Boolean,
    val contentPadding: Dp,
    val availableHeight: Dp,
    val availableWidth: Dp
)

/**
 * Shared responsive modal shell for BPS Facilities dialogs:
 * - Respects Android system bars, display cutout, and IME virtual keyboard (WindowInsets.safeDrawing)
 * - Caps width on tablets while adapting cleanly down to 320dp phones
 * - Supports fixed header + independent scrollable body + fixed footer
 */
@Composable
fun BpsResponsiveDialog(
    onDismissRequest: () -> Unit,
    dialogTestTag: String,
    dialogMaxWidth: Dp = 640.dp,
    fillMaxHeight: Boolean = false,
    content: @Composable (ResponsiveModalMetrics) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
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

            val metrics = ResponsiveModalMetrics(
                isCompactHeight = isCompactHeight,
                isCompactWidth = isCompactWidth,
                isUltraNarrow = isUltraNarrow,
                contentPadding = contentPadding,
                availableHeight = availableModalHeight,
                availableWidth = (this.maxWidth - outerHorizontalPad * 2).coerceAtMost(dialogMaxWidth)
            )

            Surface(
                modifier = Modifier
                    .padding(horizontal = outerHorizontalPad, vertical = outerVerticalPad)
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth()
                    .then(
                        if (fillMaxHeight) {
                            Modifier.heightIn(min = availableModalHeight.coerceAtMost(320.dp), max = availableModalHeight)
                        } else {
                            Modifier.heightIn(max = availableModalHeight)
                        }
                    )
                    .testTag(dialogTestTag),
                shape = RoundedCornerShape(18.dp),
                color = BpsDarkBg,
                border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.55f))
            ) {
                content(metrics)
            }
        }
    }
}

@Composable
fun BpsResponsiveModalScaffold(
    title: String,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    headerTint: Color = BpsGold,
    onDismiss: () -> Unit,
    closeButtonTestTag: String = "close_modal_btn",
    headerTestTag: String? = null,
    footerTestTag: String? = null,
    metrics: ResponsiveModalMetrics,
    scrollableBody: Boolean = true,
    footerContent: (@Composable () -> Unit)? = null,
    bodyContent: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Fixed Header
        Surface(
            color = Color(0xFF070B14),
            modifier = if (headerTestTag != null) Modifier.testTag(headerTestTag) else Modifier
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = metrics.contentPadding,
                            vertical = if (metrics.isCompactHeight) 8.dp else 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (headerIcon != null) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = headerTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (metrics.isCompactWidth) 15.sp else 16.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    fontSize = 11.sp,
                                    color = BpsGoldLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(closeButtonTestTag)
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

        // 2. Scrollable Body
        val bodyModifier = if (scrollableBody) {
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(metrics.contentPadding)
        } else {
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
        }

        Column(
            modifier = bodyModifier,
            verticalArrangement = if (scrollableBody) {
                Arrangement.spacedBy(if (metrics.isCompactHeight) 8.dp else 12.dp)
            } else {
                Arrangement.Top
            }
        ) {
            bodyContent()
        }

        // 3. Fixed Footer
        if (footerContent != null) {
            Surface(
                color = Color(0xFF070B14),
                modifier = if (footerTestTag != null) Modifier.testTag(footerTestTag) else Modifier
            ) {
                Column {
                    HorizontalDivider(color = BpsGold.copy(alpha = 0.25f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = metrics.contentPadding,
                                vertical = if (metrics.isCompactHeight) 8.dp else 10.dp
                            )
                    ) {
                        footerContent()
                    }
                }
            }
        }
    }
}
