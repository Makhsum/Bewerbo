package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two buttons that fetch a scan into the add-a-document card, and the container that has to let
 * them wrap.
 *
 * They stood in a fixed `Row`. A Row measures its first child at the width that child asks for and
 * offers the second whatever is left, so at the accessibility maximum of the system font size
 * "Foto aufnehmen" kept its width and "Datei auswählen" was left a column narrower than the word:
 * Compose stacked it letter under letter. The reader who raised the font size is the reader who
 * then could not read the label.
 *
 * The layout itself takes a measured screen and is checked on the emulator. What can be checked
 * here is the decision behind it — the same kind of shape scan [AddDocumentPickTest] runs over this
 * screen and [NavigationShapeTest] over the bottom bar — so that a later edit cannot quietly put
 * the fixed row back.
 */
class ScanActionsFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private val screen: String =
        File("src/main/java/de/bewerbo/app/ui/screens/LockerScreen.kt")
            .readText().replace("\r\n", "\n")

    /// The scan row of the add card: from the label that names the field down to the file row that
    /// follows it, which is where the two buttons live and nothing else does.
    private val scanRow: String = run {
        val start = screen.indexOf("R.string.locker_field_scan")
        assertTrue("The add card no longer names the scan field", start >= 0)
        val end = screen.indexOf("PickedScanRow(picked", start)
        assertTrue("The file row no longer follows the scan buttons", end > start)
        screen.substring(start, end)
    }

    @Test
    fun `the scan buttons sit in a container that wraps`() {
        assertTrue(
            "Both scan buttons must be in the same container — the second one being squeezed by " +
                "the first is the whole defect:\n$scanRow",
            scanRow.contains("locker_btn_scan_photo") && scanRow.contains("locker_btn_scan_file"),
        )
        assertTrue(
            "That container must be a FlowRow. A fixed Row leaves the second button the width " +
                "the first one did not take, and at a raised font scale that is less than a " +
                "word:\n$scanRow",
            scanRow.contains("FlowRow("),
        )
        assertTrue(
            "and not a fixed Row beside it:\n$scanRow",
            !Regex("""\n\s+Row\(""").containsMatchIn(scanRow),
        )
    }

    @Test
    fun `the container is given the width it needs to wrap in and the gap a second line needs`() {
        assertTrue(
            "A FlowRow of wrap-content width has no line to wrap into: it must fill the card:\n" +
                scanRow,
            scanRow.contains(".fillMaxWidth()"),
        )
        assertTrue(
            "A wrapped line needs a vertical arrangement of its own, or the second button is " +
                "glued to the first:\n$scanRow",
            scanRow.contains("verticalArrangement = Arrangement.spacedBy("),
        )
    }
}
