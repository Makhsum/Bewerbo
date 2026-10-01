package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A row of "Aus der Anzeige gelesen", and the container that has to let its value wrap.
 *
 * The marker pill stood at the END of the row with no weight. A Row measures an unweighted child at
 * the width it asks for and leaves the weighted one whatever is over, so at the accessibility
 * maximum of the system font size the pill kept its full width and the value column was left
 * narrower than a syllable: "In der Anzeige nicht genannt" came down the screen as fourteen stacked
 * fragments, while the row below it — which offers no action — set the same sentence normally. The
 * reader who raised the font size in order to read is the one who could then no longer read what
 * the posting says.
 *
 * The layout itself takes a measured screen and is checked on the emulator. What can be checked here
 * is the decision behind it — the same kind of shape scan [DocumentRowFitTest] and
 * [ScanViewerFitTest] run over the Unterlagen screen — so that a later edit cannot quietly put the
 * trailing pill back.
 */
class PostingFieldRowFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private val screen: String =
        File("src/main/java/de/bewerbo/app/ui/screens/PostingScreen.kt")
            .readText().replace("\r\n", "\n")

    /// The field row: from its own declaration down to the correction dialog that follows it, which
    /// is where the value and its marker live and nothing else does.
    private val row: String = run {
        val start = screen.indexOf("private fun PostingFieldRow(")
        assertTrue("The fields card no longer has a row of its own", start >= 0)
        val end = screen.indexOf("private fun PostingCorrectionDialog(", start)
        assertTrue("PostingCorrectionDialog no longer follows the field row", end > start)
        screen.substring(start, end)
    }

    /// The declaration of the FlowRow that carries [tag] — from the container down to the brace that
    /// opens its content, which is where its modifier and its two arrangements stand.
    private fun container(tag: String): String {
        val tagged = row.indexOf(tag)
        assertTrue("$tag is no longer in the field row:\n$row", tagged >= 0)
        val start = row.lastIndexOf("FlowRow(", tagged)
        assertTrue("$tag is not the tag of a wrapping container:\n$row", start >= 0)
        val end = row.indexOf(") {", start)
        assertTrue("The container of $tag is never closed:\n$row", end > start)
        return row.substring(start, end)
    }

    @Test
    fun `the value and its marker sit in a container that wraps`() {
        assertTrue(
            "The value and the one action the row offers must be read in the same wrapping line, " +
                "not as a pill at the row's end that takes the value's width:\n$row",
            container("posting_field_line_").isNotBlank() &&
                row.contains("posting_field_value_") && row.contains("posting_field_marker_"),
        )
        assertTrue(
            "The marker may not stand outside the weighted column again: an unweighted child " +
                "takes the width it asks for and leaves the value less than a syllable:\n$row",
            row.indexOf("posting_field_marker_") > row.indexOf("posting_field_line_"),
        )
        assertTrue(
            "The mark, the icon, the label and the value column are the row's only fixed Row. A " +
                "second one is the defect coming back:\n$row",
            Regex("""(?<!Flow)\bRow\(""").findAll(row).count() == 1,
        )
    }

    @Test
    fun `the container is given the width it needs to wrap in and the gap a second line needs`() {
        val declaration = container("posting_field_line_")
        assertTrue(
            "A FlowRow of wrap-content width has no line to wrap into: the container must fill " +
                "the value column:\n$declaration",
            declaration.contains(".fillMaxWidth()"),
        )
        assertTrue(
            "A wrapped line needs a vertical arrangement of its own, or it is glued to the first " +
                "one:\n$declaration",
            declaration.contains("verticalArrangement = Arrangement.spacedBy("),
        )
    }
}
