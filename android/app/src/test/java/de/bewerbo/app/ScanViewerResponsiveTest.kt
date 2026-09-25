package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the app is doing while an opened scan is being drawn.
 *
 * Opening a stored copy rasterised every page of the file on the main thread: a five-page PDF held
 * the app still for between 1.8 and 4 seconds, and on an app that had just been started Android
 * put its "Bewerbo isn't responding" dialog over the window and offered to close the app on the
 * way to the reader's own Zeugnis. The viewer already had the sentence for a copy still on its way,
 * but nothing could be drawn at all while the main thread was rasterising, so the reader never saw
 * it.
 *
 * Checked here rather than only written in a comment, the way [ScanReplacementTest] checks the two
 * halves of a replacement: the drawing happens off the main thread, what is on screen in the
 * meantime says the scan is being fetched, and it says it in every interface language.
 */
class ScanViewerResponsiveTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, four spaces for a member function and none for a top-level one. The same
    /// reading [ScanReplacementTest] does.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun viewModel(): String = source("data/AppViewModel.kt")

    private fun showScan(): String =
        body(viewModel(), "private suspend fun showScan(", "    ")

    private fun scanViewer(): String =
        body(source("ui/screens/LockerScreen.kt"), "private fun ScanViewerDialog(", "")

    @Test
    fun `the pages of an opened scan are drawn off the main thread`() {
        val show = showScan()

        assertTrue(
            "viewModelScope is Dispatchers.Main, so rasterising has to be handed to another " +
                "dispatcher explicitly. Without that the app stands still for as long as the file " +
                "takes to draw:\n" + show,
            show.contains("withContext(Dispatchers.Default) { renderScanPages("),
        )
        assertEquals(
            "The viewer's pages may be rasterised in ONE place, and that place is the one above. " +
                "A second call somewhere in the view model is a second freeze:\n" + show,
            1,
            Regex("""renderScanPages\(""").findAll(viewModel()).count(),
        )
    }

    @Test
    fun `the state is written where a state change belongs`() {
        val show = showScan()

        val drawn = show.indexOf("withContext(Dispatchers.Default)")
        val published = show.indexOf("scanPages = pages")
        assertTrue("The pages are no longer drawn off the main thread", drawn >= 0)
        assertTrue(
            "The pages reach the state through the value the render returned, not from inside " +
                "the block that draws them — a _state.update moved onto a background dispatcher " +
                "is a second problem behind the first one:\n" + show,
            published > drawn,
        )
        assertTrue(
            "A reader can close the window while its pages are still being drawn. The pages that " +
                "arrive after that belong to nothing, and put into the state anyway they are the " +
                "ones the next window opens over:\n" + show,
            show.contains("_state.value.openScan?.id == id"),
        )
    }

    @Test
    fun `the viewer says the scan is being fetched until the pages are there`() {
        val viewer = scanViewer()

        val loading = viewer.substringAfter("""state.busy == "scan" && pages.isEmpty() -> Text(""")
        assertTrue("The viewer no longer has a branch for a scan still on its way", loading != viewer)
        assertTrue(
            "That branch is the only thing the reader sees for the seconds the drawing takes, so " +
                "it has to be the sentence saying the scan is being fetched:\n" + loading.take(300),
            loading.contains("R.string.scan_loading"),
        )
        assertTrue(
            "and it has to be findable, because that is what a run proves the freeze is gone " +
                "with:\n" + loading.take(300),
            loading.contains("""testTag("scan_viewer_loading")"""),
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
    fun `every interface language says that the scan is being fetched`() {
        val tags = UI_LANGUAGES.map { it.first }
        val values = tags.associateWith { stringValue(it, "scan_loading") }

        val silent = tags.filter { values.getValue(it).isBlank() }
        assertTrue(
            "This sentence stands in place of the pages for as long as the drawing takes, so a " +
                "language without it shows an empty window instead: " + silent.joinToString(", "),
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
