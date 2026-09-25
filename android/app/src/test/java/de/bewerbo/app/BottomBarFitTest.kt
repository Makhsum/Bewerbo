package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * The bottom bar, and the four words it has to name its places with at every system font size.
 *
 * The labels already shrank to fit rather than wrap, but the size they stopped shrinking at was
 * written as an sp — and an sp gets bigger when the reader turns the system font up. At the
 * accessibility maximum the floor therefore stood at twice the pixels it was meant to be, the
 * ladder hit it while "Unterlagen" was still wider than its quarter of the screen, and the open
 * tab read "Unterla…" — in Ukrainian "Докуме…" — on the screen of exactly the reader who had
 * raised the font in order to read. The other three words are short enough to have survived, so
 * nothing showed until a language or a label grew.
 *
 * The layout itself takes a measured screen and is checked on the emulator. What can be checked
 * here is the decision behind it — the same kind of shape scan [ScanViewerFitTest],
 * [ScanActionsFitTest], [FormActionsFitTest] and [DocumentRowFitTest] run over the surfaces before
 * it — so that a later edit cannot quietly put the growing floor back.
 */
class BottomBarFitTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String = File(path).readText().replace("\r\n", "\n")

    private val activity: String = source("src/main/java/de/bewerbo/app/MainActivity.kt")
    private val components: String = source("src/main/java/de/bewerbo/app/ui/components/Components.kt")

    /// The bottom bar: from its own declaration to the end of the file, which is where it stands.
    private val bar: String = run {
        val start = activity.indexOf("private fun BottomBar(")
        assertTrue("The app no longer has a bottom bar", start >= 0)
        activity.substring(start)
    }

    /// The declaration of [FitOneLineText] — from the signature down to the brace that opens its
    /// body, which is where the floor it stops shrinking at is named.
    private val fitOneLineText: String = run {
        val start = components.indexOf("fun FitOneLineText(")
        assertTrue("The text that shrinks to fit is gone", start >= 0)
        val end = components.indexOf("\n}", start)
        assertTrue("FitOneLineText is never closed", end > start)
        components.substring(start, end)
    }

    /// The one number a pattern captures out of the component, as a Double.
    private fun number(pattern: String): Double {
        val found = Regex(pattern).find(components)
        assertTrue("Nothing in Components.kt matches $pattern", found != null)
        return found!!.groupValues[1].toDouble()
    }

    @Test
    fun `every tab is named by the text that shrinks to fit, never by a plain one`() {
        assertTrue(
            "The bar must write its labels with FitOneLineText, and it must tag each of them so a " +
                "driver can read the line back:\n$bar",
            bar.contains("FitOneLineText(") && bar.contains("\${destination.tag}_label"),
        )
        assertTrue(
            "A plain Text in the bar is a label that wraps or is cut instead of shrinking:\n$bar",
            !Regex("""(?<!FitOneLine)Text\(""").containsMatchIn(bar),
        )
    }

    @Test
    fun `the label keeps a margin, so a word that is whole also looks whole`() {
        assertTrue(
            "Without a horizontal padding the longest label is shrunk until it touches the screen " +
                "edge, and a tab that ends at the edge reads as one that was cut off there:\n$bar",
            bar.contains(".padding(horizontal = Space."),
        )
    }

    @Test
    fun `the size the shrinking stops at is a Dp, so it does not grow with the font scale`() {
        assertTrue(
            "The floor must be a Dp: written as an sp it is twice as many pixels at the " +
                "accessibility maximum, and the word is cut before it ever gets small enough:\n" +
                fitOneLineText,
            Regex("""minFontSize: Dp = [0-9.]+\.dp""").containsMatchIn(fitOneLineText),
        )
        assertTrue(
            "The Dp floor has to be converted at the font scale of the moment, or nothing " +
                "measures against it:\n$fitOneLineText",
            fitOneLineText.contains("minFontSize.toSp()"),
        )
    }

    @Test
    fun `the ladder is long enough to walk down to that floor at the accessibility maximum`() {
        val shrinkFactor = number("""private const val ShrinkFactor = ([0-9.]+)f""")
        val maxShrinkSteps = number("""private const val MaxShrinkSteps = ([0-9]+)""")
        val floorDp = number("""minFontSize: Dp = ([0-9.]+)\.dp""")

        // Material's labelMedium is 12.sp and that is what the bar writes its labels in. At the
        // accessibility maximum the system renders those 12.sp as 24 dp, while the floor stays at
        // floorDp — so in sp the ladder has to be able to fall to floorDp / 2 to reach it.
        val startSp = 12.0
        val floorSp = floorDp / 2.0
        val smallest = startSp * shrinkFactor.pow(maxShrinkSteps)

        assertTrue(
            "$maxShrinkSteps steps of $shrinkFactor take 12.sp down to ${"%.2f".format(smallest)}" +
                ".sp, and the floor at font scale 2.0 is ${"%.2f".format(floorSp)}.sp: the ladder " +
                "runs out before the floor, which is the ellipsis coming back",
            smallest < floorSp,
        )
    }
}
