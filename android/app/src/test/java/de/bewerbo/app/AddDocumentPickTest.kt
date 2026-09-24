package de.bewerbo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the add card may read of a picked file: the one that was picked in IT.
 *
 * A pick lives in two fields of the state — the file and the document it belongs to — because
 * choosing a file leaves the app and a screen's own `remember` does not survive that. Both halves
 * of the Documents screen pick through them: a row's "Add the scan", which names its document, and
 * the add card, which has no id yet and names none.
 *
 * The add card was reading the FILE alone. A scan added to a row while the card stood open filed
 * its page count into the card's Pages field, and the record saved there went to the server with a
 * count belonging to another document, which the Anlagenverzeichnis prints. It read as the card's
 * own number: nothing on the form said where it came from.
 *
 * The discriminator the state already carries is the gate — a pick that names no document was made
 * in the add card — and it is checked here rather than only written in a comment, the way
 * [NoCopyWordingTest] checks its one reader and [NavigationShapeTest] its bar.
 */
class AddDocumentPickTest {

    // The test runs with the module directory as the working directory.
    private fun source(path: String): String = File("src/main/java/de/bewerbo/app/$path").readText()

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, four spaces for a member function and none for a top-level one.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    /// Every way the state's picked file is read, as it stands in the text. The guard is part of the
    /// expression, so a read without one is a read this rule forbids.
    private fun reads(body: String): List<String> =
        Regex("""(?:state|it|_state\.value)\.pickedScan[^\n]*""").findAll(body).map { it.value }.toList()

    @Test
    fun `the add card reads only a pick that names no document`() {
        val card = body(source("ui/screens/LockerScreen.kt"), "private fun AddDocumentCard(", "")
        val reads = reads(card)

        assertEquals("The add card reads the picked file once, through one guarded expression", 1, reads.size)
        assertTrue(
            "The add card must read the picked file through pickedScanFor == null. Without that " +
                "guard a scan added to a row in the list fills this form's Pages field with " +
                "another document's page count:\n" + reads.joinToString("\n"),
            reads.single().contains("pickedScanFor == null"),
        )
    }

    @Test
    fun `saving the add card attaches only a pick that names no document`() {
        val add = body(source("data/AppViewModel.kt"), "fun addDocument(", "    ")
        val upload = reads(add).filterNot { it.contains("pickedScan = null") }

        assertTrue(
            "addDocument() must take the same pick the add card shows — one picked for a row in " +
                "the list would be filed under the new record, and its page count with it:\n" +
                upload.joinToString("\n"),
            upload.isNotEmpty() && upload.all { it.contains("pickedScanFor == null") },
        )
    }
}
