package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@Serializable
data class ChatSharedFileDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("uploader_id") val uploaderId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("shared_to_team") val sharedToTeam: Boolean,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewChatSharedFile(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_id") val conversationId: String?,
    @SerialName("uploader_id") val uploaderId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("shared_to_team") val sharedToTeam: Boolean
)

data class ChatMessageAttachment(
    val id: String,
    val storagePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long
)

@OptIn(SupabaseExperimental::class)
class ChatFileRepository(private val client: SupabaseClient) {
    suspend fun uploadChatAttachment(
        organizationId: String,
        conversationId: String,
        uploaderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        sharedToTeam: Boolean
    ): ChatSharedFileDto {
        require(organizationId.isNotBlank() && conversationId.isNotBlank() && uploaderId.isNotBlank())
        return upload(
            organizationId = organizationId,
            conversationId = conversationId,
            uploaderId = uploaderId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            sharedToTeam = sharedToTeam,
            folder = conversationId
        )
    }

    suspend fun uploadSharedLibraryFile(
        organizationId: String,
        uploaderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): ChatSharedFileDto {
        require(organizationId.isNotBlank() && uploaderId.isNotBlank())
        return upload(
            organizationId = organizationId,
            conversationId = null,
            uploaderId = uploaderId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            sharedToTeam = true,
            folder = "library"
        )
    }

    private suspend fun upload(
        organizationId: String,
        conversationId: String?,
        uploaderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        sharedToTeam: Boolean,
        folder: String
    ): ChatSharedFileDto {
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        require(bytes.size <= MAX_FILE_SIZE_BYTES) { "Files must be 25 MB or smaller." }
        require(isSupportedMimeType(mimeType)) { "This file type is not supported." }

        val safeName = fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(120)
            .ifBlank { "attachment" }
        val id = UUID.randomUUID().toString()
        val path = "$organizationId/$folder/$uploaderId/${id}_$safeName"
        val metadata = NewChatSharedFile(
            id = id,
            organizationId = organizationId,
            conversationId = conversationId,
            uploaderId = uploaderId,
            storagePath = path,
            fileName = fileName.take(255),
            mimeType = mimeType,
            fileSize = bytes.size.toLong(),
            sharedToTeam = sharedToTeam
        )

        client.from("chat_shared_files").insert(metadata)
        try {
            client.storage[BUCKET].upload(path, bytes) {
                upsert = false
                contentType = ContentType.parse(mimeType)
            }
        } catch (error: Exception) {
            runCatching {
                client.from("chat_shared_files").delete {
                    filter { eq("id", id) }
                }
            }
            throw error
        }

        return ChatSharedFileDto(
            id = id,
            organizationId = organizationId,
            conversationId = conversationId,
            uploaderId = uploaderId,
            storagePath = path,
            fileName = fileName.take(255),
            mimeType = mimeType,
            fileSize = bytes.size.toLong(),
            sharedToTeam = sharedToTeam,
            createdAt = Instant.now().toString()
        )
    }

    suspend fun loadSharedFiles(organizationId: String): List<ChatSharedFileDto> {
        require(organizationId.isNotBlank())
        return client.from("chat_shared_files").select {
            filter {
                eq("organization_id", organizationId)
                eq("shared_to_team", true)
            }
        }.decodeList<ChatSharedFileDto>().sortedByDescending { it.createdAt }
    }

    suspend fun createSignedUrl(path: String): String {
        require(path.isNotBlank())
        return client.storage[BUCKET].createSignedUrl(path = path, expiresIn = 5.minutes)
    }

    companion object {
        const val BUCKET = "chat-attachments"
        const val MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024

        private val supportedMimeTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "audio/mpeg",
            "audio/mp4",
            "audio/aac",
            "audio/ogg",
            "audio/wav",
            "video/mp4"
        )

        fun isSupportedMimeType(mimeType: String): Boolean =
            mimeType.lowercase() in supportedMimeTypes
    }
}
