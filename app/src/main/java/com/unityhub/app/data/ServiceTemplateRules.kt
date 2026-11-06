package com.unityhub.app.data

internal data class PreparedServiceTemplateItem(
    val songId: String?,
    val title: String,
    val itemType: String,
    val chordproSnapshot: String,
    val itemOrder: Int,
    val durationMinutes: Int?,
    val notes: String
)

internal data class PreparedServicePlanItemFromTemplate(
    val organizationId: String,
    val servicePlanId: String,
    val songId: String?,
    val title: String,
    val itemType: String,
    val chordproSnapshot: String,
    val itemOrder: Int,
    val durationMinutes: Int?,
    val notes: String
)

internal object ServiceTemplateRules {
    fun normalizeName(name: String): String {
        val normalized = name.trim()
        require(normalized.length in 2..100) {
            "Enter a template name between 2 and 100 characters."
        }
        return normalized
    }

    fun normalizeDescription(description: String): String {
        val normalized = description.trim()
        require(normalized.length <= 500) {
            "Keep the template description within 500 characters."
        }
        return normalized
    }

    fun prepareTemplateItems(
        sourceItems: List<ServicePlanItemDto>,
        organizationId: String
    ): List<PreparedServiceTemplateItem> {
        require(organizationId.isNotBlank())
        require(sourceItems.isNotEmpty()) {
            "Add at least one item to the service before saving it as a template."
        }
        require(sourceItems.all { it.organizationId == organizationId }) {
            "The service contains an item from a different organization."
        }

        return sourceItems
            .sortedWith(compareBy<ServicePlanItemDto> { it.itemOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id })
            .mapIndexed { index, item ->
                PreparedServiceTemplateItem(
                    songId = item.songId,
                    title = item.title,
                    itemType = item.itemType,
                    chordproSnapshot = item.chordproSnapshot,
                    itemOrder = index,
                    durationMinutes = item.durationMinutes,
                    notes = item.notes
                )
            }
    }

    fun prepareItemsForService(
        templateItems: List<ServiceTemplateItemDto>,
        destinationItems: List<ServicePlanItemDto>,
        organizationId: String,
        targetPlanId: String
    ): List<PreparedServicePlanItemFromTemplate> {
        require(organizationId.isNotBlank() && targetPlanId.isNotBlank())
        require(templateItems.isNotEmpty()) {
            "The selected template does not contain any setlist items."
        }
        require(templateItems.all { it.organizationId == organizationId }) {
            "The template contains items from a different organization."
        }
        require(templateItems.map { it.templateId }.distinct().size == 1) {
            "The selected template contains items from more than one template."
        }
        require(destinationItems.all {
            it.organizationId == organizationId && it.servicePlanId == targetPlanId
        }) {
            "The destination service contains items from a different service or organization."
        }

        val firstItemOrder = (destinationItems.maxOfOrNull { it.itemOrder } ?: -1) + 1
        return templateItems
            .sortedWith(compareBy<ServiceTemplateItemDto> { it.itemOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id })
            .mapIndexed { index, item ->
                PreparedServicePlanItemFromTemplate(
                    organizationId = organizationId,
                    servicePlanId = targetPlanId,
                    songId = item.songId,
                    title = item.title,
                    itemType = item.itemType,
                    chordproSnapshot = item.chordproSnapshot,
                    itemOrder = firstItemOrder + index,
                    durationMinutes = item.durationMinutes,
                    notes = item.notes
                )
            }
    }
}
