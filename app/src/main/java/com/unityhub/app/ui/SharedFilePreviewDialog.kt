package com.unityhub.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.unityhub.app.data.ChatSharedFileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

@Composable
fun SharedFilePreviewDialog(
    file: ChatSharedFileDto,
    signedUrl: String,
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                        Text(
                            file.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenExternally) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open in another app")
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (file.mimeType.substringBefore(';').trim().lowercase()) {
                        "image/jpeg", "image/png", "image/webp" -> AsyncImage(
                            model = signedUrl,
                            contentDescription = file.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        "application/pdf" -> PdfPreview(
                            fileId = file.id,
                            signedUrl = signedUrl,
                            onOpenExternally = onOpenExternally
                        )
                        "text/plain", "text/csv" -> TextPreview(
                            signedUrl = signedUrl,
                            onOpenExternally = onOpenExternally
                        )
                        else -> PreviewFallback(onOpenExternally)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreview(fileId: String, signedUrl: String, onOpenExternally: () -> Unit) {
    val context = LocalContext.current
    var pageIndex by remember(signedUrl) { mutableIntStateOf(0) }
    var page by remember(signedUrl) { mutableStateOf<PdfPage?>(null) }
    var loading by remember(signedUrl) { mutableStateOf(true) }
    var error by remember(signedUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(signedUrl, pageIndex) {
        page?.bitmap?.recycle()
        page = null
        loading = true
        error = null
        try {
            page = withContext(Dispatchers.IO) {
                renderPdfPage(context, fileId, signedUrl, pageIndex)
            }
        } catch (exception: Exception) {
            error = exception.message ?: "PDF preview unavailable."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        val current = page
        if (current != null) {
            Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageIndex > 0
                ) { Text("Previous") }
                Text("Page ${pageIndex + 1} of ${current.pageCount}")
                TextButton(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(current.pageCount - 1) },
                    enabled = pageIndex < current.pageCount - 1
                ) { Text("Next") }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator()
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "PDF preview unavailable.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onOpenExternally) { Text("Open in another app") }
                }
            }
        }
    }
}

@Composable
private fun TextPreview(signedUrl: String, onOpenExternally: () -> Unit) {
    var text by remember(signedUrl) { mutableStateOf<String?>(null) }
    var error by remember(signedUrl) { mutableStateOf<String?>(null) }
    var loading by remember(signedUrl) { mutableStateOf(true) }

    LaunchedEffect(signedUrl) {
        try {
            text = withContext(Dispatchers.IO) { loadTextPreview(signedUrl) }
        } catch (exception: Exception) {
            error = exception.message ?: "Text preview unavailable."
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        text != null -> SelectionContainer {
            Text(
                text = text.orEmpty(),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        else -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(error ?: "Text preview unavailable.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenExternally) { Text("Open in another app") }
        }
    }
}

@Composable
private fun PreviewFallback(onOpenExternally: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Preview is not available for this file type.")
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = onOpenExternally) { Text("Open in another app") }
    }
}

private data class PdfPage(val bitmap: Bitmap, val pageCount: Int)

private fun renderPdfPage(
    context: Context,
    fileId: String,
    signedUrl: String,
    requestedPage: Int
): PdfPage {
    val safeId = fileId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val destination = File(context.cacheDir, "shared-file-${safeId}.pdf")
    if (!destination.exists() || destination.length() == 0L) {
        val temporary = File(context.cacheDir, "shared-file-${safeId}.tmp")
        val connection = URL(signedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 25 * 1024 * 1024) { "PDF exceeds the preview size limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(renderer.pageCount > 0) { "This PDF has no pages." }
            val index = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { pdfPage ->
                val scale = min(1f, 1500f / max(pdfPage.width, pdfPage.height))
                val bitmap = Bitmap.createBitmap(
                    max(1, (pdfPage.width * scale).toInt()),
                    max(1, (pdfPage.height * scale).toInt()),
                    Bitmap.Config.ARGB_8888
                )
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfPage(bitmap, renderer.pageCount)
            }
        }
    }
}

private fun loadTextPreview(signedUrl: String): String {
    val connection = URL(signedUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 20_000
    connection.instanceFollowRedirects = true
    try {
        val output = StringBuilder()
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (output.length < 40_000) {
                val count = reader.read(buffer, 0, min(buffer.size, 40_000 - output.length))
                if (count < 0) break
                output.append(buffer, 0, count)
            }
        }
        return if (output.length == 40_000) {
            output.toString() + "\n\nPreview shortened. Open the file in another app to view the complete content."
        } else output.toString()
    } finally {
        connection.disconnect()
    }
}
