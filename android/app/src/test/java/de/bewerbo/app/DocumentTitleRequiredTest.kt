package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A document is saved with a title, or it is not saved.
 *
 * `POST /api/documents` refuses a blank title, and the Anlagenverzeichnis prints the title under
 * the Anlage number with no fallback — a record without one is a numbered line with nothing beside
 * it, on a page that goes to an employer. The forms used to send the save anyway and close on the
 * answer that came back, which loses everything that was typed.
 *
 * Two forms create a document: the Mappe's add card and the Abgleich's "Nachweis hochladen". The
 * rule belongs to both, and that is what is checked here — the same way [AddDocumentPickTest]
 * checks the pick both halves of the Documents screen share.
 */
class DocumentTitleRequiredTest {

    // The test runs with the module directory as the working directory. The two screens read here
    // do not agree on their line endings, so the text is normalised before anything looks for a
    // line in it.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    /// A declaration and what follows it up to the line that closes it — `indent` is the closing
    /// brace's own, none for a top-level function.
    private fun body(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun assertAsksForTheTitle(form: String, where: String, hintTag: String) {
        assertTrue(
            "$where must collect the missing fields the way the Berufserfahrung form does, " +
                "starting with a blank title",
            form.contains("if (title.isBlank()) add(stringResource(R.string.locker_field_title))"),
        )
        assertTrue(
            "$where must name what is missing where the user can see it, under the test tag " +
                "$hintTag",
            form.contains("R.string.experience_missing") && form.contains("testTag(\"$hintTag\")"),
        )
        assertTrue(
            "$where must keep Save out of reach while something is missing — a save that only " +
                "fails at the server closes the form and loses what was typed",
            form.contains("enabled = missing.isEmpty()"),
        )
    }

    @Test
    fun `the add card asks for the title before it saves`() {
        assertAsksForTheTitle(
            body(source("ui/screens/LockerScreen.kt"), "private fun AddDocumentCard("),
            "AddDocumentCard",
            "locker_missing_hint",
        )
    }

    @Test
    fun `the Abgleich's filing dialog asks for the title before it saves`() {
        assertAsksForTheTitle(
            body(source("ui/screens/MatchScreen.kt"), "private fun FileCertificateDialog("),
            "FileCertificateDialog",
            "match_file_missing_hint",
        )
    }
}
