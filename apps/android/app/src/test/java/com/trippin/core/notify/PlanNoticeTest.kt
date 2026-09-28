package com.trippin.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanNoticeTest {

    @Test
    fun `still building means keep waiting`() {
        assertNull(planOutcomeOf("GENERATING"))
        assertNull(planOutcomeOf("DRAFT"))
    }

    @Test
    fun `a finished build is ready and a failed one says so`() {
        assertEquals(PlanOutcome.READY, planOutcomeOf("READY"))
        assertEquals(PlanOutcome.FAILED, planOutcomeOf("FAILED"))
    }

    @Test
    fun `the notice names the city, not the whole address`() {
        assertEquals("Your Lisbon plan is ready", planNotice(PlanOutcome.READY, "Lisbon, Portugal").title)
        assertEquals("The Kyoto plan could not be built", planNotice(PlanOutcome.FAILED, "Kyoto").title)
        assertEquals("Your plan is ready", planNotice(PlanOutcome.READY, " ").title)
        assertEquals("Your plan is ready", planNotice(PlanOutcome.READY, null).title)
    }
}
