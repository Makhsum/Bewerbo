package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A row of the document list, and the two containers that have to let it wrap.
 *
 * The kind pill and Löschen stood in a `Column` of their own beside the text, with no weight. A Row
 * measures an unweighted child at the width it asks for and leaves the weighted one whatever is
 * over, so at the accessibility maximum of the system font size that trailing column kept its full
 * width and the text column was left less than a German word: "Scan ablegen" came out as
 * "Sc / an / abl / eg / en" and the state pill broke inside "GESPEICHERT". The reader who raised the
 * font size in order to read is the one who could then no longer tell which documents are filed.
 *
 * The layout itself takes a measured screen and is checked on the emulator. What can be checked here
 * is the decision behind it — the same kind of shape scan [ScanActionsFitTest] and
 * [FormActionsFitTest] run over the add card's two button rows — so that a later edit cannot quietly
 * put the trailing column back.
 */
class DocumentRowFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private val screen: String =
        File("src/main/java/de/bewerbo/app/ui/screens/LockerScreen.kt")
            .readText().replace("\r\n", "\n")

    /// The document row: from its own declaration down to the demand row that follows it, which is
    /// where the pills and the three actions live and nothing else does.
    private val row: String = run {
        val start = screen.indexOf("private fun DocumentRow(")
        assertTrue("The document list no longer has a row of its own", start >= 0)
        val end = screen.indexOf("private fun DemandRow(", start)
        assertTrue("DemandRow no longer follows the document row", end > start)
        screen.substring(start, end)
    }

    /// The declaration of the FlowRow that carries [tag] — from the container down to the brace that
    /// opens its content, which is where its modifier and its two arrangements stand.
    private fun container(tag: String): String {
        val tagged = row.indexOf(tag)
        assertTrue("$tag is no longer in the document row:\n$row", tagged >= 0)
        val start = row.lastIndexOf("FlowRow(", tagged)
        assertTrue("$tag is not the tag of a wrapping container:\n$row", start >= 0)
        val end = row.indexOf(") {", start)
        assertTrue("The container of $tag is never closed:\n$row", end > start)
        return row.substring(start, end)
    }

    @Test
    fun `the row's pills and its actions sit in containers that wrap`() {
        assertTrue(
            "The kind must be read in the same line as the copy state, not in a column beside " +
                "the text that squeezes it:\n$row",
            container("locker_item_states_").isNotBlank() &&
                row.contains("locker_item_kind_") && row.contains("locker_item_copy_"),
        )
        assertTrue(
            "All three things a reader can do with a row — open the scan, add one, delete the " +
                "document — must be in the same container:\n$row",
            container("locker_item_actions_").isNotBlank() &&
                row.contains("locker_item_open_scan_") &&
                row.contains("locker_item_add_scan_") &&
                row.contains("locker_item_delete_"),
        )
        assertTrue(
            "Nothing may stand in a trailing column of its own again: an unweighted child takes " +
                "the width it asks for and leaves the text less than a word:\n$row",
            !row.contains("Alignment.End"),
        )
        assertTrue(
            "The icon and the text are the row's only fixed Row. A second one is the defect " +
                "coming back:\n$row",
            Regex("""(?<!Flow)\bRow\(""").findAll(row).count() == 1,
        )
    }

    @Test
    fun `each container is given the width it needs to wrap in and the gap a second line needs`() {
        for (tag in listOf("locker_item_states_", "locker_item_actions_")) {
            val declaration = container(tag)
            assertTrue(
                "A FlowRow of wrap-content width has no line to wrap into: the container of " +
                    "$tag must fill the card:\n$declaration",
                declaration.contains(".fillMaxWidth()"),
            )
            assertTrue(
                "A wrapped line needs a vertical arrangement of its own, or it is glued to the " +
                    "first one — the container of $tag has none:\n$declaration",
                declaration.contains("verticalArrangement = Arrangement.spacedBy("),
            )
        }
    }
}
