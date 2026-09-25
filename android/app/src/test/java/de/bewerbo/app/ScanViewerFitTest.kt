package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The window that opens a stored scan, and the container its two buttons have to wrap in.
 *
 * They stood in a fixed `Row`. A Row measures its first child at the width that child asks for and
 * offers the second whatever is left, so at the accessibility maximum of the system font size
 * "Kopie teilen" kept its full width and "Ersetzen" was left a column narrower than the word:
 * Compose stacked it as "Er / se / tz / en". Replacing a scan is not an action a reader should have
 * to guess at, least of all the reader who raised the font size in order to read.
 *
 * The layout itself takes a measured screen and is checked on the emulator. What can be checked
 * here is the decision behind it — the same kind of shape scan [ScanActionsFitTest],
 * [FormActionsFitTest] and [DocumentRowFitTest] run over the add card and the list row — so that a
 * later edit cannot quietly put the fixed row back.
 */
class ScanViewerFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private val screen: String =
        File("src/main/java/de/bewerbo/app/ui/screens/LockerScreen.kt")
            .readText().replace("\r\n", "\n")

    /// The scan viewer: from its own declaration down to the camera contract that follows it, which
    /// is where the two buttons live and nothing else does.
    private val viewer: String = run {
        val start = screen.indexOf("private fun ScanViewerDialog(")
        assertTrue("The locker no longer has a window that opens a scan", start >= 0)
        val end = screen.indexOf("private class TakePictureInto", start)
        assertTrue("The camera contract no longer follows the scan viewer", end > start)
        screen.substring(start, end)
    }

    /// The declaration of the FlowRow that carries [tag] — from the container down to the brace that
    /// opens its content, which is where its modifier and its two arrangements stand.
    private fun container(tag: String): String {
        val tagged = viewer.indexOf(tag)
        assertTrue("$tag is no longer in the scan viewer:\n$viewer", tagged >= 0)
        val start = viewer.lastIndexOf("FlowRow(", tagged)
        assertTrue("$tag is not the tag of a wrapping container:\n$viewer", start >= 0)
        val end = viewer.indexOf(") {", start)
        assertTrue("The container of $tag is never closed:\n$viewer", end > start)
        return viewer.substring(start, end)
    }

    @Test
    fun `sharing the copy and replacing the scan sit in a container that wraps`() {
        assertTrue(
            "Both buttons must be in the same container — the second one being squeezed by the " +
                "first is the whole defect:\n$viewer",
            container("scan_viewer_actions").isNotBlank() &&
                viewer.contains("scan_btn_share") && viewer.contains("scan_btn_replace"),
        )
        assertTrue(
            "No fixed Row may stand in the scan viewer again: whatever child of one carries no " +
                "weight keeps its width and the rest is what breaks:\n$viewer",
            !Regex("""(?<!Flow)\bRow\(""").containsMatchIn(viewer),
        )
    }

    @Test
    fun `the container is given the width it needs to wrap in and the gap a second line needs`() {
        val declaration = container("scan_viewer_actions")
        assertTrue(
            "A FlowRow of wrap-content width has no line to wrap into: it must fill the window:\n" +
                declaration,
            declaration.contains(".fillMaxWidth()"),
        )
        assertTrue(
            "A wrapped line needs a vertical arrangement of its own, or the second button is " +
                "glued to the first:\n$declaration",
            declaration.contains("verticalArrangement = Arrangement.spacedBy("),
        )
    }

    @Test
    fun `all four things a reader can do with an opened scan are still there`() {
        for (tag in listOf("scan_btn_share", "scan_btn_replace", "scan_btn_remove", "scan_btn_close")) {
            assertTrue(
                "$tag is gone from the scan viewer — a layout fix must not cost the reader an " +
                    "action:\n$viewer",
                viewer.contains(tag),
            )
        }
    }
}
