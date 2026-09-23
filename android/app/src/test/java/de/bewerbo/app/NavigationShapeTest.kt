package de.bewerbo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar carries places, and producing an application is not one of them.
 *
 * This is the rule the flow was built to establish, and a rule that is only written in a comment is
 * a rule the next destination added to the bar quietly breaks — so it gets a gate, the same way the
 * emoji rule does in [EmojiFreeStringsTest] and the interface languages do in [UiLanguagesTest].
 */
class NavigationShapeTest {

    @Test
    fun `the bar holds the three places and nothing else`() {
        assertEquals(
            listOf("uebersicht", "profil", "mappe"),
            Destination.entries.map { it.route },
        )
    }

    @Test
    fun `no step of the flow is on the bar`() {
        val places = Destination.entries.map { it.route }.toSet()
        val posing = FlowStep.entries.filter { it.route in places }

        assertTrue(
            "These steps of the application flow are on the bottom bar, where they pose as places " +
                "the user may visit in any order: ${posing.map { it.route }}",
            posing.isEmpty(),
        )
    }

    @Test
    fun `the flow is the path an application is produced along, in order`() {
        assertEquals(
            listOf("stellenanzeige", "abgleich", "bewerbung"),
            FlowStep.entries.map { it.route },
        )
    }
}
