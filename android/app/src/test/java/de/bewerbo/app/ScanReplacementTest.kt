package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the opened scan shows after its copy has been replaced.
 *
 * "Ersetzen" uploaded the new file, the list behind the window was refreshed and the row there
 * already named the new page count — but the window itself went on showing the pages, the count and
 * the size of the copy that had just been replaced, because the document in the viewer was the
 * snapshot handed to it when it was opened. Closing and reopening was the only way to see the new
 * one, so a reader had no reason to believe anything had been sent and reached for Ersetzen a second
 * time.
 *
 * Two halves, and both are checked here rather than only written in a comment, the way
 * [AddDocumentPickTest] checks the split between the two places that hold a picked file: the upload
 * brings the window with it, and for as long as the new copy is on its way the window says so
 * instead of presenting the old one as current.
 */
class ScanReplacementTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, four spaces for a member function and none for a top-level one. The same reading
    /// [AddDocumentPickTest] does.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun deliverPickedScan(): String =
        body(source("data/AppViewModel.kt"), "private suspend fun deliverPickedScan(", "    ")

    private fun scanViewer(): String =
        body(source("ui/screens/LockerScreen.kt"), "private fun ScanViewerDialog(", "")

    @Test
    fun `the upload of a replacement brings the open viewer with it`() {
        val deliver = deliverPickedScan()

        assertTrue(
            "The upload has to recognise that it is replacing the copy the viewer is open on — " +
                "that comparison is the whole difference between a scan added to a row further " +
                "down the list and the Ersetzen of the one on screen:\n" + deliver,
            deliver.contains("openScan?.id == documentId"),
        )
        assertTrue(
            "Having recognised it, the upload must put the new record in the viewer. Refreshing " +
                "the profile alone is what the defect was: the row was right and the open window " +
                "was still the copy that had been replaced:\n" + deliver,
            deliver.contains("showScan("),
        )

        val store = deliver.indexOf("api.storeScan")
        val show = deliver.indexOf("showScan(")
        assertTrue("deliverPickedScan() no longer uploads anything", store >= 0)
        assertTrue(
            "The viewer is refilled AFTER the upload, from what it returned — before it there is " +
                "no new copy to show",
            show > store,
        )
    }

    @Test
    fun `a refused replacement leaves the copy that is there as the current one`() {
        val afterTheUpload = deliverPickedScan().substringAfter("api.storeScan")

        assertTrue(
            "An upload that is refused replaced nothing, so the window must stop saying a new " +
                "copy is on its way. Left standing it says so for the rest of the session:\n" +
                afterTheUpload,
            afterTheUpload.contains("openScanReplacing = false"),
        )
    }

    @Test
    fun `the viewer shows neither the detail line nor the pages of the copy being replaced`() {
        val viewer = scanViewer()

        assertEquals(
            "The viewer reads the replacement state once, into one name — the detail line, the " +
                "body and the two actions all follow that one expression",
            1,
            Regex("""state\.openScanReplacing""").findAll(viewer).count(),
        )
        assertTrue(
            "The detail line is the type, the page count and the size of the copy on the way out, " +
                "so it may only be drawn when no replacement is under way:\n" + viewer,
            viewer.contains("if (info != null && !replacing) {"),
        )

        val says = viewer.indexOf("replacing -> Text(")
        val loading = viewer.indexOf("""state.busy == "scan" && pages.isEmpty()""")
        assertTrue("The viewer no longer says that a replacement is on its way", says >= 0)
        assertTrue("The viewer's loading branch is gone", loading >= 0)
        assertTrue(
            "The replacement branch has to come FIRST. The pages in the state are still the old " +
                "ones at that point, so whichever branch below it matched would put the replaced " +
                "copy on screen as the current one",
            says < loading,
        )
    }

    @Test
    fun `neither action in the viewer acts on a copy that is on its way out`() {
        val viewer = scanViewer()

        assertTrue(
            "Share must be off while a replacement is under way: the file on the device is still " +
                "the one being replaced, so it would hand an employer the wrong Zeugnis:\n" + viewer,
            viewer.contains("enabled = pages.isNotEmpty() && !replacing"),
        )

        val replace = viewer.substringAfter("onClick = onReplace,")
        assertTrue("The viewer no longer offers Ersetzen", replace != viewer)
        assertTrue(
            "Ersetzen must be off while the first replacement is still on its way — replacing a " +
                "second time is exactly what a reader did when this window said nothing:\n" +
                replace.take(200),
            replace.trimStart().startsWith("enabled = !replacing,"),
        )
    }

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [NoCopyWordingTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `every interface language says that the new copy is on its way`() {
        val tags = UI_LANGUAGES.map { it.first }
        val values = tags.associateWith { stringValue(it, "scan_replacing") }

        val silent = tags.filter { values.getValue(it).isBlank() }
        assertTrue(
            "This sentence stands in place of the pages for as long as an upload takes, so a " +
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
