package de.bewerbo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Which file the add card holds, and that nothing else in the Documents screen can take it away.
 *
 * Choosing a file leaves the app and a screen's own `remember` does not survive that, so a pick
 * lives in the state. Both halves of the screen pick through it: a row's "Add the scan", which
 * names its document and uploads at once, and the add card, which has no id yet and holds its file
 * until the Save.
 *
 * They used to share ONE slot. A scan added to a row while the card stood open first filed its page
 * count into the card's Pages field, and then — once that was guarded — still overwrote the card's
 * file itself: the form's file row disappeared and the Save filed a document with no copy at all,
 * with nothing said about the file the user had chosen there. Two places that hold a file at the
 * same time need two fields, and that is `addFormScan` beside `pickedScan`.
 *
 * The split is checked here rather than only written in a comment, the way [NoCopyWordingTest]
 * checks its one reader and [NavigationShapeTest] its bar.
 */
class AddDocumentPickTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, four spaces for a member function and none for a top-level one.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    /// Every mention of the state's travelling pick, as it stands in the text — the field a row's
    /// scan passes through on its way to the server.
    private fun travelling(body: String): List<String> =
        Regex("""(?:state|it|_state\.value)\.pickedScan[^\n]*""").findAll(body).map { it.value }.toList()

    @Test
    fun `the add card shows the file chosen in it, and no other`() {
        val card = body(source("ui/screens/LockerScreen.kt"), "private fun AddDocumentCard(", "")

        assertTrue(
            "The add card must not read the travelling pick. A scan added to a row in the list " +
                "goes through it, and the card would show a file that is not its own:\n" +
                travelling(card).joinToString("\n"),
            travelling(card).isEmpty(),
        )
        assertEquals(
            "The add card reads its own file once, through state.addFormScan — the Pages field, " +
                "the file row and the caption under the field all follow that one expression",
            1,
            Regex("""state\.addFormScan""").findAll(card).count(),
        )
    }

    @Test
    fun `saving the add card attaches the file that card was holding`() {
        val add = body(source("data/AppViewModel.kt"), "fun addDocument(", "    ")

        assertTrue(
            "addDocument() must attach the file the add card holds — the record saved there is " +
                "the one the user chose it for:\n$add",
            add.contains("_state.value.addFormScan"),
        )
        assertTrue(
            "addDocument() must leave the travelling pick alone: one picked for a row in the list " +
                "would be filed under this record, and its page count with it:\n" +
                travelling(add).joinToString("\n"),
            travelling(add).isEmpty(),
        )
    }

    @Test
    fun `a scan added to a row never touches the file the add card holds`() {
        val source = source("data/AppViewModel.kt")

        val pick = body(source, "fun pickScan(", "    ")
        assertTrue(
            "pickScan() must leave addFormScan alone. It is the one thing every pick in the " +
                "screen passes through, so writing there drops the file the open add form is " +
                "holding:\n$pick",
            !pick.contains("addFormScan"),
        )

        val deliver = body(source, "private suspend fun deliverPickedScan(", "    ")
        val upload = deliver.indexOf("api.storeScan")
        assertTrue("deliverPickedScan() no longer uploads anything", upload >= 0)
        assertTrue(
            "The upload of a row's scan must not write addFormScan — the file the add form is " +
                "holding is no part of what that upload finishes:\n" + deliver.substring(upload),
            !deliver.substring(upload).contains("addFormScan"),
        )
    }
}
