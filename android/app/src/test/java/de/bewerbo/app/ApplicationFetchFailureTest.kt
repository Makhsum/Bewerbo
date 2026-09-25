package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Bewerbung screen and the rail above it say when the application never arrived.
 *
 * [ApplicationLoadingTest] is the other half of the same null: `openApplication()` clears the
 * application before its three calls, and a wait was read as "the user has no Anschreiben". A call
 * that does not answer at all — no network, a server out of reach — leaves those same nulls
 * standing, and leaves them standing for good: the screen fell back to "Noch kein Anschreiben.
 * Führen Sie zuerst den Abgleich durch." under a rail that marked the finished Abgleich as still to
 * do, and the snackbar that named the real reason was gone within seconds.
 *
 * Read from the source the way [PreviewFailureTest] reads the preview's own failure, because it is
 * the same shape: the failure is written into the state, the screen says it and offers the one way
 * back, and nothing that starts a new application is mistaken for it.
 */
class ApplicationFetchFailureTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    private fun viewModel(): String = source("data/AppViewModel.kt")

    /// A declaration and what follows it up to the line that closes it — the same reading
    /// [PreviewFailureTest] does, and `indent` is the closing brace's own.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun openApplication(): String =
        body(viewModel(), "fun openApplication(applicationId: String) = launch(", "    ")

    @Test
    fun `a fetch that did not answer is written down against the application it was for`() {
        val open = openApplication()

        assertTrue(
            "The id and not a flag: asking again is the one thing the user can do about this, and " +
                "the retry has to know WHICH application to ask for:\n" + open,
            open.contains("unfetchedApplication = applicationId"),
        )
        assertTrue(
            "The failure still has to travel on. launch() is what names the reason in the " +
                "snackbar and what ends a session the server no longer knows — swallowed here, " +
                "both stop happening:\n" + open,
            open.contains("throw failure"),
        )
        assertTrue(
            "and the attempt before has to be forgotten where the other nulls are cleared, or a " +
                "fetch that is running still says the last one failed:\n" + open,
            open.contains("unfetchedApplication = null"),
        )

        val caught = open.indexOf("} catch (failure: Throwable) {")
        val checks = open.indexOf("runChecks(")
        assertTrue("openApplication no longer guards its three calls at all", caught >= 0)
        assertTrue(
            "runChecks() belongs OUTSIDE the guard: the Textprüfung and the Maschinenlesbarkeit " +
                "are drawn beside a letter that HAS arrived, and a check that could not be run is " +
                "not an application that never came:\n" + open,
            checks > caught,
        )
    }

    @Test
    fun `a new posting is not an application that could not be fetched`() {
        for (declaration in listOf("fun parsePosting(text: String) = launch(", "fun clearPosting()")) {
            val fresh = body(viewModel(), declaration, "    ")

            assertTrue(
                "$declaration drops the letter that was written against the posting before it, so " +
                    "what it leaves behind is an application that does not exist yet — the empty " +
                    "state, not a failure. A failure left standing here is the old sentence back " +
                    "under a new heading:\n" + fresh,
                fresh.contains("unfetchedApplication = null"),
            )
        }
    }

    @Test
    fun `the screen says the failure instead of the empty state, and offers the way back`() {
        val branch = body(source("ui/screens/ApplicationScreen.kt"), "if (application == null) {", "        ")

        val failed = branch.indexOf("application_unreachable_title")
        val none = branch.indexOf("application_none_title")
        assertTrue("The screen does not say that the application could not be fetched", failed >= 0)
        assertTrue("The screen no longer has an empty state at all", none >= 0)
        assertTrue(
            "The failure has to be answered BEFORE the empty state: taken the other way round, " +
                "the null is read as \"there is no Anschreiben\" again and this card is undone",
            failed < none,
        )
        assertTrue(
            "The failure needs a tag of its own, or a run cannot tell it from the two Callouts " +
                "beside it:\n" + branch,
            branch.contains("""testTag("application_unreachable")"""),
        )
        assertTrue(
            "Trying again from this screen is half of what the card asks for, and the retry asks " +
                "for the application the failure named — not for whatever the flow last held:\n" +
                branch,
            branch.contains("""testTag("application_btn_retry")""") &&
                branch.contains("viewModel.openApplication(unfetched)"),
        )
    }

    @Test
    fun `the rail neither sends the user back to a finished Abgleich nor offers an empty one`() {
        val main = source("MainActivity.kt")
        val hasProduced = body(main, "private fun AppState.hasProduced(", "")

        val waiting = hasProduced.indexOf("unfetchedApplication != null")
        val match = hasProduced.indexOf("FlowStep.Match -> match != null")
        assertTrue(
            "The rail reads posting, match and application straight out of the state, and a fetch " +
                "that failed leaves them cleared — read straight, the Abgleich the letter was " +
                "written out of is drawn as still to do:\n" + hasProduced,
            waiting >= 0,
        )
        assertTrue("The rail no longer reads the Abgleich at all", match >= 0)
        assertTrue(
            "The failure has to be answered BEFORE the three nulls are read, for the reason the " +
                "wait beside it is",
            waiting < match,
        )

        val step = body(main, "val reachable = step ==", "")
        assertTrue(
            "A step marked done behind an application that is on its way or that never arrived " +
                "has NOTHING to show — openApplication() cleared the Abgleich it leads to. Offered " +
                "as a tap, it opens on \"Noch keine Anzeige eingelesen\", which is the sentence " +
                "about a finished step this rail exists to stop saying:\n" + step,
            step.contains("!state.isOpeningApplication") && step.contains("state.unfetchedApplication == null"),
        )
    }

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [ApplicationLoadingTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `every interface language says that the application could not be fetched`() {
        val tags = UI_LANGUAGES.map { it.first }

        for (key in listOf("application_unreachable_title", "application_unreachable_body", "application_retry")) {
            val values = tags.associateWith { stringValue(it, key) }

            val silent = tags.filter { values.getValue(it).isBlank() }
            assertTrue(
                "$key stands in place of the empty state once the fetch has failed, so a language " +
                    "without it falls back to the English one: " + silent.joinToString(", "),
                silent.isEmpty(),
            )
            assertEquals(
                "Each language needs its own sentence for $key — an English one under a Russian " +
                    "interface is the complaint the language picker exists to answer:\n" + values,
                tags.size,
                values.values.distinct().size,
            )
        }

        assertTrue(
            "The failure must not read as the empty state it replaced: \"there is no Anschreiben " +
                "yet\" over a letter that exists is the whole defect.",
            UI_LANGUAGES.map { it.first }.none {
                stringValue(it, "application_unreachable_title") == stringValue(it, "application_none_title")
            },
        )
    }
}
