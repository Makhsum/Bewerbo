package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two buttons that close the add-a-document card, and the container that has to let them wrap.
 *
 * They stood in a fixed `Row`, the way the scan buttons above them did before
 * [ScanActionsFitTest]. A Row measures its first child at the width that child asks for and offers
 * the second whatever is left, so at the accessibility maximum of the system font size "Speichern"
 * kept its width and "Abbrechen" was left a column narrower than the word: it came out as
 * "Abbreche" with a lone "n" on the line below. This is the row a reader has to find to get back
 * out of the form, and the reader who raised the font size is the one it failed.
 *
 * The layout itself takes a measured screen and is checked on the emulator. What can be checked
 * here is the decision behind it — the same shape scan [ScanActionsFitTest] runs over the scan row
 * — so that a later edit cannot quietly put the fixed row back.
 */
class FormActionsFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private val screen: String =
        File("src/main/java/de/bewerbo/app/ui/screens/LockerScreen.kt")
            .readText().replace("\r\n", "\n")

    /// The closing row of the add card: from the hint that names what is still missing down to the
    /// composable that follows the card, which is where the two buttons live and nothing else does.
    private val actionsRow: String = run {
        val start = screen.indexOf("locker_missing_hint")
        assertTrue("The add card no longer names what is missing", start >= 0)
        val end = screen.indexOf("private fun PickedScanRow", start)
        assertTrue("PickedScanRow no longer follows the add card", end > start)
        screen.substring(start, end)
    }

    @Test
    fun `the save and cancel buttons sit in a container that wraps`() {
        assertTrue(
            "Both buttons that close the form must be in the same container — the second one " +
                "being squeezed by the first is the whole defect:\n$actionsRow",
            actionsRow.contains("locker_btn_save") && actionsRow.contains("locker_btn_cancel"),
        )
        assertTrue(
            "That container must be a FlowRow. A fixed Row leaves Abbrechen the width Speichern " +
                "did not take, and at a raised font scale that is less than a word:\n$actionsRow",
            actionsRow.contains("FlowRow("),
        )
        assertTrue(
            "and not a fixed Row beside it:\n$actionsRow",
            !Regex("""\n\s+Row\(""").containsMatchIn(actionsRow),
        )
    }

    @Test
    fun `the container is given the width it needs to wrap in and the gap a second line needs`() {
        assertTrue(
            "A FlowRow of wrap-content width has no line to wrap into: it must fill the card:\n" +
                actionsRow,
            actionsRow.contains(".fillMaxWidth()"),
        )
        assertTrue(
            "A wrapped line needs a vertical arrangement of its own, or Abbrechen is glued to " +
                "Speichern:\n$actionsRow",
            actionsRow.contains("verticalArrangement = Arrangement.spacedBy("),
        )
    }
}
