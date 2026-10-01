package de.bewerbo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What EVERY step of the flow says while the application it belongs to is not here.
 *
 * [ApplicationLoadingTest] and [ApplicationFetchFailureTest] are the Bewerbung screen's halves of
 * the same null, and the rail they fixed marks all three steps done through both waits — rightly:
 * the Stellenanzeige the letter was written against and the Abgleich it was written out of arrive
 * with it. But a step marked done is a step the user taps, and the two behind the letter had
 * nothing of their own to say: `openApplication()` clears the Abgleich before it asks for it, so
 * "Abgleich" opened on
 *
 *     NOCH KEINE ANZEIGE EINGELESEN
 *     Fügen Sie zuerst auf dem Bildschirm »Stellenanzeige« eine Anzeige ein.
 *
 * — the very sentence about a finished step the loading notice was written to replace, one screen
 * over. The Stellenanzeige step, which is reachable at all times, showed the advert of the
 * application BEFORE this one, because that null is the one openApplication does not clear.
 *
 * Taking the tap away was not the answer and is undone here: it left a check the user could not
 * follow. The three steps say which wait they are in instead, with one component between them, so
 * this reads the component once and then each screen's handover to it — the way
 * [ApplicationLoadingTest] reads the screen rather than only describing it in a comment.
 */
class FlowStepWaitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, none for a top-level declaration. The same reading [ApplicationLoadingTest] does.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun applicationWait(): String =
        body(source("ui/components/DomainComponents.kt"), "fun ApplicationWait(", "")

    @Test
    fun `the wait is one component, and it says which of the two waits it is`() {
        val wait = applicationWait()

        val loading = wait.indexOf("application_loading_title")
        val unreachable = wait.indexOf("application_unreachable_title")
        assertTrue("The wait no longer says that the application is being fetched", loading >= 0)
        assertTrue("The wait no longer says that it never arrived", unreachable >= 0)
        assertTrue(
            "The fetch that is running NOW has to be answered before the one that failed: a retry " +
                "offered during a fetch is a button the user cannot press, and the attempt before " +
                "is history. The same order the Bewerbung screen read these two in:\n" + wait,
            loading < unreachable,
        )
        assertTrue(
            "Both sentences are about the application and neither is a new string: the three steps " +
                "draw the same two Callouts about one fetch, and a second wording for it would be " +
                "four more strings in four locales saying what these already say:\n" + wait,
            !wait.contains("match_loading") && !wait.contains("posting_loading"),
        )
    }

    @Test
    fun `each step tags the wait with its own name, so a run can say where it is standing`() {
        val wait = applicationWait()

        for (tag in listOf("{tagPrefix}_loading", "{tagPrefix}_unreachable", "{tagPrefix}_btn_retry")) {
            assertTrue(
                "The three steps draw this in turn, so the tags have to be built from the prefix " +
                    "the screen hands over — one fixed id would make flow_abgleich's wait " +
                    "indistinguishable from flow_bewerbung's:\n" + wait,
                wait.contains(tag),
            )
        }
    }

    @Test
    fun `every step of the flow answers the wait before its own empty state`() {
        // Screen, the handover it makes, and the empty state the wait has to be read BEFORE: taken
        // the other way round each of these is the defect again, this time one screen further along.
        val steps = listOf(
            Triple("ui/screens/PostingScreen.kt", """ApplicationWait(state, "posting"""", "posting_bring_in"),
            Triple("ui/screens/MatchScreen.kt", """ApplicationWait(state, "match"""", "match_none_title"),
            Triple(
                "ui/screens/ApplicationScreen.kt",
                """ApplicationWait(state, "application"""",
                "application_none_title",
            ),
        )

        for ((path, handover, empty) in steps) {
            val screen = source(path)
            val said = screen.indexOf(handover)
            val none = screen.indexOf(empty)

            assertTrue("$path does not say which wait it is in at all:\n$handover", said >= 0)
            assertTrue("$path no longer has an empty state at all", none >= 0)
            assertTrue(
                "$path reads the wait AFTER its empty state, which is the empty state again: the " +
                    "Abgleich asking for an advert that is being fetched while it asks is the " +
                    "whole card",
                said < none,
            )
        }
    }

    @Test
    fun `the Stellenanzeige step keeps its way in once the fetch has ended`() {
        val screen = source("ui/screens/PostingScreen.kt")

        assertEquals(
            "This step answers the fetch that is RUNNING and nothing else. It is the screen a user " +
                "whose application never arrived begins the next one on, and the form is the only " +
                "way in there — hidden behind a failure that nothing on this screen clears, the " +
                "step is a dead end. The retry lives where the letter does.",
            0,
            Regex("""state\.isAwaitingApplication""").findAll(screen).count(),
        )
        assertTrue(
            "and it does have to answer the running fetch: what stood here then was the advert of " +
                "the application BEFORE this one — openApplication() does not clear the posting — " +
                "or the form asking for the advert that was arriving as it asked:\n" + screen,
            screen.contains("state.isOpeningApplication"),
        )
    }

    @Test
    fun `a step the rail marks done can be opened`() {
        val step = body(source("MainActivity.kt"), "val reachable = step ==", "")

        assertTrue(
            "Done and reachable are one thing: a check beside a step that does nothing is the rail " +
                "saying two opposite things in one frame, and it is what the tap was reduced to " +
                "while the steps had nothing to show. They have something to show now — the wait " +
                "itself:\n" + step,
            step.contains("step == FlowStep.entries.first() || isDone"),
        )
        assertTrue(
            "and nothing about the wait may be read here again. The rail decides where the user " +
                "may go; what is there to see is the screen's own answer, and a second guard here " +
                "is the muted check back:\n" + step,
            !step.contains("isOpeningApplication") && !step.contains("unfetchedApplication"),
        )
    }
}
