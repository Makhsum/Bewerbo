package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where "Weitere Bewerbung beginnen" puts the user.
 *
 * [NavigationShapeTest] guards the SHAPE of the flow — that its three steps are steps and not places
 * on the bar. This guards where the flow is entered when the user asks to begin another application,
 * which the shape cannot say.
 *
 * The Übersicht asked for the first step all along. It was the navigation that overrode it:
 * `openFromOverview` restores a saved back stack, and the flow's saved stack ends on the Bewerbung,
 * so beginning another application answered with the LAST step of the path — under the empty state
 * telling the user to go and do the Abgleich first, on a rail that showed SCHRITT 3 VON 3. A comment
 * saying "not this one" is a rule the next link out of the Übersicht quietly breaks, so it gets a
 * gate, the same way [NavigationShapeTest] gates the bar.
 *
 * Read from the source the way [LetterWriteFailureTest] and [ApplicationFetchFailureTest] read
 * theirs: these are private extensions on NavHostController inside an Activity, so there is nothing
 * a unit test can call.
 */
class BeginAnotherApplicationTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    private fun mainActivity(): String = source("MainActivity.kt")

    /// A declaration and what follows it up to the line that closes it — the same reading
    /// [LetterWriteFailureTest] does, and `indent` is the closing brace's own.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun beginFlow(): String =
        body(mainActivity(), "private fun NavHostController.beginFlow()", "")

    @Test
    fun `beginning another application does not restore the path the last one was walked along`() {
        val begin = beginFlow()

        assertTrue(
            "restoreState brings the saved flow stack back WHOLE and leaves the user on its top " +
                "entry, the Bewerbung. That is what the bar wants and the opposite of what " +
                "beginning another application asks for:\n" + begin,
            !begin.contains("restoreState"),
        )
        assertTrue(
            "and the stack has to be CLEARED, not merely ignored: clearPosting() has just dropped " +
                "the posting, the match and the letter it was walked for, so it is waiting under " +
                "the first step's own id for the next navigation that restores — which is what " +
                "\"Bewerbung fortsetzen\" does:\n" + begin,
            begin.contains("clearBackStack(FlowStep.Posting.route)"),
        )
        assertTrue(
            "The Übersicht still has to be left the way the bar leaves it, or tapping " +
                "\"Übersicht\" lands back on the step instead of the list:\n" + begin,
            begin.contains("popUpTo(graph.startDestinationId) { saveState = true }"),
        )
        assertTrue(
            "and the step it opens is the FIRST one:\n" + begin,
            begin.contains("navigate(FlowStep.Posting.route)"),
        )
    }

    @Test
    fun `the Ubersicht begins another application through that navigation and drops the posting first`() {
        val overview = body(mainActivity(), "onBeginAnother = if (beginning) {", "                            ")

        assertTrue(
            "Beginning another application goes through beginFlow(). openFromOverview() is the " +
                "link out of the Übersicht for a place and for a step being RESUMED, and it is " +
                "what put the user on the Bewerbung here:\n" + overview,
            overview.contains("navController.beginFlow()"),
        )
        assertTrue(
            "and the posting of the application before it goes first, or the next employer's " +
                "first step opens with the last one's advert already in the field:\n" + overview,
            overview.contains("viewModel.clearPosting()"),
        )
    }
}
