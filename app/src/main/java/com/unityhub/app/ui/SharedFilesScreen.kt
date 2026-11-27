package com.unityhub.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.ChatFileRepository
import com.unityhub.app.data.ChatSharedFileDto
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFilesScreen(
    organizationId: String,
    userId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileRepository = remember { ChatFileRepository(SupabaseClientProvider.client) }
    val memberRepository = remember { ServiceAssignmentRepository(SupabaseClientProvider.client) }

    var files by remember(organizationId) { mutableStateOf<List<ChatSharedFileDto>>(emptyList()) }
    var membersByUserId by remember(organizationId) {
        mutableStateOf<Map<String, ServiceMemberOption>>(emptyMap())
    }
    var loading by remember(organizationId) { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    fun refresh(showIndicator: Boolean = false) {
        scope.launch {
            refreshing = showIndicator
            loading = true
            errorMessage = ""
            try {
                val loadedFiles = fileRepository.loadSharedFiles(organizationId)
                val members = memberRepository.loadTeamMembers(organizationId)
                files = loadedFiles
                membersByUserId = members.associateBy { it.userId }
            } catch (error: Exception) {
                errorMessage = error.message ?: "Shared files could not be loaded. Try again."
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun upload(uri: Uri) {
        if (uploading) return
        scope.launch {
            uploading = true
            errorMessage = ""
            try {
                val picked = withContext(Dispatchers.IO) {
                    readPickedSharedFile(context, uri)
                }
                fileRepository.uploadSharedLibraryFile(
                    organizationId = organizationId,
                    uploaderId = userId,
                    fileName = picked.fileName,
                    mimeType = picked.mimeType,
                    bytes = picked.bytes
                )
                files = fileRepository.loadSharedFiles(organizationId)
            } catch (error: Exception) {
                errorMessage = error.message ?: "The file could not be uploaded. Try again."
            } finally {
                uploading = false
            }
        }
    }

    fun openFile(file: ChatSharedFileDto) {
        scope.launch {
            runCatching { fileRepository.createSignedUrl(file.storagePath) }
                .onSuccess { signedUrl ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(signedUrl), file.mimeType.ifBlank { "*/*" })
                        }
                        context.startActivity(intent)
                    }.onFailure { error ->
                        errorMessage = error.message ?: "No compatible app could open this file."
                    }
                }
                .onFailure { error ->
                    errorMessage = error.message ?: "This file is not available to your account."
                }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) upload(uri)
    }

    LaunchedEffect(organizationId) {
        refresh()
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh(showIndicator = true) },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "Team files",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Shared documents and media for this organization",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !uploading
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(if (uploading) "Uploading" else "Upload")
                    }
                }
            }

            if (errorMessage.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text("Try again")
                        }
                    }
                }
            }

            if (loading && files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!loading && files.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No shared files yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Upload a document, song reference, rehearsal guide, or photo for your team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(files, key = { it.id }) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { openFile(file) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(25.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    file.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Text(
                                    "${formatSharedFileSize(file.fileSize)} · ${membersByUserId[file.uploaderId]?.displayName ?: "Team member"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Open ${file.fileName}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PickedSharedFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

private fun readPickedSharedFile(context: Context, uri: Uri): PickedSharedFile {
    val fileName = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "attachment"

    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val detectedMime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
    val mimeType = detectedMime
        ?.takeIf { it != "application/octet-stream" }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"

    require(ChatFileRepository.isSupportedMimeType(mimeType)) {
        "This file type is not supported. Choose an image, PDF, Office document, text file, or audio recording."
    }

    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= ChatFileRepository.MAX_FILE_SIZE_BYTES) {
                "Files must be 25 MB or smaller."
            }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: error("The selected file could not be opened.")

    require(bytes.isNotEmpty()) { "The selected file is empty." }
    return PickedSharedFile(fileName, mimeType, bytes)
}

private fun formatSharedFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes} B"
}
