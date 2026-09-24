package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a document with no stored copy may be told about itself.
 *
 * It was told the same thing about every one of them: "This document was added on another device."
 * The Documents screen was reading the absence of a scan as an ORIGIN, and the record holds no such
 * fact — a document filed on this phone with no file chosen, and one whose file the server refused,
 * both stand there with no copy and neither came from anywhere else. The user read a statement
 * about their own data that was simply untrue, under a "No copy stored" mark that was correct.
 *
 * The claim now rests on something: the client stamps what it files with its own installation id
 * (`AppViewModel.deviceId`), and only a stamp that is ANOTHER device's may produce that sentence.
 * A gate rather than a corrected sentence, for the reason [DeleteAccountDialogTest] is one — an
 * untrue sentence about the user's data comes back the next time the screen is reworded, and the
 * wrong version reads perfectly well.
 */
class NoCopyWordingTest {

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [DeleteAccountDialogTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    /// The word each language names a second device with. The check is on the word and not on a
    /// whole sentence: how the sentence is phrased is the writer's business, whether it speaks of
    /// another device is not. Matched without case, so "Gerät" catches "Geräte" too.
    private val NAMES_A_DEVICE = mapOf(
        "en" to "device",
        "de" to "Gerät",
        "ru" to "устройств",
        "uk" to "пристро",
    )

    private fun localeTags(): List<String> = UI_LANGUAGES.map { it.first }

    private fun names(tag: String, value: String): Boolean =
        value.contains(NAMES_A_DEVICE.getValue(tag), ignoreCase = true)

    @Test
    fun `the sentence for a document whose scan is simply missing names no other device`() {
        val offenders = localeTags().filter { names(it, stringValue(it, "locker_no_scan_yet")) }

        assertTrue(
            "locker_no_scan_yet stands under a document this phone may well have filed itself, " +
                "so it may say that the scan is missing and never where the document came from:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `a document that really was added elsewhere is still said to have been`() {
        // The other half of the rule. Watering this one down until it says nothing would pass the
        // test above and lose the case the sentence exists for — a user signing in on a second
        // phone, looking at a list of documents whose files are all on the first one.
        val silent = localeTags().filterNot { names(it, stringValue(it, "locker_added_elsewhere")) }

        assertTrue(
            "locker_added_elsewhere is the one sentence that may name another device, and these " +
                "languages no longer do:\n" + silent.joinToString("\n"),
            silent.isEmpty(),
        )
    }

    @Test
    fun `the sentence about another device is shown only where the record says so`() {
        val uses = sources()
            .filter { it.readText().contains("R.string.locker_added_elsewhere") }
            .map { it.name }
            .toList()

        // One reader, and it is the one that compares the stamp. Any second place showing this
        // string is a place that decided the origin from something else — which is the defect.
        assertEquals("Only noCopyReason() may show this sentence", listOf("LockerScreen.kt"), uses)

        val reason = Regex("""private fun noCopyReason\((.*?)\n\n""", RegexOption.DOT_MATCHES_ALL)
            .find(File("src/main/java/de/bewerbo/app/ui/screens/LockerScreen.kt").readText())
            ?.value
            .orEmpty()

        assertTrue("noCopyReason() not found in LockerScreen.kt", reason.isNotBlank())
        assertTrue(
            "noCopyReason() must decide on the document's stamp — the absence of a scan is not " +
                "an origin, which is the whole of this rule",
            reason.contains("addedOnDevice"),
        )
    }

    // The test runs with the module directory as the working directory.
    private fun sources(): Sequence<File> =
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
}
