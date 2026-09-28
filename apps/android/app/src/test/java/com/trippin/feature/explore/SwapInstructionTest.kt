package com.trippin.feature.explore

import com.trippin.core.network.ExplorePlaceDto
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapInstructionTest {

    @Test
    fun `the instruction names both places, pins the new one and keeps the rest of the plan`() {
        val place = ExplorePlaceDto(
            id = "osm_node_42", name = "Fabrica Coffee", kind = "coffee",
            latitude = 38.71, longitude = -9.14, cuisine = null
        )
        val text = swapInstruction("Castle walk", place)
        assertTrue(text.contains("\"Castle walk\""))
        assertTrue(text.contains("\"Fabrica Coffee\""))
        assertTrue(text.contains("osm_node_42"))
        assertTrue(text.contains("38.71, -9.14"))
        assertTrue(text.contains("same time slot"))
    }
}
