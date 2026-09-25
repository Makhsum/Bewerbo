package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the app is doing while the Bewerbungsmappe's preview is being drawn.
 *
 * The preview rasterised every page of the exported file on the main thread, the way opening a
 * stored scan did until [ScanViewerResponsiveTest]'s card was fixed. The Mappe is the longer of the
 * two — Anschreiben, Lebenslauf and Anlagenverzeichnis — so the wait is longer, and it falls on the
 * last screen before the user sends their application: Android offered them the "Bewerbo isn't
 * responding" dialog there of all places.
 *
 * Read from the source the same way [ScanViewerResponsiveTest] reads its own half: the drawing
 * happens off the main thread, the finished pages are dropped when the chips no longer describe
 * them, what stands in their place says the pages are being made, and it says it in every interface
 * language.
 */
class PreviewResponsiveTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, four spaces for a member function and none for a top-level one. The same
    /// reading [ScanViewerResponsiveTest] does.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun viewModel(): String = source("data/AppViewModel.kt")

    private fun refreshPreview(): String =
        body(viewModel(), "fun refreshPreview(parts: String) = launch(", "    ")

    @Test
    fun `the pages of the preview are drawn off the main thread`() {
        val refresh = refreshPreview()

        assertTrue(
            "viewModelScope is Dispatchers.Main, so rasterising has to be handed to another " +
                "dispatcher explicitly. Without that the app stands still for as long as the " +
                "whole Mappe takes to draw:\n" + refresh,
            refresh.contains("withContext(Dispatchers.Default) { renderPdfPages("),
        )
        assertEquals(
            "The preview's pages may be rasterised in ONE place, and that place is the one above. " +
                "A second call somewhere in the view model is a second freeze:\n" + refresh,
            1,
            Regex("""renderPdfPages\(""").findAll(viewModel()).count(),
        )
    }

    @Test
    fun `the state is written where a state change belongs`() {
        val refresh = refreshPreview()

        val drawn = refresh.indexOf("withContext(Dispatchers.Default)")
        val published = refresh.indexOf("previewPages = pages")
        assertTrue("The pages are no longer drawn off the main thread", drawn >= 0)
        assertTrue(
            "The pages reach the state through the value the render returned, not from inside " +
                "the block that draws them — a _state.update moved onto a background dispatcher " +
                "is a second problem behind the first one:\n" + refresh,
            published > drawn,
        )
        assertTrue(
            "The chips can be changed while the pages are still being drawn. The render started " +
                "for the selection before then belongs to nothing, and published anyway it is a " +
                "picture of a file the chips no longer describe:\n" + refresh,
            refresh.contains("_state.value.previewParts == parts"),
        )
    }

    @Test
    fun `the screen says the pages are being made until they are there`() {
        val screen = source("ui/screens/ApplicationScreen.kt")

        val pending = screen.substringAfter("if (previewPages.isEmpty()) {")
        assertTrue("The screen no longer has a branch for pages still on their way", pending != screen)
        assertTrue(
            "That branch is the only thing the user sees for as long as the drawing takes, so it " +
                "has to be the sentence saying the pages are being made:\n" + pending.take(400),
            pending.contains("R.string.application_preview_pending"),
        )
        assertTrue(
            "and it has to be findable, because that is what a run proves the freeze is gone " +
                "with:\n" + pending.take(400),
            pending.contains("""testTag("application_preview_pending")"""),
        )
    }

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [ScanViewerResponsiveTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `every interface language says that the pages are being made`() {
        val tags = UI_LANGUAGES.map { it.first }
        val values = tags.associateWith { stringValue(it, "application_preview_pending") }

        val silent = tags.filter { values.getValue(it).isBlank() }
        assertTrue(
            "This sentence stands in place of the pages for as long as the drawing takes, so a " +
                "language without it shows an empty card instead: " + silent.joinToString(", "),
            silent.isEmpty(),
        )
        assertEquals(
            "Each language needs its own sentence — an English one under a Russian interface is " +
                "the complaint the language picker exists to answer:\n" + values,
            tags.size,
            values.values.distinct().size,
        )
    }
}
