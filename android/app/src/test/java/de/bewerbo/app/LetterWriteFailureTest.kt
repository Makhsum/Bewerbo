package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Bewerbung screen says when the Anschreiben the user asked for could not be written.
 *
 * [LetterWritingTest] is the other half of the same call: the Abgleich's "Anschreiben schreiben"
 * starts the write and navigates in the same onClick, and the many seconds it runs were spent on the
 * empty state. A write that does not answer at all — no network, a server out of reach — leaves the
 * same null standing, and leaves it standing for good: the screen fell back to "Noch kein
 * Anschreiben. Führen Sie zuerst den Abgleich durch." under a rail that marked that very Abgleich
 * done, the snackbar that named the reason was gone within seconds, and nothing on the screen
 * offered a second attempt.
 *
 * Read from the source the way [ApplicationFetchFailureTest] reads the fetch's own failure, because
 * it is the same shape: the failure is written into the state, the screen says it and offers the one
 * way on, and nothing that starts a new application is mistaken for it. What differs is what the
 * retry needs — the TONE, because `generateLetter` is the call that has to be made again — and that
 * the Abgleich behind this failure is untouched, so the way on is the write and not the step before.
 */
class LetterWriteFailureTest {

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

    private fun generateLetter(): String =
        body(viewModel(), "fun generateLetter(tone: String) = launch(", "    ")

    @Test
    fun `a write that did not answer is written down against the tone it was asked for`() {
        val generate = generateLetter()

        assertTrue(
            "The tone and not a flag: asking again is the one thing the user can do about this, " +
                "the call that has to be made again is generateLetter, and the screen offering the " +
                "second attempt has no tone selector of its own to read it off:\n" + generate,
            generate.contains("unwrittenLetterTone = tone"),
        )
        assertTrue(
            "The failure still has to travel on. launch() is what names the reason in the " +
                "snackbar and what ends a session the server no longer knows — swallowed here, " +
                "both stop happening:\n" + generate,
            generate.contains("throw failure"),
        )
        assertTrue(
            "and it has to be forgotten the moment a letter does arrive, or the Anschreiben on " +
                "screen goes on carrying a failure that is over:\n" + generate,
            generate.contains("unwrittenLetterTone = null"),
        )

        val caught = generate.indexOf("} catch (failure: Throwable) {")
        assertTrue("generateLetter no longer guards the call that writes the letter", caught >= 0)
        for (after in listOf("runChecks(", "refreshDerived()")) {
            assertTrue(
                "$after belongs OUTSIDE the guard: the Textprüfung, the Maschinenlesbarkeit and " +
                    "the Übersicht are read beside a letter that HAS been written, and a check " +
                    "that could not be run is not a letter that was never written:\n" + generate,
                generate.indexOf(after) > caught,
            )
        }
    }

    @Test
    fun `a new application is not a letter that could not be written`() {
        val declarations = listOf(
            "fun parsePosting(text: String) = launch(",
            "fun clearPosting()",
            "fun openApplication(applicationId: String) = launch(",
        )
        for (declaration in declarations) {
            val fresh = body(viewModel(), declaration, "    ")

            assertTrue(
                "$declaration drops the letter that was written before it, so what it leaves " +
                    "behind is an application with no Anschreiben yet — the empty state, not a " +
                    "failure. A failure left standing here is the old sentence back under a new " +
                    "heading, and on the fetch's own screen it would hide the fetch:\n" + fresh,
                fresh.contains("unwrittenLetterTone = null"),
            )
        }
    }

    @Test
    fun `the screen says the write failed instead of the empty state, and offers the second attempt`() {
        val branch = body(source("ui/screens/ApplicationScreen.kt"), "if (application == null) {", "        ")

        val failed = branch.indexOf("application_unwritten_title")
        val writing = branch.indexOf("application_writing_title")
        val waiting = branch.indexOf("ApplicationWait(state, \"application\"")
        val none = branch.indexOf("application_none_title")
        assertTrue("The screen does not say that the Anschreiben could not be written", failed >= 0)
        assertTrue("The screen no longer says that the Anschreiben is being written", writing >= 0)
        assertTrue("The screen no longer says which wait for the application it is in", waiting >= 0)
        assertTrue("The screen no longer has an empty state at all", none >= 0)
        assertTrue(
            "The failure has to be answered BEFORE the empty state: taken the other way round, " +
                "the null is read as \"there is no Anschreiben\" again and this card is undone",
            failed < none,
        )
        assertTrue(
            "and AFTER the write that is still running: the same null means both, and a write in " +
                "flight is what the screen is doing while an attempt that failed is history",
            writing < failed,
        )
        assertTrue(
            "and before the fetch's own waits, for the reason the running write is: " +
                "openApplication() forgets this failure as it starts, so a failure recorded here " +
                "is the latest thing that happened to this screen",
            failed < waiting,
        )
        assertTrue(
            "The failure needs a tag of its own, or a run cannot tell it from the three Callouts " +
                "beside it:\n" + branch,
            branch.contains("testTag(\"application_unwritten\")"),
        )
        assertTrue(
            "Asking for the letter again from this screen is half of what the card asks for, and " +
                "it asks with the tone the failure wrote down — not with whatever a selector on " +
                "another screen last held:\n" + branch,
            branch.contains("testTag(\"application_btn_write_retry\")") &&
                branch.contains("viewModel.generateLetter(unwrittenTone)"),
        )
    }

    @Test
    fun `the second attempt is the write itself, so the finished Abgleich is not asked for again`() {
        val generate = generateLetter()

        // [LetterWritingTest] reads the same two lines for the wait; here they are what makes the
        // retry a WRITE rather than a walk back through step 2. The rail marks the Abgleich done off
        // `match != null` and offers a tap to a step that is done, so with the match still in hand
        // the user can reach it — they simply do not have to.
        assertTrue(
            "The Abgleich the letter is written out of has to survive a write that failed, or the " +
                "second attempt has nothing to write against and the rail sends the user back to " +
                "the step the card says they must not redo:\n" + generate,
            !generate.contains("match = null"),
        )
        assertTrue(
            "The Stellenanzeige behind it for the same reason: generateLetter reads it to make the " +
                "call at all",
            !generate.contains("posting = null"),
        )

        val rail = source("MainActivity.kt")
        assertTrue(
            "The rail must NOT read this failure the way it reads unfetchedApplication. That one " +
                "stands over a state openApplication() had cleared, so its steps were done and " +
                "EMPTY; this one leaves the posting and the match in hand, so every step behind it " +
                "has something to show, and taking the tap away would strand the second attempt as " +
                "the only way on.",
            !rail.contains("unwrittenLetterTone"),
        )
    }

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [LetterWritingTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `every interface language says that the Anschreiben could not be written`() {
        val tags = UI_LANGUAGES.map { it.first }
        val keys = listOf(
            "application_unwritten_title", "application_unwritten_body", "application_unwritten_retry",
        )

        for (key in keys) {
            val values = tags.associateWith { stringValue(it, key) }

            val silent = tags.filter { values.getValue(it).isBlank() }
            assertTrue(
                "$key stands in place of the empty state once the write has failed, so a language " +
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
                "yet\" over a finished Abgleich is the whole defect.",
            tags.none {
                stringValue(it, "application_unwritten_title") == stringValue(it, "application_none_title")
            },
        )
        assertTrue(
            "nor as the failure of the FETCH: one says that a letter on the server did not reach " +
                "this phone, the other that no letter was written at all, and the second attempt " +
                "each one offers is a different call.",
            tags.none {
                stringValue(it, "application_unwritten_title") == stringValue(it, "application_unreachable_title")
            },
        )
    }
}
