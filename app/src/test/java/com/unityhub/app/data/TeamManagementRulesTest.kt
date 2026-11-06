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
    @Test
    fun directorySearchMatchesMemberNamesWithoutCaseSensitivity() {
        val members = listOf(
            ServiceMemberOption("m1", "u1", "Aaron Dela Cruz", "team_member"),
            ServiceMemberOption("m2", "u2", "Bea Santos", "worship_leader")
        )

        val result = TeamDirectoryRules.filterMembers(
            members = members,
            assignedPositionsByMember = emptyMap(),
            searchQuery = "bea",
            selectedPosition = ""
        )

        assertEquals(listOf("m2"), result.map { it.memberId })
    }

    @Test
    fun directorySearchCanMatchTeamRoleAndAssignedPosition() {
        val members = listOf(
            ServiceMemberOption("m1", "u1", "Aaron Dela Cruz", "team_member"),
            ServiceMemberOption("m2", "u2", "Bea Santos", "worship_leader")
        )
        val assigned = mapOf(
            "m1" to listOf("Electric Guitar"),
            "m2" to listOf("Worship Leader", "Vocalist")
        )

        val roleResult = TeamDirectoryRules.filterMembers(
            members, assigned, "worship leader", ""
        )
        val positionResult = TeamDirectoryRules.filterMembers(
            members, assigned, "guitar", ""
        )

        assertEquals(listOf("m2"), roleResult.map { it.memberId })
        assertEquals(listOf("m1"), positionResult.map { it.memberId })
    }

    @Test
    fun positionFilterAndSearchAreAppliedTogether() {
        val members = listOf(
            ServiceMemberOption("m1", "u1", "Aaron Dela Cruz", "team_member"),
            ServiceMemberOption("m2", "u2", "Bea Santos", "worship_leader"),
            ServiceMemberOption("m3", "u3", "Carlo Reyes", "team_member")
        )
        val assigned = mapOf(
            "m1" to listOf("Vocalist"),
            "m2" to listOf("Vocalist", "Keys"),
            "m3" to listOf("Drums")
        )

        val result = TeamDirectoryRules.filterMembers(
            members = members,
            assignedPositionsByMember = assigned,
            searchQuery = "bea",
            selectedPosition = "Vocalist"
        )

        assertEquals(listOf("m2"), result.map { it.memberId })
    }

    @Test
    fun emptyDirectoryFiltersReturnAllMembersSortedByName() {
        val members = listOf(
            ServiceMemberOption("m2", "u2", "Bea Santos", "team_member"),
            ServiceMemberOption("m1", "u1", "Aaron Dela Cruz", "admin")
        )

        val result = TeamDirectoryRules.filterMembers(
            members = members,
            assignedPositionsByMember = emptyMap(),
            searchQuery = " ",
            selectedPosition = ""
        )

        assertEquals(listOf("m1", "m2"), result.map { it.memberId })
    }

}
