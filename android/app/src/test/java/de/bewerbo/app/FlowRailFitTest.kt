package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rail above the application steps, and the three words it has to name them with at every
 * system font size.
 *
 * The names already shrank to fit rather than wrap, but each of them only as far as its OWN third
 * of the screen needed, and three words in a row of three came out in three sizes: at a raised
 * font size "Bewerbung" stood at full size beside a noticeably smaller "Stellenanzeige", and the
 * rail read as three separate controls instead of one path. The bottom bar had the same fault and
 * was fixed by handing every label all of the row's words and letting the row agree on the
 * smallest of the answers; the rail is [de.bewerbo.app.ui.components.FitOneLineText]'s other
 * caller and is fixed the same way.
 *
 * The component's own half of that fix — the Dp floor, the length of the ladder, the group that
 * carries the agreement — is checked by [BottomBarFitTest] and not repeated here. What this test
 * covers is the rail's own decision: that it opens a group at all and hands its three names on.
 * The layout itself takes a measured screen and is checked on the emulator.
 */
class FlowRailFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String = File(path).readText().replace("\r\n", "\n")

    private val activity: String = source("src/main/java/de/bewerbo/app/MainActivity.kt")

    /// The rail: from its own declaration to the bottom bar's, which is what follows it.
    private val rail: String = run {
        val start = activity.indexOf("private fun FlowRail(")
        assertTrue("The application flow no longer has a rail", start >= 0)
        val end = activity.indexOf("private fun BottomBar(", start)
        assertTrue("The rail runs into the end of the file", end > start)
        activity.substring(start, end)
    }

    @Test
    fun `every step is named by the text that shrinks to fit, never by a plain one`() {
        assertTrue(
            "The rail must write its step names with FitOneLineText, and it must tag each of them " +
                "so a driver can read the line back. The badge beside a name is a plain Text and " +
                "stays one — a numeral has no word to keep whole:\n$rail",
            rail.contains("FitOneLineText(") && rail.contains("\${step.tag}_label"),
        )
    }

    @Test
    fun `the rail hands every name all three words, so the row is lettered in one size`() {
        assertTrue(
            "The rail must resolve the three step names once and hand that list to every one of " +
                "them as its peers. A name that is shrunk only as far as its OWN third needs " +
                "comes out at its own size, and at a raised font size \"Bewerbung\" then stands " +
                "at full size beside a much smaller \"Stellenanzeige\":\n$rail",
            rail.contains("val labels = FlowStep.entries.map { stringResource(it.label) }") &&
                rail.contains("peers = peers"),
        )
    }

    @Test
    fun `the three agree on the size, because their places are not exactly as wide`() {
        assertTrue(
            "The rail must put its steps into a FitOneLineTextGroup. Measuring the same three " +
                "words letters them in one size only while the three places are exactly as wide " +
                "as each other, and they are not: a Row hands the pixels left over from the " +
                "division to one of them, and one pixel is enough to let that word live a step of " +
                "the ladder longer than the other two:\n$rail",
            rail.contains("FitOneLineTextGroup {"),
        )
    }
}
