package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Bewerbung screen and the rail above it say while the Anschreiben is being written.
 *
 * [ApplicationLoadingTest] and [ApplicationFetchFailureTest] are the other readings of the same
 * null, and this is the longest one: the Abgleich's "Anschreiben schreiben" starts the call and
 * navigates in the same onClick, so the screen is reached with the letter asked for and not there
 * yet. Where a model writes it that is many seconds, and every one of them was spent on the empty
 * state — "Noch kein Anschreiben. Führen Sie zuerst den Abgleich durch." — under a rail that marked
 * that very Abgleich done. Two opposite statements in one frame, and the sentence the user is left
 * reading sends them back to a step they had just finished.
 *
 * Read from the source the way the two tests above read theirs, because it is the same shape: the
 * call names itself, the screen tells the wait from the empty state and from the failure, and the
 * step the letter is written out of stays in hand behind it.
 */
class LetterWritingTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    private fun viewModel(): String = source("data/AppViewModel.kt")

    /// A declaration and what follows it up to the line that closes it — the same reading
    /// [ApplicationFetchFailureTest] does, and `indent` is the closing brace's own.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `the call that writes the Anschreiben is named once, not spelled out at both ends`() {
        val viewModel = viewModel()

        for (declaration in listOf("fun generateLetter(tone: String)", "fun regenerateLetter(tone: String)")) {
            assertTrue(
                "$declaration has to carry the NAMED busy state — the screen's own reading is off " +
                    "exactly that constant, and two literals a thousand lines apart drift:\n" +
                    body(viewModel, declaration, "    ").lines().first(),
                viewModel.contains("$declaration = launch(LETTER)"),
            )
        }
        assertEquals(
            "Nothing may pass \"letter\" to launch() as a literal any more",
            0,
            Regex("""launch\("letter"\)""").findAll(viewModel).count(),
        )
        assertTrue(
            "isWritingLetter is the one place that says what this null means, and it has to read " +
                "both halves: no Anschreiben HERE, and the call that will write one still running. " +
                "The application being null is also what tells generateLetter from " +
                "regenerateLetter, which carries the same name over a letter that is on screen:\n" +
                viewModel,
            viewModel.contains("get() = application == null && busy == AppViewModel.LETTER"),
        )
    }

    @Test
    fun `the screen says the Anschreiben is being written instead of that there is none`() {
        val branch = body(source("ui/screens/ApplicationScreen.kt"), "if (application == null) {", "        ")

        assertEquals(
            "The screen reads this wait once, in the one branch that chooses between the Callouts " +
                "— a second reading is a second answer to the same question:\n" + branch,
            1,
            Regex("""state\.isWritingLetter""").findAll(branch).count(),
        )

        val writing = branch.indexOf("application_writing_title")
        val failed = branch.indexOf("application_unreachable_title")
        val none = branch.indexOf("application_none_title")
        assertTrue("The screen does not say that the Anschreiben is being written", writing >= 0)
        assertTrue("The screen no longer says that the application could not be fetched", failed >= 0)
        assertTrue("The screen no longer has an empty state at all", none >= 0)
        assertTrue(
            "The write has to be answered BEFORE the empty state: the empty state is what stands " +
                "when nothing was asked for, and taken first it is this card's defect again",
            writing < none,
        )
        assertTrue(
            "and before the failure too: a write that is running now is what the screen is doing, " +
                "an earlier fetch that failed is history — and its retry is disabled while a call " +
                "is out, so read that way round the longest wait in the app offers a dead button",
            writing < failed,
        )
        assertTrue(
            "The write needs a tag of its own, or a run cannot tell it from the three Callouts " +
                "beside it:\n" + branch,
            branch.contains("""testTag("application_writing")"""),
        )
    }

    @Test
    fun `the Abgleich the letter is written out of stays in hand while it is written`() {
        val generate = body(viewModel(), "fun generateLetter(tone: String)", "    ")

        assertTrue(
            "The rail reads the Abgleich straight out of the state — `FlowStep.Match -> match != " +
                "null` — and marks it done for as long as it is there. Cleared here, the step the " +
                "letter is being written out of would be drawn as still to do beside a screen " +
                "that says it is writing, which is the contradiction this card is about:\n" +
                generate,
            !generate.contains("match = null"),
        )
        assertTrue(
            "The posting behind it for the same reason: the rail's first step reads it the same way",
            !generate.contains("posting = null"),
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
    fun `every interface language says that the Anschreiben is being written`() {
        val tags = UI_LANGUAGES.map { it.first }

        for (key in listOf("application_writing_title", "application_writing_body")) {
            val values = tags.associateWith { stringValue(it, key) }

            val silent = tags.filter { values.getValue(it).isBlank() }
            assertTrue(
                "$key stands in place of the empty state for the whole of the longest wait in the " +
                    "app, so a language without it falls back to the English one: " +
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

        assertTrue(
            "The write must not read as the empty state it replaced: \"there is no Anschreiben " +
                "yet\" over one that is being written is the whole defect.",
            tags.none {
                stringValue(it, "application_writing_title") == stringValue(it, "application_none_title")
            },
        )
    }
}
