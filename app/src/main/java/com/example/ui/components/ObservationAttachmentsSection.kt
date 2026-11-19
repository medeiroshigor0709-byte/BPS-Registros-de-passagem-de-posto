package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AttachmentType
import com.example.data.model.ShiftAttachment
import com.example.ui.theme.BpsGold
import com.example.ui.theme.BpsGoldLight
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOk
import com.example.ui.viewmodel.AttachmentFeedback
import com.example.util.AttachmentStorageManager
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObservationAttachmentsSection(
    attachments: List<ShiftAttachment>,
    onAddPhotos: (List<Uri>) -> Unit,
    onAddPdfs: (List<Uri>) -> Unit,
    onRemoveAttachment: ((ShiftAttachment) -> Unit)?,
    feedback: AttachmentFeedback? = null,
    onDismissFeedback: (() -> Unit)? = null,
    readOnly: Boolean = false,
    testTagPrefix: String = "obs",
    modifier: Modifier = Modifier
) {
    var previewPhoto by remember { mutableStateOf<ShiftAttachment?>(null) }
    var previewPdf by remember { mutableStateOf<ShiftAttachment?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddPhotos(uris)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddPdfs(uris)
        }
    }

    val photos = remember(attachments) { attachments.filter { it.type == AttachmentType.PHOTO } }
    val pdfs = remember(attachments) { attachments.filter { it.type == AttachmentType.PDF } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_attachments_section")
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "📎 Anexos da Observação (${attachments.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BpsGoldLight
            )
            if (attachments.isNotEmpty()) {
                Text(
                    text = "${photos.size} foto(s) • ${pdfs.size} PDF(s)",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        if (!readOnly) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_add_photo_btn"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, BpsGold.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = BpsGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Adicionar foto",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BpsGoldLight,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_add_pdf_btn"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Adicionar PDF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (feedback != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (feedback.isError) Color(0xFF3F171B) else Color(0xFF0E291A),
                border = BorderStroke(1.dp, if (feedback.isError) StatusError else StatusOk),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismissFeedback?.invoke() }
            ) {
                Text(
                    text = feedback.message,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        // Photo Thumbnails
        if (photos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Fotografias anexadas (toque para ampliar):",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(photos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, BpsGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { previewPhoto = photo }
                            .testTag("${testTagPrefix}_photo_thumb_${photo.id}")
                    ) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = "Miniatura: ${photo.displayName}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (!readOnly && onRemoveAttachment != null) {
                            Surface(
                                onClick = { onRemoveAttachment(photo) },
                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                color = Color(0xCC0F172A),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .testTag("${testTagPrefix}_remove_attachment_${photo.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remover foto ${photo.displayName}",
                                        tint = StatusError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // PDF Documents List
        if (pdfs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Documentos PDF anexados:",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pdfs.forEach { pdf ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF162033),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { previewPdf = pdf }
                            .testTag("${testTagPrefix}_pdf_item_${pdf.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF3F171B),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pdf.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${AttachmentStorageManager.formatFileSize(pdf.sizeBytes)} • Toque para abrir",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            IconButton(
                                onClick = { previewPdf = pdf },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Visualizar PDF ${pdf.displayName}",
                                    tint = BpsGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (!readOnly && onRemoveAttachment != null) {
                                IconButton(
                                    onClick = { onRemoveAttachment(pdf) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("${testTagPrefix}_remove_attachment_${pdf.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remover PDF ${pdf.displayName}",
                                        tint = StatusError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Photo Preview Dialog (Responsive edge-to-edge)
    previewPhoto?.let { photo ->
        BpsResponsiveDialog(
            onDismissRequest = { previewPhoto = null },
            dialogTestTag = "${testTagPrefix}_photo_preview_dialog",
            dialogMaxWidth = 560.dp
        ) { metrics ->
            val previewImageMaxHeight = (metrics.availableHeight * 0.55f).coerceAtLeast(160.dp)
            BpsResponsiveModalScaffold(
                title = photo.displayName,
                subtitle = "Armazenamento persistente • ${AttachmentStorageManager.formatFileSize(photo.sizeBytes)}",
                headerIcon = Icons.Default.Image,
                onDismiss = { previewPhoto = null },
                closeButtonTestTag = "${testTagPrefix}_close_photo_preview_btn",
                metrics = metrics,
                footerContent = {
                    Button(
                        onClick = { previewPhoto = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BpsGold),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Fechar", color = Color(0xFF1E1400), fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = previewImageMaxHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = photo.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    // PDF Viewer Dialog
    previewPdf?.let { pdf ->
        PdfViewerDialog(
            pdfFile = File(pdf.filePath),
            title = pdf.displayName,
            onDismiss = { previewPdf = null }
        )
    }
}
