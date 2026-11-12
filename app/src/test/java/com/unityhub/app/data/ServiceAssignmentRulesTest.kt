package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceAssignmentRulesTest {
    @Test
    fun trimsAndAcceptsValidRoleLabels() {
        assertEquals("Electric Guitar", ServiceAssignmentRules.normalizeRoleLabel("  Electric Guitar  "))
    }

    @Test
    fun rejectsRoleLabelsOutsideAllowedLength() {
        assertTrue(runCatching { ServiceAssignmentRules.normalizeRoleLabel("A") }.isFailure)
        assertTrue(runCatching { ServiceAssignmentRules.normalizeRoleLabel("R".repeat(81)) }.isFailure)
    }

    @Test
    fun normalizesOptionalResponseNotesAndAcceptsTheMaximumLength() {
        assertEquals("I have a schedule conflict.", ServiceAssignmentRules.normalizeResponseNote("  I have a schedule conflict.  "))
        assertEquals("", ServiceAssignmentRules.normalizeResponseNote(""))
        assertEquals(300, ServiceAssignmentRules.normalizeResponseNote("N".repeat(300)).length)
    }

    @Test
    fun rejectsResponseNotesLongerThanTheMaximum() {
        assertTrue(runCatching {
            ServiceAssignmentRules.normalizeResponseNote("N".repeat(301))
        }.isFailure)
    }

    @Test
    fun acceptsOnlyMemberResponseValues() {
        assertTrue(ServiceAssignmentRules.isValidResponse("confirmed"))
        assertTrue(ServiceAssignmentRules.isValidResponse("declined"))
        assertFalse(ServiceAssignmentRules.isValidResponse("pending"))
    }

    @Test
    fun completedOrCancelledServicesDoNotAcceptResponses() {
        assertTrue(ServiceAssignmentRules.canRespondToService("draft"))
        assertTrue(ServiceAssignmentRules.canRespondToService("published"))
        assertFalse(ServiceAssignmentRules.canRespondToService("completed"))
        assertFalse(ServiceAssignmentRules.canRespondToService("cancelled"))
    }
}
