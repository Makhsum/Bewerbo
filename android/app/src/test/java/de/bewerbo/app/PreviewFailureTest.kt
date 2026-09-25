package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Bewerbungsmappe's preview does when it ends up with no pages.
 *
 * Two holes were left when the drawing moved off the main thread — see [PreviewResponsiveTest], the
 * card that moved it. Every render was downloaded into the one cache file "vorschau.pdf", so a
 * preview started while the one before was still being rasterised truncated the file that render
 * was reading; and only the DOWNLOAD was guarded, so a Mappe that arrived and could not be drawn
 * left the screen standing on "the pages are being made" with no page and no way to ask again,
 * because the retry button is composed under a failure.
 *
 * Read from the source the same way [PreviewResponsiveTest] reads its own half: each render writes
 * a file of its own, a drawing that yields nothing is a failure the state carries, and the screen
 * says which of the two happened in every interface language.
 */
class PreviewFailureTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    private fun viewModel(): String = source("data/AppViewModel.kt")

    /// A declaration and what follows it up to the line that closes it — the same reading
    /// [PreviewResponsiveTest] does, and `indent` is the closing brace's own.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun refreshPreview(): String =
        body(viewModel(), "fun refreshPreview(parts: String) = launch(", "    ")

    @Test
    fun `every render of the preview is downloaded into a file of its own`() {
        val api = source("data/BewerboApi.kt")

        assertTrue(
            "previewFile() has to name the render it belongs to. One name for all of them is one " +
                "file two overlapping renders write and read at the same time, and writeBytes() " +
                "truncates before it writes:\n" + api.substringAfter("fun Context.previewFile").take(200),
            api.contains("fun Context.previewFile(render: Int)"),
        )
        assertTrue(
            "and the name has to CARRY that number, or it is the same file under a new signature",
            api.contains("""File(previewDir(), "vorschau-${'$'}render.pdf")"""),
        )
        assertTrue(
            "The render asks for the file with a number nothing else has had — a counter that " +
                "goes up, not the state of the screen:\n" + refreshPreview(),
            refreshPreview().contains("previewFile(++previewRenders)"),
        )
        assertTrue(
            "A cache file per render fills the cache unless each one is removed when its render " +
                "is over — and over means whichever way it ended:\n" + refreshPreview(),
            refreshPreview().contains("} finally {") && refreshPreview().contains("file.delete()"),
        )
    }

    @Test
    fun `signing out empties the folder the previews are in`() {
        val forget = body(viewModel(), "private fun forgetTheAccountOnThisDevice()", "    ")

        assertTrue(
            "The previews are cache copies of the Mappe of an account that has just been signed " +
                "out of. Deleting one named file leaves every other render's behind:\n" + forget,
            forget.contains("previewDir().deleteRecursively()"),
        )
    }

    @Test
    fun `a drawing that produced nothing is a failure the screen can say`() {
        val refresh = refreshPreview()

        assertTrue(
            "Rasterising throws on a file it cannot read, and returns no page for one that is " +
                "empty. Neither may travel on as an exception: the snackbar would say Bewerbo " +
                "could not be reached, and the file is on the device:\n" + refresh,
            refresh.contains("}.getOrDefault(emptyList())"),
        )
        assertTrue(
            "No page is no preview, whichever of the two it was, and the screen has to be told " +
                "so — standing on the sentence that says the pages are being made is what this " +
                "card is about:\n" + refresh,
            refresh.contains("if (pages.isEmpty())") && refresh.contains("PREVIEW_NOT_DRAWN"),
        )
        assertTrue(
            "and the download keeps a failure of its own, because the two are not the same " +
                "sentence:\n" + refresh,
            refresh.contains("PREVIEW_NOT_FETCHED"),
        )
    }

    @Test
    fun `a failure of the render before is not said over a render that is still running`() {
        val said = body(viewModel(), "private fun previewFailed(parts: String, reason: String)", "    ")

        assertTrue(
            "The chips can be changed while a render is in flight. Its failure published anyway " +
                "puts the way-back card over a preview that is on its way — the same check the " +
                "finished pages go through:\n" + said,
            said.contains("_state.value.previewParts == parts"),
        )
    }

    @Test
    fun `the screen says which of the two happened and offers the one way back`() {
        val screen = source("ui/screens/ApplicationScreen.kt")

        val failure = screen.substringAfter("if (previewFailure != null) {").substringBefore("} else item {")
        assertTrue("The screen no longer has a branch for a preview with no pages", failure != screen)

        assertTrue(
            "A file that never arrived and one that could not be drawn are two statements:\n" + failure,
            failure.contains("R.string.application_preview_failed_title") &&
                failure.contains("R.string.application_preview_undrawn_title"),
        )
        assertTrue(
            "and each needs a tag of its own, or a run cannot tell which one it is looking at:\n" + failure,
            failure.contains(""""application_preview_undrawn"""") &&
                failure.contains(""""application_preview_failed""""),
        )
        assertTrue(
            "The way back is the same button for both — asking again is all the user can do " +
                "about either:\n" + failure,
            Regex("""testTag\("application_btn_preview_retry"\)""").findAll(failure).count() == 1,
        )
    }

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [PreviewResponsiveTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `every interface language says that the pages could not be drawn`() {
        val tags = UI_LANGUAGES.map { it.first }

        for (key in listOf("application_preview_undrawn_title", "application_preview_undrawn_body")) {
            val values = tags.associateWith { stringValue(it, key) }

            val silent = tags.filter { values.getValue(it).isBlank() }
            assertTrue(
                "$key stands in place of the pages once the drawing has failed, so a language " +
                    "without it shows an empty card: " + silent.joinToString(", "),
                silent.isEmpty(),
            )
            assertEquals(
                "Each language needs its own sentence for $key — an English one under a Russian " +
                    "interface is the complaint the language picker exists to answer:\n" + values,
                tags.size,
                values.values.distinct().size,
            )
            assertTrue(
                "$key must not read as the failed DOWNLOAD's sentence: the two are told apart " +
                    "on the screen and would not be by the reader.",
                tags.none { values.getValue(it) == stringValue(it, key.replace("undrawn", "failed")) },
            )
        }
    }
}
