package com.unityhub.app.ui

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unityhub.app.data.AccountRepository
import com.unityhub.app.data.ChatConversationSummary
import com.unityhub.app.data.ChatFileRepository
import com.unityhub.app.data.ChatMessageAttachment
import com.unityhub.app.data.ChatSharedFileDto
import com.unityhub.app.data.NotificationRules
import com.unityhub.app.data.NotificationsRepository
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.TeamChatMessageDto
import com.unityhub.app.data.TeamChatReadStateDto
import com.unityhub.app.data.TeamChatReactionDto
import com.unityhub.app.data.TeamChatRealtimeEvent
import com.unityhub.app.data.TeamChatRepository
import com.unityhub.app.data.TeamChatRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TeamChatScreen(
    organizationId: String,
    organizationName: String,
    userId: String,
    openConversationId: String? = null,
    openMessageId: String? = null,
    openSharedFiles: Boolean = false,
    onOpenMemberProfile: (ServiceMemberOption) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val client = remember { SupabaseClientProvider.client }
    val repository = remember { TeamChatRepository(client) }
    val memberRepository = remember { ServiceAssignmentRepository(client) }
    val notificationsRepository = remember { NotificationsRepository(client) }
    val fileRepository = remember { ChatFileRepository(client) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var conversations by remember(organizationId) { mutableStateOf<List<ChatConversationSummary>>(emptyList()) }
    var unreadByConversation by remember(organizationId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var activeConversationId by remember(organizationId) { mutableStateOf<String?>(null) }
    var showConversationList by rememberSaveable(organizationId) { mutableStateOf(true) }
    var showNewConversationDialog by rememberSaveable(organizationId) { mutableStateOf(false) }
    var showConversationDetails by rememberSaveable(organizationId) { mutableStateOf(false) }
    var showAddGroupMemberDialog by rememberSaveable(organizationId) { mutableStateOf(false) }
    var pendingGroupRemoval by remember { mutableStateOf<ServiceMemberOption?>(null) }
    var groupTitleDraft by remember(activeConversationId) { mutableStateOf("") }
    var showSharedLibrary by rememberSaveable(organizationId) { mutableStateOf(false) }
    var showAttachmentOptions by rememberSaveable(organizationId) { mutableStateOf(false) }
    var shareAttachmentWithTeam by rememberSaveable(organizationId) { mutableStateOf(false) }
    var nextPickIsLibrary by remember { mutableStateOf(false) }
    var sharedFiles by remember(organizationId) { mutableStateOf<List<ChatSharedFileDto>>(emptyList()) }
    var loadingSharedFiles by remember { mutableStateOf(false) }
    var uploadingAttachment by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var voicePreviewReady by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var voicePreviewDurationMs by remember { mutableStateOf(0) }
    var isVoicePreviewPlaying by remember { mutableStateOf(false) }
    var recordingDragDistance by remember { mutableStateOf(0f) }
    var voicePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeVoiceMessageId by remember { mutableStateOf<String?>(null) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var isVoicePreparing by remember { mutableStateOf(false) }
    var voiceDurationMs by remember { mutableStateOf(0) }
    var voicePositionMs by remember { mutableStateOf(0) }
    var messages by remember(organizationId) { mutableStateOf<List<TeamChatMessageDto>>(emptyList()) }
    var readStates by remember(organizationId) { mutableStateOf<List<TeamChatReadStateDto>>(emptyList()) }
    var reactions by remember(organizationId) { mutableStateOf<List<TeamChatReactionDto>>(emptyList()) }
    var replyToMessageId by remember(organizationId) { mutableStateOf<String?>(null) }
    var editingMessageId by remember(organizationId) { mutableStateOf<String?>(null) }
    var membersByUserId by remember(organizationId) { mutableStateOf<Map<String, ServiceMemberOption>>(emptyMap()) }
    var draft by remember(organizationId) { mutableStateOf("") }
    var loading by remember(organizationId) { mutableStateOf(true) }
    var loadedConversationId by remember(organizationId) { mutableStateOf<String?>(null) }
    var loadingOlder by remember(organizationId) { mutableStateOf(false) }
    var hasMoreOlder by remember(organizationId) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasPositionedChatList by remember(organizationId) { mutableStateOf(false) }
    var pendingAnchorMessageId by remember { mutableStateOf<String?>(null) }
    var pendingAnchorOffset by remember { mutableStateOf(0) }
    var lastMarkedMessageId by remember(organizationId) { mutableStateOf<String?>(null) }

    fun refreshConversationUnreadCounts() {
        scope.launch {
            runCatching { notificationsRepository.loadNotifications(userId) }
                .onSuccess { notifications ->
                    unreadByConversation = NotificationRules.unreadByConversation(
                        notifications,
                        organizationId
                    )
                }
        }
    }

    fun reloadSharedFiles() {
        scope.launch {
            loadingSharedFiles = true
            runCatching { fileRepository.loadSharedFiles(organizationId) }
                .onSuccess { sharedFiles = it }
                .onFailure { errorMessage = it.message ?: "Shared files could not be loaded." }
            loadingSharedFiles = false
        }
    }

    fun playVoiceMessage(path: String, messageId: String) {
        if (activeVoiceMessageId == messageId && voicePlayer != null) {
            val player = voicePlayer
            if (player?.isPlaying == true) {
                player.pause()
                isVoicePlaying = false
            } else {
                runCatching { player?.start() }
                isVoicePlaying = true
            }
            return
        }
        scope.launch {
            runCatching {
                voicePlayer?.release()
                voicePlayer = null
                isVoicePlaying = false
                isVoicePreparing = true
                activeVoiceMessageId = messageId
                val signedUrl = fileRepository.createSignedUrl(path)
                val player = MediaPlayer()
                voicePlayer = player
                player.setOnPreparedListener {
                    voiceDurationMs = it.duration.coerceAtLeast(0)
                    voicePositionMs = 0
                    isVoicePreparing = false
                    it.start()
                    isVoicePlaying = true
                }
                player.setOnCompletionListener {
                    isVoicePlaying = false
                    isVoicePreparing = false
                    activeVoiceMessageId = null
                    voicePlayer?.release()
                    voicePlayer = null
                }
                player.setOnErrorListener { mediaPlayer, _, _ ->
                    isVoicePlaying = false
                    isVoicePreparing = false
                    activeVoiceMessageId = null
                    runCatching { mediaPlayer.release() }
                    voicePlayer = null
                    errorMessage = "This voice message could not be played."
                    true
                }
                player.setDataSource(signedUrl)
                player.prepareAsync()
            }.onFailure { error ->
                voicePlayer?.release()
                voicePlayer = null
                activeVoiceMessageId = null
                isVoicePlaying = false
                isVoicePreparing = false
                errorMessage = error.message ?: "This voice message could not be loaded."
            }
        }
    }

    fun openStorageFile(path: String, mimeType: String) {
        scope.launch {
            runCatching { fileRepository.createSignedUrl(path) }
                .onSuccess { signedUrl ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(signedUrl), mimeType.ifBlank { "*/*" })
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

    fun uploadPickedFile(uri: Uri, toLibrary: Boolean) {
        if (uploadingAttachment) return
        val conversationId = activeConversationId
        if (!toLibrary && conversationId.isNullOrBlank()) {
            errorMessage = "Open a conversation before attaching a file."
            return
        }
        scope.launch {
            uploadingAttachment = true
            sending = !toLibrary
            errorMessage = ""
            try {
                val picked = withContext(Dispatchers.IO) { readPickedChatFile(context, uri) }
                if (toLibrary) {
                    fileRepository.uploadSharedLibraryFile(
                        organizationId = organizationId,
                        uploaderId = userId,
                        fileName = picked.fileName,
                        mimeType = picked.mimeType,
                        bytes = picked.bytes
                    )
                    sharedFiles = fileRepository.loadSharedFiles(organizationId)
                    showSharedLibrary = true
                    showConversationList = false
                } else {
                    val file = fileRepository.uploadChatAttachment(
                        organizationId = organizationId,
                        conversationId = conversationId!!,
                        uploaderId = userId,
                        fileName = picked.fileName,
                        mimeType = picked.mimeType,
                        bytes = picked.bytes,
                        sharedToTeam = shareAttachmentWithTeam
                    )
                    repository.sendMessage(
                        organizationId = organizationId,
                        conversationId = conversationId,
                        senderId = userId,
                        content = draft.trim().ifBlank { "Shared an attachment" },
                        replyToMessageId = replyToMessageId,
                        attachment = ChatMessageAttachment(
                            id = file.id,
                            storagePath = file.storagePath,
                            fileName = file.fileName,
                            mimeType = file.mimeType,
                            fileSize = file.fileSize
                        )
                    )
                    draft = ""
                    replyToMessageId = null
                    shareAttachmentWithTeam = false
                    runCatching { repository.loadLatestMessages(organizationId, conversationId) }
                        .onSuccess { page ->
                            messages = TeamChatRules.mergeMessages(messages, page.messages)
                        }
                    if (file.sharedToTeam) {
                        sharedFiles = runCatching { fileRepository.loadSharedFiles(organizationId) }
                            .getOrDefault(sharedFiles)
                    }
                }
            } catch (error: Exception) {
                errorMessage = error.message ?: "The file could not be uploaded. Try again."
            } finally {
                uploadingAttachment = false
                sending = false
            }
        }
    }

    @Suppress("DEPRECATION")
    fun beginVoiceRecording() {
        if (activeConversationId.isNullOrBlank()) {
            errorMessage = "Open a conversation before recording a voice message."
            return
        }
        if (isRecordingVoice || uploadingAttachment || sending) return
        val output = File(context.cacheDir, "unityhub_voice_${System.currentTimeMillis()}.m4a")
        var createdRecorder: MediaRecorder? = null
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            createdRecorder = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            voiceRecorder = recorder
            recordingFile = output
            isRecordingVoice = true
            voicePreviewReady = false
            recordingStartedAt = SystemClock.elapsedRealtime()
            recordingElapsedMs = 0L
            recordingDragDistance = 0f
            errorMessage = ""
        } catch (error: Exception) {
            runCatching { createdRecorder?.release() }
            voiceRecorder = null
            output.delete()
            recordingFile = null
            isRecordingVoice = false
            errorMessage = error.message ?: "The microphone could not start. Check microphone access and try again."
        }
    }

    fun discardVoiceRecording() {
        runCatching { voiceRecorder?.stop() }
        runCatching { voiceRecorder?.release() }
        runCatching { voicePlayer?.release() }
        recordingFile?.delete()
        voiceRecorder = null
        voicePlayer = null
        recordingFile = null
        isRecordingVoice = false
        voicePreviewReady = false
        isVoicePreviewPlaying = false
        recordingElapsedMs = 0L
        voicePreviewDurationMs = 0
        recordingDragDistance = 0f
    }

    fun stopVoiceRecordingForPreview() {
        val recorder = voiceRecorder ?: return
        val output = recordingFile ?: return
        try {
            recorder.stop()
            runCatching { recorder.release() }
            voiceRecorder = null
            isRecordingVoice = false
            if (!output.exists() || output.length() <= 0L) {
                output.delete()
                recordingFile = null
                errorMessage = "The recording is empty. Please try again."
                return
            }
            voicePreviewReady = true
            runCatching {
                val preview = MediaPlayer()
                preview.setDataSource(output.absolutePath)
                preview.setOnPreparedListener { player ->
                    voicePreviewDurationMs = player.duration.coerceAtLeast(0)
                    player.release()
                    voicePlayer = null
                }
                preview.setOnErrorListener { player, _, _ ->
                    player.release()
                    voicePlayer = null
                    errorMessage = "The recording was saved, but its preview could not be prepared."
                    true
                }
                voicePlayer = preview
                preview.prepareAsync()
            }.onFailure {
                errorMessage = "The recording was saved, but its preview could not be prepared."
            }
        } catch (error: Exception) {
            runCatching { recorder.release() }
            output.delete()
            recordingFile = null
            voiceRecorder = null
            isRecordingVoice = false
            voicePreviewReady = false
            errorMessage = error.message ?: "The recording could not be saved. Please try again."
        }
    }

    fun toggleVoicePreview() {
        val output = recordingFile ?: return
        if (isVoicePreviewPlaying) {
            runCatching { voicePlayer?.pause() }
            isVoicePreviewPlaying = false
            return
        }
        runCatching { voicePlayer?.release() }
        val player = MediaPlayer()
        voicePlayer = player
        player.setOnPreparedListener {
            it.start()
            isVoicePreviewPlaying = true
        }
        player.setOnCompletionListener {
            isVoicePreviewPlaying = false
            runCatching { it.seekTo(0) }
        }
        player.setOnErrorListener { mediaPlayer, _, _ ->
            isVoicePreviewPlaying = false
            runCatching { mediaPlayer.release() }
            voicePlayer = null
            errorMessage = "The recording preview could not be played."
            true
        }
        runCatching {
            player.setDataSource(output.absolutePath)
            player.prepareAsync()
        }.onFailure {
            runCatching { player.release() }
            voicePlayer = null
            errorMessage = "The recording preview could not be played."
        }
    }

    fun sendVoicePreview() {
        val output = recordingFile ?: return
        val conversationId = activeConversationId ?: run {
            discardVoiceRecording()
            errorMessage = "Open a conversation before sending a voice message."
            return
        }
        runCatching { voicePlayer?.release() }
        voicePlayer = null
        isVoicePreviewPlaying = false
        voicePreviewReady = false
        recordingFile = null
        scope.launch {
            uploadingAttachment = true
            sending = true
            errorMessage = ""
            try {
                val bytes = withContext(Dispatchers.IO) { output.readBytes() }
                val fileName = "voice_message_${System.currentTimeMillis()}.m4a"
                val uploaded = fileRepository.uploadChatAttachment(
                    organizationId = organizationId,
                    conversationId = conversationId,
                    uploaderId = userId,
                    fileName = fileName,
                    mimeType = "audio/mp4",
                    bytes = bytes,
                    sharedToTeam = shareAttachmentWithTeam
                )
                repository.sendMessage(
                    organizationId = organizationId,
                    conversationId = conversationId,
                    senderId = userId,
                    content = draft.trim().ifBlank { "Voice message" },
                    replyToMessageId = replyToMessageId,
                    attachment = ChatMessageAttachment(
                        id = uploaded.id,
                        storagePath = uploaded.storagePath,
                        fileName = uploaded.fileName,
                        mimeType = uploaded.mimeType,
                        fileSize = uploaded.fileSize
                    )
                )
                draft = ""
                replyToMessageId = null
                shareAttachmentWithTeam = false
                recordingElapsedMs = 0L
                voicePreviewDurationMs = 0
                runCatching { repository.loadLatestMessages(organizationId, conversationId) }
                    .onSuccess { page -> messages = TeamChatRules.mergeMessages(messages, page.messages) }
            } catch (error: Exception) {
                errorMessage = error.message ?: "The voice message could not be sent. Try again."
                recordingFile = output
                voicePreviewReady = true
            } finally {
                output.delete()
                if (!voicePreviewReady) recordingFile = null
                uploadingAttachment = false
                sending = false
            }
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginVoiceRecording()
        else errorMessage = "Microphone permission is needed to record a voice message."
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) uploadPickedFile(uri, toLibrary = nextPickIsLibrary)
        nextPickIsLibrary = false
    }
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) uploadPickedFile(uri, toLibrary = nextPickIsLibrary)
        nextPickIsLibrary = false
    }

    fun reload() {
        val conversationId = activeConversationId ?: return
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                val hadLoadedMessages = messages.isNotEmpty()
                val page = repository.loadLatestMessages(organizationId, conversationId)
                messages = TeamChatRules.mergeMessages(messages, page.messages)
                if (!hadLoadedMessages) hasMoreOlder = page.hasMore
                readStates = repository.loadReadStates(organizationId, conversationId)
                reactions = repository.loadReactions(organizationId)
                membersByUserId = memberRepository.loadTeamMembers(organizationId)
                    .associateBy { it.userId }
            } catch (_: Exception) {
                errorMessage = "This conversation could not load. Check your connection and membership, then try again."
            } finally {
                loadedConversationId = conversationId
                loading = false
            }
        }
    }

    fun loadOlderMessages() {
        val oldest = messages.firstOrNull() ?: return
        if (loadingOlder || !hasMoreOlder) return

        val visibleMessage = listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
            val id = item.key as? String
            messages.firstOrNull { it.id == id }?.let { item to it }
        }
        pendingAnchorMessageId = visibleMessage?.second?.id
        pendingAnchorOffset = visibleMessage?.first?.let { item ->
            if (item.index == listState.firstVisibleItemIndex) {
                listState.firstVisibleItemScrollOffset
            } else {
                -item.offset
            }
        } ?: 0

        scope.launch {
            loadingOlder = true
            errorMessage = ""
            try {
                val conversationId = activeConversationId ?: return@launch
                val page = repository.loadOlderMessages(organizationId, conversationId, oldest)
                messages = TeamChatRules.mergeMessages(page.messages, messages)
                hasMoreOlder = page.hasMore
            } catch (_: Exception) {
                pendingAnchorMessageId = null
                errorMessage = "Older messages could not be loaded. Check your connection and try again."
            } finally {
                loadingOlder = false
            }
        }
    }

    fun sendDraft() {
        val content = draft.trim()
        if (
            content.isEmpty() ||
            content.length > TeamChatRules.MAX_MESSAGE_LENGTH ||
            sending
        ) return

        val conversationId = activeConversationId ?: return
        scope.launch {
            sending = true
            errorMessage = ""
            try {
                val activeEditId = editingMessageId
                if (activeEditId != null) {
                    repository.editMessage(
                        organizationId = organizationId,
                        messageId = activeEditId,
                        senderId = userId,
                        content = content
                    )
                } else {
                    repository.sendMessage(
                        organizationId = organizationId,
                        conversationId = conversationId,
                        senderId = userId,
                        content = content,
                        replyToMessageId = replyToMessageId
                    )
                }
                draft = ""
                editingMessageId = null
                replyToMessageId = null
                // Realtime supplies the inserted message. Refresh is only a
                // fallback and must not turn a successful send into an error.
                runCatching {
                    repository.loadLatestMessages(organizationId, conversationId)
                }.onSuccess { page ->
                    messages = TeamChatRules.mergeMessages(messages, page.messages)
                }
            } catch (_: Exception) {
                errorMessage = "Your message could not be sent. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }

    LaunchedEffect(openSharedFiles) {
        if (openSharedFiles) {
            showSharedLibrary = true
            showConversationList = false
            reloadSharedFiles()
        }
    }

    LaunchedEffect(organizationId, userId) {
        try {
            notificationsRepository.observeNotifications(userId).collect { incoming ->
                unreadByConversation = NotificationRules.unreadByConversation(
                    incoming,
                    organizationId
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            refreshConversationUnreadCounts()
        }
    }

    LaunchedEffect(organizationId, userId) {
        try {
            val loadedMembers = memberRepository.loadTeamMembers(organizationId)
            membersByUserId = loadedMembers.associateBy { it.userId }
            val loadedConversations = repository.loadConversations(organizationId)
            conversations = loadedConversations
            refreshConversationUnreadCounts()
            val defaultConversationId = loadedConversations.firstOrNull {
                it.conversation.conversationType == "organization"
            }?.conversation?.id ?: loadedConversations.firstOrNull()?.conversation?.id
            if (activeConversationId == null) activeConversationId = defaultConversationId
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            errorMessage = "Conversations could not load. Check your connection and try again."
        }
    }

    LaunchedEffect(openConversationId, conversations) {
        val requestedId = openConversationId ?: return@LaunchedEffect
        if (conversations.any { it.conversation.id == requestedId }) {
            activeConversationId = requestedId
            showConversationList = false
        }
    }

    LaunchedEffect(organizationId, openConversationId, openMessageId, activeConversationId) {
        val targetConversationId = openConversationId ?: return@LaunchedEffect
        val targetMessageId = openMessageId ?: return@LaunchedEffect
        // Do not mistake the previous conversation's messages for this destination.
        if (loadedConversationId != targetConversationId || loading) {
            snapshotFlow { loadedConversationId to loading }
                .first { (loadedId, isLoading) ->
                    loadedId == targetConversationId && !isLoading
                }
        }

        var pagesLoaded = 0
        while (messages.none { it.id == targetMessageId } && hasMoreOlder && pagesLoaded < 100) {
            val oldest = messages.firstOrNull() ?: break
            val page = try {
                repository.loadOlderMessages(organizationId, targetConversationId, oldest)
            } catch (_: Exception) {
                errorMessage = "The linked message could not be loaded. You can still browse this conversation."
                break
            }
            if (page.messages.isEmpty()) break
            messages = TeamChatRules.mergeMessages(page.messages, messages)
            hasMoreOlder = page.hasMore
            pagesLoaded++
        }

        if (messages.any { it.id == targetMessageId }) {
            pendingAnchorMessageId = targetMessageId
            pendingAnchorOffset = 0
            showConversationList = false
        } else {
            errorMessage = "The linked message is no longer available, but the conversation is open."
        }
    }

    LaunchedEffect(activeVoiceMessageId, isVoicePlaying) {
    LaunchedEffect(isRecordingVoice, recordingStartedAt) {
        while (isRecordingVoice) {
            recordingElapsedMs = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L)
            delay(200)
        }
    }

        while (activeVoiceMessageId != null && isVoicePlaying) {
            val player = voicePlayer ?: break
            voicePositionMs = runCatching { player.currentPosition }.getOrDefault(voicePositionMs)
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { voicePlayer?.release() }
            voicePlayer = null
            isVoicePlaying = false
        }
    }

    LaunchedEffect(organizationId, activeConversationId) {
        runCatching { voicePlayer?.release() }
        voicePlayer = null
        activeVoiceMessageId = null
        isVoicePlaying = false
        isVoicePreparing = false
        voiceDurationMs = 0
        voicePositionMs = 0
        if (isRecordingVoice || voicePreviewReady) discardVoiceRecording()
        val conversationId = activeConversationId ?: return@LaunchedEffect
        messages = emptyList()
        loadedConversationId = null
        readStates = emptyList()
        reactions = emptyList()
        draft = ""
        replyToMessageId = null
        editingMessageId = null
        hasMoreOlder = false
        hasPositionedChatList = false
        lastMarkedMessageId = null
        loading = true
        reload()
    }

    LaunchedEffect(organizationId, activeConversationId) {
        val conversationId = activeConversationId ?: return@LaunchedEffect
        try {
            repository.observeNewMessages(conversationId).collect { event ->
                when (event) {
                    TeamChatRealtimeEvent.Connected -> reload()
                    is TeamChatRealtimeEvent.MessageReceived -> {
                        messages = TeamChatRules.mergeMessages(messages, listOf(event.message))
                        loading = false
                        errorMessage = ""
                    }
                    is TeamChatRealtimeEvent.MessageUpdated -> {
                        messages = TeamChatRules.mergeMessages(messages, listOf(event.message))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (messages.isEmpty()) {
                errorMessage = "Live updates are unavailable. Loading messages once without the live connection."
            } else {
                errorMessage = "Live updates are temporarily unavailable. Your loaded messages remain available."
            }
            reload()
        }
    }

    LaunchedEffect(organizationId, activeConversationId) {
        val conversationId = activeConversationId ?: return@LaunchedEffect
        try {
            repository.observeReactions(organizationId).collect { incoming ->
                reactions = incoming
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { repository.loadReactions(organizationId) }
                .onSuccess { reactions = it }
        }
    }

    LaunchedEffect(organizationId, activeConversationId) {
        val conversationId = activeConversationId ?: return@LaunchedEffect
        try {
            repository.observeReadStates(organizationId, conversationId).collect { incoming ->
                readStates = incoming
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { repository.loadReadStates(organizationId, conversationId) }
                .onSuccess { readStates = it }
        }
    }

    LaunchedEffect(pendingAnchorMessageId, messages.size, hasMoreOlder) {
        val anchorId = pendingAnchorMessageId ?: return@LaunchedEffect
        val messageIndex = messages.indexOfFirst { it.id == anchorId }
        if (messageIndex >= 0) {
            listState.scrollToItem(
                index = messageIndex + if (hasMoreOlder) 1 else 0,
                scrollOffset = pendingAnchorOffset
            )
            pendingAnchorMessageId = null
            hasPositionedChatList = true
        }
    }

    LaunchedEffect(organizationId, activeConversationId, messages.size, messages.lastOrNull()?.id, hasMoreOlder) {
        if (messages.isEmpty() || pendingAnchorMessageId != null) return@LaunchedEffect

        val messageById = messages.withIndex().associate { it.value.id to it.index }
        val lastVisibleMessageIndex = listState.layoutInfo.visibleItemsInfo
            .mapNotNull { item -> (item.key as? String)?.let(messageById::get) }
            .maxOrNull() ?: -1
        if (
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = lastVisibleMessageIndex,
                totalItemCount = messages.size,
                hasPositionedList = hasPositionedChatList
            )
        ) {
            listState.animateScrollToItem(messages.lastIndex + if (hasMoreOlder) 1 else 0)
        }
        hasPositionedChatList = true
    }

    // The highest chronological message currently visible becomes the member's
    // read cursor. The database rejects backward cursor movement.
    LaunchedEffect(organizationId, activeConversationId, userId) {
        snapshotFlow {
            val messageById = messages.associateBy { it.id }
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { item -> (item.key as? String)?.let(messageById::get) }
                .maxWithOrNull(compareBy<TeamChatMessageDto> { it.createdAt }.thenBy { it.id })
                ?.id
        }.distinctUntilChanged().collect { visibleMessageId ->
            val visibleMessage = messages.firstOrNull { it.id == visibleMessageId } ?: return@collect
            if (visibleMessage.id == lastMarkedMessageId) return@collect

            val ownCursor = readStates.firstOrNull { it.userId == userId }
            val readThroughMessageId = if (
                ownCursor != null && TeamChatRules.hasReadMessage(ownCursor, visibleMessage)
            ) {
                // Use the persisted cursor itself when it is already ahead of
                // the visible row, so upgrade cleanup reaches every stale alert.
                ownCursor.lastReadMessageId
            } else {
                val readResult = runCatching {
                    repository.markAsRead(activeConversationId ?: return@collect, userId, visibleMessage.id)
                }
                if (readResult.isFailure) return@collect
                visibleMessage.id
            }

            // Also reconcile notifications when an existing cursor is already
            // ahead of this message; older app versions could leave alerts for
            // history that was outside their loaded page.
            lastMarkedMessageId = visibleMessage.id
            runCatching {
                notificationsRepository.markTeamChatNotificationsReadThrough(
                    organizationId = organizationId,
                    throughMessageId = readThroughMessageId,
                    userId = userId
                )
            }
            refreshConversationUnreadCounts()
        }
    }

    val normalizedDraft = draft.trim()
    val currentUserReadState = readStates.firstOrNull { it.userId == userId }
    val recentUnreadCount = TeamChatRules.unreadCount(messages, currentUserReadState, userId)
    val currentMemberIds = membersByUserId.keys

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val activeSummary = conversations.firstOrNull {
            it.conversation.id == activeConversationId
        }
        val activeConversation = activeSummary?.conversation
        val activeTitle = when (activeConversation?.conversationType) {
            "organization" -> organizationName
            "direct" -> activeSummary.members.firstOrNull { it.userId != userId }
                ?.let { membersByUserId[it.userId]?.displayName } ?: "Direct message"
            else -> activeConversation?.title ?: "Group chat"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = {
                when {
                    showSharedLibrary -> if (openSharedFiles) onBack() else showSharedLibrary = false
                    !showConversationList -> showConversationList = true
                    else -> onBack()
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        showSharedLibrary -> "Shared files"
                        showConversationList -> "Messages"
                        else -> activeTitle
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    when {
                        showSharedLibrary -> "Files shared with your organization"
                        showConversationList -> organizationName
                        else -> when (activeConversation?.conversationType) {
                            "organization" -> "Church-wide conversation"
                            "direct" -> "Private direct message"
                            else -> "${activeSummary?.members?.size ?: 0} group members"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!showSharedLibrary && !showConversationList && activeSummary != null) {
                IconButton(onClick = { showConversationDetails = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Conversation details")
                }
            }
            if (!showSharedLibrary && showConversationList) {
                IconButton(onClick = { showNewConversationDialog = true }, enabled = membersByUserId.isNotEmpty()) {
                    Icon(Icons.Default.Add, contentDescription = "New message or group")
                }
            }
        }

        if (!showSharedLibrary && recentUnreadCount > 0) {
            Text(
                "$recentUnreadCount unread messages in the loaded history",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (!showSharedLibrary) messages.lastOrNull { it.pinnedAt != null }?.let { pinnedMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Pinned", style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f)) {
                        Text(pinnedMessage.content, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (showSharedLibrary) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Team reference files", style = MaterialTheme.typography.titleSmall)
                    Button(
                        onClick = {
                            nextPickIsLibrary = true
                            documentPicker.launch(arrayOf("*/*"))
                        },
                        enabled = !uploadingAttachment
                    ) { Text(if (uploadingAttachment) "Uploading" else "Upload file") }
                }
                Text(
                    "Documents and photos saved for everyone in this organization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    loadingSharedFiles -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(26.dp))
                    }
                    sharedFiles.isEmpty() -> Card(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No shared files yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Upload a song reference, document, rehearsal guide, or photo for your team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sharedFiles, key = { it.id }) { file ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    openStorageFile(file.storagePath, file.mimeType)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(file.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                        Text(
                                            "${formatChatFileSize(file.fileSize)} · ${membersByUserId[file.uploaderId]?.displayName ?: "Team member"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text("Open", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        } else if (showConversationList) {
            if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Loading conversations", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversations, key = { it.conversation.id }) { entry ->
                        val conversation = entry.conversation
                        val title = when (conversation.conversationType) {
                            "organization" -> organizationName
                            "direct" -> entry.members.firstOrNull { it.userId != userId }
                                ?.let { membersByUserId[it.userId]?.displayName } ?: "Direct message"
                            else -> conversation.title ?: "Group chat"
                        }
                        val subtitle = when (conversation.conversationType) {
                            "organization" -> "Everyone in the organization"
                            "direct" -> "Direct message"
                            else -> entry.members.mapNotNull { membersByUserId[it.userId]?.displayName }
                                .take(4).joinToString(" · ")
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                activeConversationId = conversation.id
                                showConversationList = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (conversation.id == activeConversationId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val unreadCount = unreadByConversation[conversation.id] ?: 0
                                if (unreadCount > 0) {
                                    Text(
                                        if (unreadCount == 1) "1 unread message" else "$unreadCount unread messages",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (loading && messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Loading messages", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (messages.isEmpty()) {
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Start the conversation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Share an update, ask a question, or encourage your worship team.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasMoreOlder) {
                    item(key = "load-older-messages") {
                        TextButton(
                            onClick = { loadOlderMessages() },
                            enabled = !loadingOlder,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            if (loadingOlder) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                Text("Loading older messages")
                            } else {
                                Text("Load older messages")
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    val member = membersByUserId[message.senderId]
                    TeamChatMessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == userId,
                        senderName = if (message.senderId == userId) "You" else member?.displayName ?: "Team member",
                        avatarUrl = AccountRepository(client).publicMediaUrl(member?.avatarPath),
                        readByCount = TeamChatRules.readByCount(
                            message = message,
                            readStates = readStates,
                            currentMemberIds = currentMemberIds
                        ),
                        reactions = reactions.filter { it.messageId == message.id },
                        currentUserId = userId,
                        replyPreview = message.replyToMessageId?.let { replyId ->
                            messages.firstOrNull { it.id == replyId }?.let { parent ->
                                "${membersByUserId[parent.senderId]?.displayName ?: "Team member"}: ${parent.content}"
                            } ?: "Earlier message"
                        },
                        onOpenProfile = { member?.let(onOpenMemberProfile) },
                        onReply = {
                            editingMessageId = null
                            replyToMessageId = message.id
                        },
                        onEdit = {
                            editingMessageId = message.id
                            replyToMessageId = null
                            draft = message.content
                        },
                        onDelete = {
                            scope.launch {
                                val conversationId = activeConversationId ?: return@launch
                                runCatching {
                                    repository.deleteMessage(organizationId, message.id, userId)
                                    val page = repository.loadLatestMessages(organizationId, conversationId)
                                    messages = TeamChatRules.mergeMessages(messages, page.messages)
                                }.onFailure {
                                    errorMessage = "This message could not be deleted. Try again."
                                }
                            }
                        },
                        onPin = { shouldPin ->
                            scope.launch {
                                val conversationId = activeConversationId ?: return@launch
                                runCatching {
                                    repository.setPinned(organizationId, message.id, userId, shouldPin)
                                    val page = repository.loadLatestMessages(organizationId, conversationId)
                                    messages = TeamChatRules.mergeMessages(messages, page.messages)
                                }.onFailure { error ->
                                    errorMessage = error.message ?: "The pin change could not be saved."
                                }
                            }
                        },
                        onReact = { emoji ->
                            scope.launch {
                                runCatching {
                                    repository.toggleReaction(organizationId, message.id, userId, emoji)
                                }.onFailure {
                                    errorMessage = "Your reaction could not be saved. Try again."
                                }
                            }
                        },
                        onOpenAttachment = {
                            val path = message.attachmentPath
                            if (!path.isNullOrBlank()) {
                                openStorageFile(path, message.attachmentMimeType ?: "*/*")
                            }
                        },
                        onPlayVoice = {
                            val path = message.attachmentPath
                            if (!path.isNullOrBlank()) playVoiceMessage(path, message.id)
                        },
                        isVoicePlaying = activeVoiceMessageId == message.id && isVoicePlaying,
                        isVoicePreparing = activeVoiceMessageId == message.id && isVoicePreparing,
                        voiceProgress = if (activeVoiceMessageId == message.id && voiceDurationMs > 0)
                            voicePositionMs.toFloat() / voiceDurationMs.toFloat() else 0f,
                        voicePositionMs = if (activeVoiceMessageId == message.id) voicePositionMs else 0,
                        voiceDurationMs = if (activeVoiceMessageId == message.id) voiceDurationMs else 0,
                        onSeekVoice = { fraction ->
                            val player = voicePlayer
                            if (player != null && voiceDurationMs > 0) {
                                val target = (fraction * voiceDurationMs).toInt().coerceIn(0, voiceDurationMs)
                                runCatching { player.seekTo(target) }
                                voicePositionMs = target
                            }
                        }
                    )
                }
            }
        }

        if (!showConversationList && !showSharedLibrary) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editingMessageId != null || replyToMessageId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (editingMessageId != null) "Editing your message"
                        else "Replying to ${messages.firstOrNull { it.id == replyToMessageId }?.let { "message from " + (membersByUserId[it.senderId]?.displayName ?: "team member") } ?: "an earlier message"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        editingMessageId = null
                        replyToMessageId = null
                        draft = ""
                    }) { Text("Cancel") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (draft.isBlank() && editingMessageId == null && !isRecordingVoice) {
                    IconButton(onClick = { documentPicker.launch(arrayOf("*/*")) },
                        enabled = !sending && !uploadingAttachment) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach a file")
                    }
                    IconButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = !sending && !uploadingAttachment) {
                        Icon(Icons.Default.Image, contentDescription = "Choose a photo")
                    }
                }
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            .pointerInput(isRecordingVoice) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        recordingDragDistance = (recordingDragDistance + dragAmount).coerceAtMost(0f)
                                    },
                                    onDragEnd = {
                                        if (recordingDragDistance < -120f) discardVoiceRecording()
                                        recordingDragDistance = 0f
                                    },
                                    onDragCancel = { recordingDragDistance = 0f }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (recordingDragDistance < -24f) "Release to cancel" else "‹ Swipe left to cancel",
                            color = if (recordingDragDistance < -24f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.weight(1f))
                        Text(formatVoiceTime(recordingElapsedMs.toInt()), style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = { discardVoiceRecording() }) {
                        Icon(Icons.Default.Close, contentDescription = "Discard recording")
                    }
                    IconButton(onClick = { stopVoiceRecordingForPreview() }, enabled = !sending && !uploadingAttachment) {
                        Icon(Icons.Default.Stop, contentDescription = "Finish recording and preview")
                    }
                } else if (voicePreviewReady) {
                    IconButton(onClick = { toggleVoicePreview() }, enabled = !sending && !uploadingAttachment) {
                        Icon(
                            if (isVoicePreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isVoicePreviewPlaying) "Pause preview" else "Play preview"
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Voice preview", style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatVoiceTime(voicePreviewDurationMs.takeIf { it > 0 } ?: recordingElapsedMs.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { discardVoiceRecording() }) {
                        Icon(Icons.Default.Close, contentDescription = "Discard voice preview")
                    }
                    Button(onClick = { sendVoicePreview() }, enabled = !sending && !uploadingAttachment) {
                        Text(if (sending || uploadingAttachment) "Sending" else "Send")
                    }
                } else if (draft.isBlank() && editingMessageId == null) {
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                beginVoiceRecording()
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !sending && !uploadingAttachment && editingMessageId == null
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Record a voice message")
                    }
                }
                if (!isRecordingVoice) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= TeamChatRules.MAX_MESSAGE_LENGTH) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write a message…") },
                        label = { Text(if (editingMessageId != null) "Edit message" else "Message") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                        minLines = 1,
                        maxLines = 4,
                        enabled = !sending
                    )
                    Button(
                        onClick = { sendDraft() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        enabled = normalizedDraft.isNotEmpty() &&
                            normalizedDraft.length <= TeamChatRules.MAX_MESSAGE_LENGTH &&
                            !sending
                    ) {
                        Text(if (sending) "Saving" else if (editingMessageId != null) "Save" else "Send")
                    }
    
                }
            }
            Text(
                "${normalizedDraft.length} of ${TeamChatRules.MAX_MESSAGE_LENGTH} characters",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    if (showAttachmentOptions) {
        AlertDialog(
            onDismissRequest = { showAttachmentOptions = false },
            title = { Text("Attach to chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a photo from your gallery or a document from your device.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shareAttachmentWithTeam,
                            onCheckedChange = { shareAttachmentWithTeam = it }
                        )
                        Text(
                            "Also save to the team reference library",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        nextPickIsLibrary = false
                        showAttachmentOptions = false
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("Photo") }
                    TextButton(onClick = {
                        nextPickIsLibrary = false
                        showAttachmentOptions = false
                        documentPicker.launch(arrayOf("*/*"))
                    }) { Text("File") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAttachmentOptions = false }) { Text("Cancel") }
            }
        )
    }

    if (showConversationDetails) {
        val detailConversation = conversations.firstOrNull {
            it.conversation.id == activeConversationId
        }
        if (detailConversation != null) {
            val conversation = detailConversation.conversation
            val isGroup = conversation.conversationType == "group"
            val isGroupOwner = detailConversation.members.any {
                it.userId == userId && it.memberRole == "owner"
            }
            val detailTitle = when (conversation.conversationType) {
                "organization" -> organizationName
                "direct" -> detailConversation.members.firstOrNull { it.userId != userId }
                    ?.let { membersByUserId[it.userId]?.displayName } ?: "Direct message"
                else -> conversation.title ?: "Group chat"
            }
            AlertDialog(
                onDismissRequest = { showConversationDetails = false },
                title = { Text(detailTitle) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            when (conversation.conversationType) {
                                "organization" -> "Organization-wide conversation"
                                "direct" -> "Private direct conversation"
                                else -> "Group conversation"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isGroup && isGroupOwner) {
                            OutlinedTextField(
                                value = groupTitleDraft.ifBlank { detailTitle },
                                onValueChange = { if (it.length <= 80) groupTitleDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Group name") }
                            )
                            TextButton(
                                onClick = {
                                    val newTitle = groupTitleDraft.trim()
                                    scope.launch {
                                        runCatching { repository.renameGroupConversation(conversation.id, newTitle) }
                                            .onSuccess {
                                                conversations = repository.loadConversations(organizationId)
                                                groupTitleDraft = ""
                                            }
                                            .onFailure { errorMessage = it.message ?: "Group name could not be updated." }
                                    }
                                },
                                enabled = groupTitleDraft.trim().length in 2..80 && groupTitleDraft.trim() != detailTitle
                            ) { Text("Save group name") }
                        }
                        Text(
                            if (detailConversation.members.size == 1) "1 member" else "${detailConversation.members.size} members",
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (isGroup && isGroupOwner) {
                            TextButton(onClick = { showAddGroupMemberDialog = true }) {
                                Text("Add member")
                            }
                        }
                        detailConversation.members
                            .sortedBy { membersByUserId[it.userId]?.displayName?.lowercase().orEmpty() }
                            .forEach { member ->
                                val memberProfile = membersByUserId[member.userId]
                                val memberName = memberProfile?.displayName
                                    ?: if (member.userId == userId) "You" else "Team member"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(memberName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (member.userId == userId) "You · ${member.memberRole}" else member.memberRole,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (memberProfile != null && member.userId != userId) {
                                            TextButton(onClick = { onOpenMemberProfile(memberProfile) }) {
                                                Text("Profile")
                                            }
                                        }
                                        if (isGroup && isGroupOwner && member.memberRole != "owner" && memberProfile != null) {
                                            TextButton(onClick = { pendingGroupRemoval = memberProfile }) {
                                                Text("Remove", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showConversationDetails = false }) { Text("Done") }
                }
            )
        }
    }

    if (showAddGroupMemberDialog) {
        val currentGroup = conversations.firstOrNull { it.conversation.id == activeConversationId }
        val currentIds = currentGroup?.members?.map { it.userId }?.toSet().orEmpty()
        AlertDialog(
            onDismissRequest = { showAddGroupMemberDialog = false },
            title = { Text("Add group member") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    membersByUserId.values
                        .filter { it.userId !in currentIds }
                        .sortedBy { it.displayName.lowercase() }
                        .forEach { candidate ->
                            TextButton(
                                onClick = {
                                    val conversationId = activeConversationId ?: return@TextButton
                                    scope.launch {
                                        runCatching { repository.addGroupMember(conversationId, candidate.userId) }
                                            .onSuccess {
                                                conversations = repository.loadConversations(organizationId)
                                                showAddGroupMemberDialog = false
                                            }
                                            .onFailure { errorMessage = it.message ?: "Member could not be added." }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(candidate.displayName) }
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddGroupMemberDialog = false }) { Text("Done") }
            }
        )
    }

    pendingGroupRemoval?.let { memberToRemove ->
        AlertDialog(
            onDismissRequest = { pendingGroupRemoval = null },
            title = { Text("Remove ${memberToRemove.displayName}?") },
            text = { Text("They will no longer be able to view or send messages in this group.") },
            confirmButton = {
                TextButton(onClick = {
                    val conversationId = activeConversationId
                    pendingGroupRemoval = null
                    if (conversationId != null) {
                        scope.launch {
                            runCatching { repository.removeGroupMember(conversationId, memberToRemove.userId) }
                                .onSuccess {
                                    conversations = repository.loadConversations(organizationId)
                                    if (memberToRemove.userId == userId) {
                                        activeConversationId = conversations.firstOrNull()?.conversation?.id
                                    }
                                }
                                .onFailure { errorMessage = it.message ?: "Member could not be removed." }
                        }
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroupRemoval = null }) { Text("Cancel") }
            }
        )
    }

    if (showNewConversationDialog) {
        NewChatConversationDialog(
            members = membersByUserId.values.sortedBy { it.displayName.lowercase() },
            currentUserId = userId,
            onDismiss = { showNewConversationDialog = false },
            onOpenDirect = { otherUserId ->
                scope.launch {
                    runCatching {
                        repository.getOrCreateDirectConversation(organizationId, otherUserId)
                    }.onSuccess { conversationId ->
                        conversations = repository.loadConversations(organizationId)
                        activeConversationId = conversationId
                        showConversationList = false
                        showNewConversationDialog = false
                    }.onFailure { error ->
                        errorMessage = error.message ?: "The direct message could not be opened."
                    }
                }
            },
            onCreateGroup = { title, selectedUserIds ->
                scope.launch {
                    runCatching {
                        repository.createGroupConversation(organizationId, title, selectedUserIds)
                    }.onSuccess { conversationId ->
                        conversations = repository.loadConversations(organizationId)
                        activeConversationId = conversationId
                        showConversationList = false
                        showNewConversationDialog = false
                    }.onFailure { error ->
                        errorMessage = error.message ?: "The group could not be created."
                    }
                }
            }
        )
    }
}

@Composable
private fun NewChatConversationDialog(
    members: List<ServiceMemberOption>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onOpenDirect: (String) -> Unit,
    onCreateGroup: (String, List<String>) -> Unit
) {
    var mode by remember { mutableStateOf("Direct message") }
    var groupType by remember { mutableStateOf("Musicians") }
    var customTitle by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    val groupTypes = listOf("Musicians", "Instrumentalists", "Singers", "Dancers", "Multimedia", "Custom")
    val groupTitle = if (groupType == "Custom") customTitle.trim() else groupType

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New conversation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "Direct message",
                        onClick = { mode = "Direct message" },
                        label = { Text("Direct") }
                    )
                    FilterChip(
                        selected = mode == "Group chat",
                        onClick = { mode = "Group chat" },
                        label = { Text("Group") }
                    )
                }
                if (mode == "Direct message") {
                    Text("Choose a team member to start or reopen your private conversation.")
                    Column(
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        members.filter { it.userId != currentUserId }.forEach { member ->
                            TextButton(
                                onClick = { onOpenDirect(member.userId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(member.displayName)
                                    Text("Message", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    Text("Choose a ministry group or create a custom group.")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groupTypes.forEach { option ->
                            FilterChip(
                                selected = groupType == option,
                                onClick = { groupType = option },
                                label = { Text(option) }
                            )
                        }
                    }
                    if (groupType == "Custom") {
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { if (it.length <= 80) customTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Group name") }
                        )
                    }
                    Text("Select group members", style = MaterialTheme.typography.labelLarge)
                    Column(
                        modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        members.filter { it.userId != currentUserId }.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedMembers = if (member.userId in selectedMembers) {
                                        selectedMembers - member.userId
                                    } else {
                                        selectedMembers + member.userId
                                    }
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = member.userId in selectedMembers,
                                    onCheckedChange = { checked ->
                                        selectedMembers = if (checked) selectedMembers + member.userId
                                        else selectedMembers - member.userId
                                    }
                                )
                                Text(member.displayName)
                            }
                        }
                    }
                    Text(
                        "You will be included as the group creator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (mode == "Group chat") {
                TextButton(
                    onClick = { onCreateGroup(groupTitle, selectedMembers.toList()) },
                    enabled = groupTitle.length in 2..80 && selectedMembers.isNotEmpty()
                ) { Text("Create group") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamChatMessageBubble(
    message: TeamChatMessageDto,
    isOwnMessage: Boolean,
    senderName: String,
    avatarUrl: String?,
    readByCount: Int,
    reactions: List<TeamChatReactionDto>,
    currentUserId: String,
    replyPreview: String?,
    onOpenProfile: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: (Boolean) -> Unit,
    onReact: (String) -> Unit,
    onOpenAttachment: () -> Unit,
    onPlayVoice: () -> Unit,
    isVoicePlaying: Boolean,
    isVoicePreparing: Boolean,
    voiceProgress: Float,
    voicePositionMs: Int,
    voiceDurationMs: Int,
    onSeekVoice: (Float) -> Unit
) {
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    var showEmojiPicker by remember(message.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(message.id) { mutableStateOf(false) }
    var customEmoji by remember(message.id) { mutableStateOf("") }
    val isDeleted = message.deletedAt != null
    val isPinned = message.pinnedAt != null
    val groupedReactions = reactions.groupBy { it.emoji }.toSortedMap()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwnMessage) {
            Box(
                modifier = Modifier.padding(end = 7.dp, bottom = 3.dp).size(34.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = "${senderName} profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else Text(senderName.take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .pointerInput(message.id) {
                        var horizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                horizontalDrag += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(horizontalDrag) > 72f && !isDeleted) onReply()
                                horizontalDrag = 0f
                            },
                            onDragCancel = { horizontalDrag = 0f }
                        )
                    }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuExpanded = true }
                        ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (isOwnMessage) 4.dp else 16.dp,
                        bottomStart = if (isOwnMessage) 16.dp else 4.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOwnMessage) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isOwnMessage) {
                            Text(
                                senderName,
                                modifier = Modifier.clickable(onClick = onOpenProfile),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!replyPreview.isNullOrBlank()) {
                            Text(
                                "↪ $replyPreview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 2
                            )
                        }
                        if (message.content.isNotBlank()) {
                            Text(
                                message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!isDeleted && !message.attachmentPath.isNullOrBlank() &&
                            message.attachmentMimeType?.startsWith("audio/") == true
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    IconButton(onClick = onPlayVoice, enabled = !isVoicePreparing) {
                                        if (isVoicePreparing) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = if (isVoicePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isVoicePlaying) "Pause voice message" else "Play voice message"
                                            )
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Voice message", style = MaterialTheme.typography.labelLarge)
                                        val playedWaveColor = MaterialTheme.colorScheme.primary
                                        val unplayedWaveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        Canvas(
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            val count = 32
                                            val gap = size.width / (count * 1.75f)
                                            val barWidth = gap * 0.72f
                                            for (index in 0 until count) {
                                                val wave = (0.22f + 0.78f * kotlin.math.abs(kotlin.math.sin(index * 1.17f)) * (0.55f + 0.45f * kotlin.math.abs(kotlin.math.cos(index * 0.39f))))
                                                val barHeight = size.height * wave
                                                val left = index * gap + (gap - barWidth) / 2f
                                                val top = (size.height - barHeight) / 2f
                                                drawRoundRect(
                                                    color = if (index.toFloat() / count <= voiceProgress) playedWaveColor else unplayedWaveColor,
                                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                                                )
                                            }
                                        }
                                        Slider(
                                            value = voiceProgress.coerceIn(0f, 1f),
                                            onValueChange = onSeekVoice,
                                            enabled = voiceDurationMs > 0 && !isVoicePreparing
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(formatVoiceTime(voicePositionMs), style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatVoiceTime(voiceDurationMs), style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else if (!isDeleted && !message.attachmentPath.isNullOrBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAttachment),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            message.attachmentName ?: "Attachment",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 2
                                        )
                                        Text(
                                            "${message.attachmentMimeType ?: "File"} · ${formatChatFileSize(message.attachmentSize ?: 0L)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "Open",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        val receipt = buildString {
                            append(formatChatTime(message.createdAt))
                            if (message.editedAt != null && !isDeleted) append(" · Edited")
                            if (isOwnMessage && readByCount > 0) append(" · Seen by $readByCount")
                            if (isPinned) append(" · Pinned")
                        }
                        Text(
                            receipt,
                            modifier = Modifier.align(Alignment.End),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (groupedReactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedReactions.forEach { (emoji, rows) ->
                            TextButton(
                                onClick = { onReact(emoji) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                modifier = Modifier.heightIn(min = 28.dp)
                            ) {
                                Text("$emoji ${rows.size}", style = MaterialTheme.typography.labelSmall)
                                if (rows.any { it.userId == currentUserId }) {
                                    Text(" ✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (!isDeleted) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("❤️", "😆", "😯", "😥", "😡", "👍").forEach { emoji ->
                                TextButton(
                                    onClick = {
                                        onReact(emoji)
                                        menuExpanded = false
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text(emoji) }
                            }
                            TextButton(
                                onClick = {
                                    showEmojiPicker = true
                                    menuExpanded = false
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("+") }
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        onClick = { menuExpanded = false; onReply() }
                    )
                    if (isOwnMessage) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuExpanded = false; showDeleteConfirmation = true }
                        )
                    }
                    if (!isPinned) {
                        DropdownMenuItem(
                            text = { Text("Pin message") },
                            onClick = { menuExpanded = false; onPin(true) }
                        )
                    } else if (message.pinnedBy == currentUserId) {
                        DropdownMenuItem(
                            text = { Text("Unpin message") },
                            onClick = { menuExpanded = false; onPin(false) }
                        )
                    }
                }
            }
        }
        if (isOwnMessage) {
            Box(
                modifier = Modifier.padding(start = 7.dp, bottom = 3.dp).size(34.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = "Your profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else Text(senderName.take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showEmojiPicker) {
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = { Text("Add a reaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose any emoji using your device's emoji keyboard.")
                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = { value ->
                            if (value.length <= 16) customEmoji = value
                        },
                        singleLine = true,
                        label = { Text("Emoji") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = customEmoji.trim()
                        if (value.isNotEmpty()) onReact(value)
                        showEmojiPicker = false
                    },
                    enabled = customEmoji.trim().isNotEmpty()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showEmojiPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete message?") },
            text = { Text("This message will be replaced with a deleted-message notice for everyone in the conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

private data class PickedChatFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

private fun readPickedChatFile(context: Context, uri: Uri): PickedChatFile {
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
    return PickedChatFile(fileName, mimeType, bytes)
}

private fun formatVoiceTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatChatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun formatChatTime(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    return DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
