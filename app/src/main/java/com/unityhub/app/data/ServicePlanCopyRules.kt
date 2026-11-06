package com.unityhub.app.data

internal data class PreparedServicePlanItemCopy(
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

internal object ServicePlanCopyRules {
    fun prepareCopy(
        sourceItems: List<ServicePlanItemDto>,
        destinationItems: List<ServicePlanItemDto>,
        organizationId: String,
        targetPlanId: String
    ): List<PreparedServicePlanItemCopy> {
        require(organizationId.isNotBlank() && targetPlanId.isNotBlank())
        require(sourceItems.isNotEmpty()) {
            "The selected service does not have any setlist items to copy."
        }
        require(sourceItems.all { it.organizationId == organizationId }) {
            "The source setlist contains items from a different organization."
        }
        require(destinationItems.all {
            it.organizationId == organizationId && it.servicePlanId == targetPlanId
        }) {
            "The destination setlist contains items from a different service or organization."
        }

        val firstItemOrder = (destinationItems.maxOfOrNull { it.itemOrder } ?: -1) + 1
        return sourceItems
            .sortedWith(compareBy<ServicePlanItemDto> { it.itemOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id })
            .mapIndexed { index, item ->
                PreparedServicePlanItemCopy(
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
