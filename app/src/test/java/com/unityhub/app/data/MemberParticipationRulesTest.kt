package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberParticipationRulesTest {

    @Test
    fun serviceStatusesHaveClearMemberFacingLabels() {
        assertEquals("Confirmed", MemberParticipationRules.serviceStatusLabel("confirmed"))
        assertEquals("Can't serve", MemberParticipationRules.serviceStatusLabel("declined"))
        assertEquals("Awaiting response", MemberParticipationRules.serviceStatusLabel("pending"))
        assertEquals("Response unavailable", MemberParticipationRules.serviceStatusLabel("unknown"))
    }

    @Test
    fun rehearsalStatusesDistinguishAvailabilityAndAttendance() {
        assertEquals("Available", MemberParticipationRules.rehearsalStatusLabel("available"))
        assertEquals("Can't attend", MemberParticipationRules.rehearsalStatusLabel("unavailable"))
        assertEquals("Attended", MemberParticipationRules.rehearsalStatusLabel("attended"))
        assertEquals("Absent", MemberParticipationRules.rehearsalStatusLabel("absent"))
        assertEquals("Awaiting response", MemberParticipationRules.rehearsalStatusLabel("pending"))
        assertEquals("Not recorded", MemberParticipationRules.rehearsalStatusLabel("unknown"))
    }
}
