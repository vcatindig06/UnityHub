package com.unityhub.app.sync

internal object OfflineSyncScopeRules {
    fun maySync(
        requestedUserId: String,
        authenticatedUserId: String?,
        requestedOrganizationId: String,
        membershipOrganizationId: String?
    ): Boolean =
        requestedUserId.isNotBlank() &&
            requestedOrganizationId.isNotBlank() &&
            authenticatedUserId == requestedUserId &&
            membershipOrganizationId == requestedOrganizationId
}
