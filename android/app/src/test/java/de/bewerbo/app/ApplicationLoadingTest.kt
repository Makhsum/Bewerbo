package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Bewerbung screen and the rail above it say while an application is on its way.
 *
 * `openApplication()` clears the application before its three calls, so that one employer's
 * Anschreiben is never shown under another's name. The screen read that same null as "the user has
 * no application" and answered with its empty state — "Noch kein Anschreiben. Führen Sie zuerst den
 * Abgleich durch." — while the rail drew the Abgleich as still to do. Both stood for about a second
 * on the last screen before sending, over a letter that existed and a step that was finished.
 *
 * One null with two meanings is the whole defect, so the three places that follow from it are
 * checked here rather than only described in a comment, the way [ScanReplacementTest] checks the two
 * halves of a replacement: the fetch names itself, the screen tells the wait from the empty state,
 * and the rail keeps the path behind the application it is fetching.
 */
class ApplicationLoadingTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, none for a top-level declaration and as deep as the block for one inside it. The
    /// same reading [ScanReplacementTest] does.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `the fetch behind opening an application is named once, not spelled out at both ends`() {
        val viewModel = source("data/AppViewModel.kt")

        assertTrue(
            "openApplication() has to carry the NAMED busy state — the screen's own reading is off " +
                "exactly that constant, and two literals a thousand lines apart drift:\n" +
                body(viewModel, "fun openApplication(", "    ").lines().first(),
            viewModel.contains("fun openApplication(applicationId: String) = launch(APPLICATION)"),
        )
        assertEquals(
            "Nothing may pass \"application\" to launch() as a literal any more",
            0,
            Regex("""launch\("application"\)""").findAll(viewModel).count(),
        )
        assertTrue(
            "isOpeningApplication is the one place that says what the null means, and it has to " +
                "read both halves: no application HERE, and the fetch that will bring one still " +
                "running. Either half alone answers a different question:\n" + viewModel,
            viewModel.contains("get() = application == null && busy == AppViewModel.APPLICATION"),
        )
    }

    @Test
    fun `the Bewerbung screen keeps the empty state for where there is really no application`() {
        val branch = body(source("ui/screens/ApplicationScreen.kt"), "if (application == null) {", "        ")

        assertEquals(
            "The screen reads the wait once, into the one branch that chooses between the two " +
                "Callouts — a second reading is a second answer to the same question:\n" + branch,
            1,
            Regex("""state\.isOpeningApplication""").findAll(branch).count(),
        )

        val waiting = branch.indexOf("application_loading_title")
        val none = branch.indexOf("application_none_title")
        assertTrue("The screen no longer says that the application is being fetched", waiting >= 0)
        assertTrue("The screen no longer has an empty state at all", none >= 0)
        assertTrue(
            "The wait has to be the FIRST of the two: the empty state is what stands when the " +
                "fetch is not running, and taken first it is the defect again",
            waiting < none,
        )
        assertTrue(
            "Both Callouts need their own testTag, because telling them apart is the whole point " +
                "of this branch and a driver can only do it by id:\n" + branch,
            branch.contains("""testTag("application_loading")""") &&
                branch.contains("""testTag("application_none")"""),
        )
    }

    @Test
    fun `the rail does not draw a finished Abgleich as still to do during the fetch`() {
        val hasProduced = body(source("MainActivity.kt"), "private fun AppState.hasProduced(", "")

        val waiting = hasProduced.indexOf("isOpeningApplication")
        val match = hasProduced.indexOf("FlowStep.Match -> match != null")
        assertTrue(
            "The rail reads posting, match and application straight out of the state, and " +
                "openApplication() clears them before it fetches them — so it has to know about " +
                "that wait or it marks the Abgleich behind the letter as outstanding:\n" +
                hasProduced,
            waiting >= 0,
        )
        assertTrue("The rail no longer reads the Abgleich at all", match >= 0)
        assertTrue(
            "The wait has to be answered BEFORE the three nulls are read: whichever branch below " +
                "it matched would report the step the fetch is about to fill as not done",
            waiting < match,
        )
    }

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [ScanReplacementTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `every interface language says that the application is being opened`() {
        val tags = UI_LANGUAGES.map { it.first }

        for (key in listOf("application_loading_title", "application_loading_body")) {
            val values = tags.associateWith { stringValue(it, key) }

            val silent = tags.filter { values.getValue(it).isBlank() }
            assertTrue(
                "This is what stands in place of the empty state for as long as the fetch takes, " +
                    "so a language without $key falls back to the English one: " +
                    silent.joinToString(", "),
                silent.isEmpty(),
            )
            assertEquals(
                "Each language needs its own sentence for $key — an English one under a Russian " +
                    "interface is the complaint the language picker exists to answer:\n" + values,
                tags.size,
                values.values.distinct().size,
            )
        }
    }
}
