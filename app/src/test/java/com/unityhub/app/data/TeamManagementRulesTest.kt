package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TeamManagementRulesTest {

    @Test
    fun positionNameIsTrimmed() {
        assertEquals("Electric Guitar", TeamManagementRules.normalizePositionName("  Electric Guitar  "))
    }

    @Test
    fun positionNameMustHaveAtLeastTwoCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            TeamManagementRules.normalizePositionName(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamManagementRules.normalizePositionName("A")
        }
    }

    @Test
    fun positionNameCannotExceedSixtyCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            TeamManagementRules.normalizePositionName("A".repeat(61))
        }
    }

    @Test
    fun positionDiffIdentifiesNewAndRemovedPositions() {
        val current = setOf("vocal", "keys")
        val selected = setOf("keys", "drums")

        assertEquals(setOf("drums"), TeamManagementRules.positionsToAdd(current, selected))
        assertEquals(setOf("vocal"), TeamManagementRules.positionsToRemove(current, selected))
    }

    @Test
    fun positionDiffDoesNotChangeIdenticalSelections() {
        val current = setOf("vocal", "keys")

        assertEquals(emptySet<String>(), TeamManagementRules.positionsToAdd(current, current))
        assertEquals(emptySet<String>(), TeamManagementRules.positionsToRemove(current, current))
    }
}
