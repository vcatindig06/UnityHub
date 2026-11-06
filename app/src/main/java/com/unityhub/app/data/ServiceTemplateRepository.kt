package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ServiceTemplateDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val description: String = "",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ServiceTemplateItemDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("template_id") val templateId: String,
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
private data class NewServiceTemplate(
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val description: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class NewServiceTemplateItem(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("template_id") val templateId: String,
    @SerialName("song_id") val songId: String?,
    val title: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("chordpro_snapshot") val chordproSnapshot: String,
    @SerialName("item_order") val itemOrder: Int,
    @SerialName("duration_minutes") val durationMinutes: Int?,
    val notes: String
)

@Serializable
private data class NewServicePlanItemFromTemplate(
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

class ServiceTemplateRepository(private val client: SupabaseClient) {
    private val servicePlanRepository = ServicePlanRepository(client)

    suspend fun loadTemplates(organizationId: String): List<ServiceTemplateDto> {
        require(organizationId.isNotBlank())
        return client.from("service_templates").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ServiceTemplateDto>()
            .sortedWith(compareBy<ServiceTemplateDto> { it.name.lowercase() }
                .thenByDescending { it.updatedAt })
    }

    suspend fun loadTemplateItems(
        templateId: String,
        organizationId: String
    ): List<ServiceTemplateItemDto> {
        require(templateId.isNotBlank() && organizationId.isNotBlank())
        return client.from("service_template_items").select {
            filter {
                eq("template_id", templateId)
                eq("organization_id", organizationId)
            }
        }.decodeList<ServiceTemplateItemDto>().sortedWith(
            compareBy<ServiceTemplateItemDto> { it.itemOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
    }

    suspend fun loadItemCounts(organizationId: String): Map<String, Int> {
        require(organizationId.isNotBlank())
        return client.from("service_template_items").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ServiceTemplateItemDto>()
            .groupingBy { it.templateId }
            .eachCount()
    }

    suspend fun saveFromService(
        servicePlanId: String,
        organizationId: String,
        userId: String,
        name: String,
        description: String
    ): ServiceTemplateDto {
        require(servicePlanId.isNotBlank() && organizationId.isNotBlank() && userId.isNotBlank())
        val normalizedName = ServiceTemplateRules.normalizeName(name)
        val normalizedDescription = ServiceTemplateRules.normalizeDescription(description)
        val servicePlan = servicePlanRepository.loadPlans(organizationId)
            .firstOrNull { it.id == servicePlanId }
            ?: throw IllegalArgumentException("The selected service does not belong to this worship team.")
        val sourceItems = servicePlanRepository.loadPlanItems(servicePlan.id, organizationId)
        val preparedItems = ServiceTemplateRules.prepareTemplateItems(
            sourceItems = sourceItems,
            organizationId = organizationId
        )

        client.from("service_templates").insert(
            NewServiceTemplate(
                organizationId = organizationId,
                name = normalizedName,
                description = normalizedDescription,
                createdBy = userId
            )
        )

        val createdTemplate = client.from("service_templates").select {
            filter {
                eq("organization_id", organizationId)
                eq("created_by", userId)
                eq("name", normalizedName)
            }
        }.decodeList<ServiceTemplateDto>().singleOrNull()
            ?: throw IllegalStateException("The template was saved but could not be loaded. Refresh the template list.")

        try {
            client.from("service_template_items").insert(
                preparedItems.map { item ->
                    NewServiceTemplateItem(
                        organizationId = organizationId,
                        templateId = createdTemplate.id,
                        songId = item.songId,
                        title = item.title,
                        itemType = item.itemType,
                        chordproSnapshot = item.chordproSnapshot,
                        itemOrder = item.itemOrder,
                        durationMinutes = item.durationMinutes,
                        notes = item.notes
                    )
                }
            )
        } catch (exception: Exception) {
            runCatching {
                client.from("service_templates").delete {
                    filter {
                        eq("id", createdTemplate.id)
                        eq("organization_id", organizationId)
                    }
                }
            }
            throw exception
        }
        return createdTemplate
    }

    suspend fun renameTemplate(
        templateId: String,
        organizationId: String,
        name: String,
        description: String
    ) {
        require(templateId.isNotBlank() && organizationId.isNotBlank())
        val normalizedName = ServiceTemplateRules.normalizeName(name)
        val normalizedDescription = ServiceTemplateRules.normalizeDescription(description)
        client.from("service_templates").update({
            set("name", normalizedName)
            set("description", normalizedDescription)
            set("updated_at", Instant.now().toString())
        }) {
            filter {
                eq("id", templateId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun deleteTemplate(templateId: String, organizationId: String) {
        require(templateId.isNotBlank() && organizationId.isNotBlank())
        client.from("service_templates").delete {
            filter {
                eq("id", templateId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun applyToService(
        templateId: String,
        targetPlanId: String,
        organizationId: String
    ): Int {
        require(templateId.isNotBlank() && targetPlanId.isNotBlank() && organizationId.isNotBlank())
        val targetPlan = servicePlanRepository.loadPlans(organizationId)
            .firstOrNull { it.id == targetPlanId }
            ?: throw IllegalArgumentException("The destination service does not belong to this worship team.")
        require(targetPlan.status != "completed" && targetPlan.status != "cancelled") {
            "A completed or cancelled service cannot be changed."
        }
        val template = loadTemplates(organizationId).firstOrNull { it.id == templateId }
            ?: throw IllegalArgumentException("The selected template is no longer available.")
        require(template.organizationId == organizationId) {
            "This template does not belong to the current worship team."
        }

        val templateItems = loadTemplateItems(template.id, organizationId)
        val destinationItems = servicePlanRepository.loadPlanItems(targetPlan.id, organizationId)
        val preparedItems = ServiceTemplateRules.prepareItemsForService(
            templateItems = templateItems,
            destinationItems = destinationItems,
            organizationId = organizationId,
            targetPlanId = targetPlan.id
        )

        client.from("service_plan_items").insert(
            preparedItems.map { item ->
                NewServicePlanItemFromTemplate(
                    organizationId = item.organizationId,
                    servicePlanId = item.servicePlanId,
                    songId = item.songId,
                    title = item.title,
                    itemType = item.itemType,
                    chordproSnapshot = item.chordproSnapshot,
                    itemOrder = item.itemOrder,
                    durationMinutes = item.durationMinutes,
                    notes = item.notes
                )
            }
        )
        return preparedItems.size
    }
}
