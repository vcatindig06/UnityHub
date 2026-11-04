package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class ServicePlanDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val title: String,
    @SerialName("service_date") val serviceDate: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    val notes: String = "",
    val status: String = "draft",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ServicePlanItemDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("service_plan_id") val servicePlanId: String,
    @SerialName("song_id") val songId: String? = null,
    val title: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("chordpro_snapshot") val chordproSnapshot: String = "",
    @SerialName("item_order") val itemOrder: Int,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val notes: String = "",
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewServicePlan(
    @SerialName("organization_id") val organizationId: String,
    val title: String,
    @SerialName("service_date") val serviceDate: String?,
    @SerialName("start_time") val startTime: String?,
    val notes: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class NewServicePlanItem(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("service_plan_id") val servicePlanId: String,
    @SerialName("song_id") val songId: String?,
    val title: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("chordpro_snapshot") val chordproSnapshot: String,
    @SerialName("item_order") val itemOrder: Int,
    @SerialName("duration_minutes") val durationMinutes: Int?,
    val notes: String
)

class ServicePlanRepository(private val client: SupabaseClient) {
    suspend fun loadPlans(organizationId: String): List<ServicePlanDto> =
        client.from("service_plans").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ServicePlanDto>()
            .sortedWith(compareBy<ServicePlanDto> { it.serviceDate ?: "9999-12-31" }
                .thenBy { it.startTime.orEmpty() }
                .thenBy { it.title.lowercase() })

    suspend fun loadPlanItems(planId: String, organizationId: String): List<ServicePlanItemDto> =
        client.from("service_plan_items").select {
            filter {
                eq("service_plan_id", planId)
                eq("organization_id", organizationId)
            }
        }.decodeList<ServicePlanItemDto>().sortedBy { it.itemOrder }

    suspend fun loadSongs(organizationId: String): List<SongDto> =
        client.from("songs").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<SongDto>().sortedBy { it.title.lowercase() }

    suspend fun createPlan(
        organizationId: String, userId: String, title: String,
        serviceDate: String, startTime: String, notes: String
    ) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.length in 1..160) {
            "Enter a service title between 1 and 160 characters."
        }
        client.from("service_plans").insert(
            NewServicePlan(
                organizationId, normalizedTitle, validateDate(serviceDate),
                validateTime(startTime), notes.trim(), userId
            )
        )
    }

    suspend fun updatePlan(
        planId: String, organizationId: String, title: String,
        serviceDate: String, startTime: String, notes: String
    ) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.length in 1..160) {
            "Enter a service title between 1 and 160 characters."
        }
        client.from("service_plans").update({
            set("title", normalizedTitle)
            set("service_date", validateDate(serviceDate))
            set("start_time", validateTime(startTime))
            set("notes", notes.trim())
        }) {
            filter {
                eq("id", planId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun updateStatus(planId: String, organizationId: String, status: String) {
        require(status in setOf("draft", "published", "completed", "cancelled"))
        client.from("service_plans").update({ set("status", status) }) {
            filter {
                eq("id", planId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun addSongToPlan(
        planId: String, organizationId: String, song: SongDto, itemOrder: Int
    ) {
        require(song.organizationId == organizationId) {
            "This song does not belong to the current worship team."
        }
        client.from("service_plan_items").insert(
            NewServicePlanItem(
                organizationId, planId, song.id, song.title, "song",
                song.chordpro, itemOrder, null, ""
            )
        )
    }

    suspend fun addCustomItem(
        planId: String, organizationId: String, title: String,
        itemType: String, durationMinutes: Int?, itemOrder: Int
    ) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.length in 1..200) {
            "Enter an item title between 1 and 200 characters."
        }
        require(itemType in setOf("prayer", "reading", "announcement", "other"))
        require(durationMinutes == null || durationMinutes in 0..600) {
            "Duration must be between 0 and 600 minutes."
        }
        client.from("service_plan_items").insert(
            NewServicePlanItem(
                organizationId, planId, null, normalizedTitle, itemType,
                "", itemOrder, durationMinutes, ""
            )
        )
    }

    suspend fun removeItem(itemId: String, planId: String, organizationId: String) {
        client.from("service_plan_items").delete {
            filter {
                eq("id", itemId)
                eq("service_plan_id", planId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun moveItem(
        items: List<ServicePlanItemDto>, index: Int, direction: Int,
        planId: String, organizationId: String
    ) {
        val targetIndex = index + direction
        if (index !in items.indices || targetIndex !in items.indices) return
        val current = items[index]
        val target = items[targetIndex]
        updateItemOrder(current.id, planId, organizationId, target.itemOrder)
        updateItemOrder(target.id, planId, organizationId, current.itemOrder)
    }

    private suspend fun updateItemOrder(
        itemId: String, planId: String, organizationId: String, newOrder: Int
    ) {
        client.from("service_plan_items").update({ set("item_order", newOrder) }) {
            filter {
                eq("id", itemId)
                eq("service_plan_id", planId)
                eq("organization_id", organizationId)
            }
        }
    }

    private fun validateDate(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        require(runCatching { LocalDate.parse(normalized) }.isSuccess) {
            "Use a valid date in YYYY-MM-DD format."
        }
        return normalized
    }

    private fun validateTime(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        val parsed = runCatching { LocalTime.parse(normalized) }.getOrElse {
            throw IllegalArgumentException("Use a valid 24-hour time, such as 09:30.")
        }
        return parsed.toString().let { if (it.length == 5) "$it:00" else it }
    }
}
