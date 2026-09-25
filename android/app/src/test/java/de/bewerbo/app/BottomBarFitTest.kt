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
 * Shrinking then kept every word whole, but each word only as far as its OWN item needed, and four
 * words in a row of four items came out in four sizes: at the accessibility maximum "Profil" stood
 * at full size beside a noticeably smaller "Übersicht", "Assistent" and "Unterlagen", and the bar
 * read as four separate controls rather than one row. The size is now the one that fits the longest
 * of the four, and the four agree on it — measuring the same words is not enough while one item is
 * a pixel wider than the others, which on this bar one of them is.
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
    fun `the bar hands every label all four words, so the row is lettered in one size`() {
        assertTrue(
            "The bar must resolve the four labels once and hand that list to every one of them as " +
                "its peers. A label that is shrunk only as far as its OWN word needs comes out at " +
                "its own size, and at a raised font size \"Profil\" then stands at full size " +
                "beside a much smaller \"Übersicht\":\n$bar",
            bar.contains("val labels = Destination.entries.map { stringResource(it.label) }") &&
                bar.contains("peers = labels"),
        )
    }

    @Test
    fun `the four agree on the size, because one item is a pixel wider than the others`() {
        assertTrue(
            "The bar must put its items into a FitOneLineTextGroup. Measuring the same four words " +
                "letters them in one size only while the four places are exactly as wide as each " +
                "other, and they are not: the bar gave its first item 211 px and the other three " +
                "210, and at font scale 1.5 that one pixel let \"Übersicht\" live one step of the " +
                "ladder longer than the rest:\n$bar",
            bar.contains("FitOneLineTextGroup {"),
        )
        assertTrue(
            "A member of the group must render at the size the group agreed on, not at the one " +
                "its own place allowed:\n$fitOneLineText",
            fitOneLineText.contains("group?.smallest?.let { own.copy(fontSize = it) } ?: own"),
        )
        assertTrue(
            "A member that is measured again must correct its own answer and take it with it when " +
                "it leaves, or the row cannot grow back when the font scale falls or the " +
                "interface language changes:\n$fitOneLineText",
            fitOneLineText.contains("DisposableEffect(group, text, own.fontSize)") &&
                fitOneLineText.contains("group.report(text, own.fontSize)") &&
                fitOneLineText.contains("onDispose { group.forget(text) }"),
        )
    }

    @Test
    fun `the shrinking stops at the step every word of the group still fits`() {
        assertTrue(
            "FitOneLineText must measure its peers along with its own text, or naming them " +
                "changes nothing:\n$fitOneLineText",
            fitOneLineText.contains("listOf(text) + peers"),
        )
        assertTrue(
            "The ladder must keep walking while ANY text of the group overflows — stopping at the " +
                "first one that fits is the per-word size coming back:\n$components",
            Regex("""texts: List<String>""").containsMatchIn(components) &&
                Regex("""val overflows = texts\.any""").containsMatchIn(components),
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
