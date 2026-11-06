package com.unityhub.app.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountProfileRulesTest {

    @Test
    fun displayNameNormalizationTrimsAndCollapsesWhitespace() {
        assertEquals(
            "Vincent Catindig",
            AccountProfileRules.normalizeDisplayName("  Vincent   Catindig  ")
        )
    }

    @Test
    fun acceptsDisplayNamesWithinTheSupportedLength() {
        assertTrue(AccountProfileRules.isValidDisplayName("Jo"))
        assertTrue(AccountProfileRules.isValidDisplayName("UnityHub Member"))
        assertTrue(AccountProfileRules.isValidDisplayName("A".repeat(80)))
    }

    @Test
    fun rejectsDisplayNamesOutsideTheSupportedLength() {
        assertFalse(AccountProfileRules.isValidDisplayName(""))
        assertFalse(AccountProfileRules.isValidDisplayName("A"))
        assertFalse(AccountProfileRules.isValidDisplayName("A".repeat(81)))
        assertFalse(AccountProfileRules.isValidDisplayName("   "))
    }

    @Test
    fun prefersDisplayNameMetadataOverProviderName() {
        val metadata = buildJsonObject {
            put("display_name", "UnityHub member")
            put("full_name", "Provider supplied name")
            put("name", "Fallback name")
        }

        assertEquals(
            "UnityHub member",
            AccountProfileRules.displayNameFromMetadata(metadata)
        )
    }

    @Test
    fun fallsBackToProviderNameWhenDisplayNameIsMissing() {
        val metadata = buildJsonObject {
            put("full_name", "Provider supplied name")
            put("name", "Fallback name")
        }

        assertEquals(
            "Provider supplied name",
            AccountProfileRules.displayNameFromMetadata(metadata)
        )
    }

    @Test
    fun returnsBlankWhenProfileMetadataIsMissing() {
        assertEquals("", AccountProfileRules.displayNameFromMetadata(null))
        assertEquals("", AccountProfileRules.displayNameFromMetadata(buildJsonObject { }))
    }
    @Test
    fun databaseProfileNameTakesPrecedenceOverRegistrationMetadata() {
        val metadata = buildJsonObject {
            put("display_name", "Old registration name")
        }

        assertEquals(
            "Current profile name",
            AccountProfileRules.resolveDisplayName(
                profileName = " Current profile name ",
                metadata = metadata,
                email = "member@example.com"
            )
        )
    }

    @Test
    fun metadataIsUsedWhenTheProfileNameIsBlank() {
        val metadata = buildJsonObject {
            put("display_name", "Registration name")
        }

        assertEquals(
            "Registration name",
            AccountProfileRules.resolveDisplayName(
                profileName = " ",
                metadata = metadata,
                email = "member@example.com"
            )
        )
    }

    @Test
    fun emailNameIsUsedWhenNoProfileOrMetadataNameExists() {
        assertEquals(
            "member",
            AccountProfileRules.resolveDisplayName(
                profileName = null,
                metadata = null,
                email = "member@example.com"
            )
        )
    }

    @Test
    fun safeDefaultIsUsedWhenAllProfileSourcesAreBlank() {
        assertEquals(
            "UnityHub member",
            AccountProfileRules.resolveDisplayName(
                profileName = " ",
                metadata = buildJsonObject { },
                email = null
            )
        )
    }

}
