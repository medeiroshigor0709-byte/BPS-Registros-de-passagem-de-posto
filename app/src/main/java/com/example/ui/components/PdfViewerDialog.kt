package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.BpsDarkSurface
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.StatusError
import com.example.util.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewerDialog(
    pdfFile: File,
    title: String = pdfFile.name,
    onDismiss: () -> Unit,
    onSaveClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var renderedPages by remember(pdfFile.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember(pdfFile.absolutePath) { mutableStateOf(true) }
    var errorMessage by remember(pdfFile.absolutePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfFile.absolutePath) {
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (!pdfFile.exists() || pdfFile.length() == 0L) {
                    throw IllegalStateException("Arquivo PDF não encontrado ou vazio.")
                }
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val bitmaps = mutableListOf<Bitmap>()
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount == 0) {
                            throw IllegalStateException("O documento PDF não possui páginas.")
                        }
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val targetWidth = 1080
                                val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                                val targetHeight = (targetWidth * ratio).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bmp)
                            }
                        }
                    }
                }
                bitmaps
            }
        }
        result.onSuccess { pages ->
            renderedPages = pages
            isLoading = false
        }.onFailure { err ->
            errorMessage = err.message ?: "Não foi possível renderizar o documento PDF."
            isLoading = false
        }
    }

    DisposableEffect(pdfFile.absolutePath) {
        onDispose {
            renderedPages.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .testTag("pdf_viewer_dialog"),
            shape = RoundedCornerShape(18.dp),
            color = BpsDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, BpsGold.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = BpsGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isLoading) "Carregando páginas..."
                                else if (errorMessage != null) "Erro ao carregar"
                                else "${renderedPages.size} página(s) • Visualização offline",
                                color = BpsGoldLight,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_pdf_viewer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar visualizador",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = BpsGold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Renderizando PDF...",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        errorMessage != null -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { PdfReportGenerator.viewPdf(context, pdfFile) }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir em Leitor Externo")
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(renderedPages) { index, bitmap ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BpsDarkSurface,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            Text(
                                                text = "Página ${index + 1} de ${renderedPages.size}",
                                                color = BpsGoldLight,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        val aspect = (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                                            .coerceIn(0.5f, 2.0f)
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Página ${index + 1} do PDF",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(aspect)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer actions
                Surface(color = Color(0xFF070B14)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onSaveClick != null) {
                            Button(
                                onClick = onSaveClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BpsGold,
                                    contentColor = Color(0xFF1E1400)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onShareClick?.invoke() ?: PdfReportGenerator.sharePdf(context, pdfFile)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = BpsGold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enviar PDF", fontSize = 13.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = { PdfReportGenerator.viewPdf(context, pdfFile) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Abrir em aplicativo externo",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}
