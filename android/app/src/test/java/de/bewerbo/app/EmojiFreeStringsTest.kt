package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The hard rule, with an enforcement mechanism rather than a promise: no emoji in any user-facing
 * string, in any locale.
 *
 * The concept this app was built from was rejected once for using emoji where icons belong. A rule
 * that lives only in a review comment comes back the first time somebody is in a hurry; this one
 * fails the build.
 */
class EmojiFreeStringsTest {

    private fun stringsFiles(): List<File> {
        // The test runs with the module directory as the working directory.
        val res = File("src/main/res")
        require(res.isDirectory) { "res/ not found at ${res.absolutePath}" }
        return res.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .mapNotNull { File(it, "strings.xml").takeIf(File::isFile) }
    }

    @Test
    fun `no strings file contains an emoji`() {
        val offenders = mutableListOf<String>()

        stringsFiles().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                line.codePoints().toArray().forEach { codePoint ->
                    if (isEmoji(codePoint)) {
                        offenders += "${file.parentFile.name}/strings.xml:${index + 1} " +
                            "U+${codePoint.toString(16).uppercase()}"
                    }
                }
            }
        }

        assertTrue(
            "Emoji found in user-facing strings. Use an icon from BewerboIcons instead:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the test can actually see the strings files`() {
        // Without this, a scan that found nothing because it looked in the wrong directory would
        // pass silently — which is the usual way a rule like this stops being enforced.
        val files = stringsFiles()
        assertTrue("No strings.xml found to scan", files.size >= 3)
    }

    @Test
    fun `the emoji detector recognises what it is looking for`() {
        // The guard on the guard: a detector that matches nothing passes every file.
        assertTrue("Emoticon not detected", isEmoji(0x1F600))
        assertTrue("Pictograph not detected", isEmoji(0x1F4C4))
        assertTrue("Transport symbol not detected", isEmoji(0x1F680))
        assertTrue("Dingbat not detected", isEmoji(0x2705))
        assertTrue("Warning sign not detected", isEmoji(0x26A0))

        // And the characters the product legitimately uses must NOT be flagged.
        assertTrue("En dash wrongly flagged", !isEmoji(0x2013))
        assertTrue("Em dash wrongly flagged", !isEmoji(0x2014))
        assertTrue("Middle dot wrongly flagged", !isEmoji(0x00B7))
        assertTrue("German quotes wrongly flagged", !isEmoji(0x00BB))
        assertTrue("Cyrillic wrongly flagged", !isEmoji(0x0410))
        assertTrue("Umlaut wrongly flagged", !isEmoji(0x00FC))
    }

    private fun isEmoji(codePoint: Int): Boolean = when (codePoint) {
        in 0x1F300..0x1F5FF -> true   // symbols and pictographs
        in 0x1F600..0x1F64F -> true   // emoticons
        in 0x1F680..0x1F6FF -> true   // transport and map
        in 0x1F900..0x1F9FF -> true   // supplemental symbols
        in 0x1FA70..0x1FAFF -> true   // extended-A
        in 0x2600..0x27BF -> true     // misc symbols and dingbats
        in 0x2B00..0x2BFF -> true     // arrows and stars used as emoji
        0xFE0F, 0x20E3 -> true        // variation selector, keycap
        in 0x1F1E6..0x1F1FF -> true   // regional indicators (flags)
        else -> false
    }
}
