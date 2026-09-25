package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a driver gets when it asks a tagged step name or tab name for its text.
 *
 * The three step names of the rail above the application steps and the four names of the bottom bar
 * each carry a test tag, but the tag sat on the box that MEASURES the place while the word is drawn
 * by a Text inside that box. A driver that asked the tagged node for its text therefore got the
 * empty string — the dump read `resource-id="nav_profil_label" text=""` with `Profil` on an untagged
 * TextView one level below — so a test that asserts "this tab is named Profil" failed against a
 * screen that was perfectly right, and the word could only be got at by walking the tree by hand.
 *
 * The tag now goes on the line itself, which is the node that has the word. It is a parameter of
 * [de.bewerbo.app.ui.components.FitOneLineText] rather than something a caller writes into the
 * modifier, because the modifier is the box's: the caller cannot reach the Text inside it.
 *
 * What the names are LETTERED IN is untouched by this and is checked where it was —
 * [BottomBarFitTest], [FlowRailFitTest] and [FitOneLineTextSizeTest]. What this test covers is that
 * the tag stays on the word, and the shape scan is the same kind [BottomBarFitTest] runs.
 */
class FitOneLineTextTagTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String = File(path).readText().replace("\r\n", "\n")

    private val activity: String = source("src/main/java/de/bewerbo/app/MainActivity.kt")
    private val components: String = source("src/main/java/de/bewerbo/app/ui/components/Components.kt")

    /// The declaration of [de.bewerbo.app.ui.components.FitOneLineText] — from the signature to the
    /// brace that closes it, which is where the tag is handed on.
    private val fitOneLineText: String = run {
        val start = components.indexOf("fun FitOneLineText(")
        assertTrue("The text that shrinks to fit is gone", start >= 0)
        val end = components.indexOf("\n}", start)
        assertTrue("FitOneLineText is never closed", end > start)
        components.substring(start, end)
    }

    /// The rail: from its own declaration to the bottom bar's, which is what follows it.
    private val rail: String = run {
        val start = activity.indexOf("private fun FlowRail(")
        assertTrue("The application flow no longer has a rail", start >= 0)
        val end = activity.indexOf("private fun BottomBar(", start)
        assertTrue("The rail runs into the end of the file", end > start)
        activity.substring(start, end)
    }

    /// The bottom bar: from its own declaration to the end of the file, which is where it stands.
    private val bar: String = run {
        val start = activity.indexOf("private fun BottomBar(")
        assertTrue("The app no longer has a bottom bar", start >= 0)
        activity.substring(start)
    }

    @Test
    fun `the tag goes on the line that carries the word, not on the box around it`() {
        assertTrue(
            "FitOneLineText must take the tag as a parameter of its own. Left to the caller's " +
                "modifier the tag lands on the box that measures the place, and that box has no " +
                "text to give back:\n$fitOneLineText",
            fitOneLineText.contains("testTag: String? = null"),
        )
        assertTrue(
            "The tag must be put on the Text that draws the word. Anywhere else a driver asking " +
                "the tagged node for its text gets the empty string back:\n$fitOneLineText",
            fitOneLineText.contains("modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,"),
        )
    }

    @Test
    fun `every tab name is tagged through that parameter, not through the modifier`() {
        assertTrue(
            "The bar must name its labels with the parameter: a tag in the modifier is a tag on " +
                "the measuring box, which answers with the empty string:\n$bar",
            bar.contains("testTag = \"\${destination.tag}_label\"") &&
                !bar.contains(".testTag(\"\${destination.tag}_label\")"),
        )
    }

    @Test
    fun `every step name is tagged through that parameter, not through the modifier`() {
        assertTrue(
            "The rail must name its step names with the parameter: a tag in the modifier is a tag " +
                "on the measuring box, which answers with the empty string:\n$rail",
            rail.contains("testTag = \"\${step.tag}_label\"") &&
                !rail.contains(".testTag(\"\${step.tag}_label\")"),
        )
    }
}
