package de.bewerbo.app

import de.bewerbo.app.ui.components.fittedFontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Whether the three step names of the rail above the application steps GROW when the reader turns
 * the system font up — in every interface language, and not only in the two that had room.
 *
 * [FitOneLineTextSizeTest] closed the half of this that was a defect of the ladder: raising the
 * system font must never letter a name SMALLER. What it left standing is the ceiling. The names are
 * lettered in one size, the size the longest of them needs in its third of the screen, and one line
 * in a third of the screen is all the room there is: at the accessibility maximum the rail grew to
 * about 1.4x in German and Ukrainian but to 1.03x in English and 1.00x in Russian, because
 * "Requirement check" and "Сверка требований" already filled their third at the default setting. A
 * reader who doubled the system font saw the heading and the step badges double while the names
 * stood at the everyday size that reader could not read.
 *
 * The room a name needs to grow is a SECOND line, so the rail offers two —
 * [de.bewerbo.app.ui.components.FitOneLineText]'s `maxLines` — and the name is wrapped between its
 * words instead of being shrunk to one line. Between its words and never INSIDE one: two lines is
 * room enough for Android to break a long word at a character boundary, and "Stellenanze" over
 * "ige" is not a step name. So a name of one unbreakable word is lettered in exactly what one line
 * holds, which is why German is no worse off than it was.
 *
 * The layout itself takes a measured screen and is checked on the emulator. The arithmetic that
 * decides it is checked here, the way [FitOneLineTextSizeTest] checks the ladder's, and the rail's
 * own decision — that it asks for the second line at all — is a shape scan of the same kind
 * [FlowRailFitTest] runs.
 *
 * That raising the system font never letters a name SMALLER stays [FitOneLineTextSizeTest]'s and is
 * deliberately not repeated here. It is a property of the ladder and of nothing else: the rungs
 * stand at fixed places in PIXELS, and whether a size still fits is a question about the words and
 * the place and not about the font scale — which the two-line rule is exactly as much as the
 * one-line rule was, so the proof carries over to it unchanged. Re-asserting it over a sweep of
 * places AND character widths does not strengthen it; it only manufactures sizes that sit a
 * ten-thousandth of a pixel from the edge of their place, where the two Float paths to one nominal
 * rung disagree and a model reads the last bit of a Float as a reader losing size. The ladder's own
 * test sweeps one parameter and is clean; the device readings of the run that built this cover the
 * rest.
 */
class FlowRailGrowthTest {

    /// The rail's own numbers on the emulator it was measured on: `labelMedium` is 12.sp, the
    /// component's floor is 9.dp, and the device is 1080x2400 at density 2.625.
    private val styleSp = 12f
    private val floorDp = 9f
    private val density = 2.625f

    /// The system font sizes Android offers, from the smallest to the accessibility maximum.
    private val fontScales = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)

    /**
     * The width a step name has on that device: a third of 1080 px, less the rail's own padding and
     * the two lines that join the three steps. Read off the device rather than recomputed — at font
     * scale 1.5 and above the German rail is pinned at a line 302 px wide, which is what its third
     * holds.
     */
    private val railThirdPx = 303f

    /**
     * How wide one character is, as a fraction of the glyph size. Measured: "Stellenanzeige" is 224
     * px at font scale 1.0, where `labelMedium` renders at 12 * 2.625 = 31.5 px, so 14 characters
     * come to 0.508 of the size each. It is swept rather than pinned wherever a claim should not
     * depend on it — a letter is not a square and Cyrillic is not Latin.
     */
    private val perChar = 0.508f
    private val perChars: List<Float> = (30..70).map { it / 100f }

    /// The three names of the rail, as `res/values*/strings.xml` spells them. The longest of each
    /// triple is what the whole row is lettered by.
    private val namesByLanguage = mapOf(
        "de" to listOf("Stellenanzeige", "Abgleich", "Bewerbung"),
        "en" to listOf("Posting", "Requirement check", "Application"),
        "ru" to listOf("Вакансия", "Сверка требований", "Заявка"),
        "uk" to listOf("Вакансія", "Звірка вимог", "Заявка"),
    )

    /// The parts a wrapped name may be broken between, the way the component counts them.
    private fun words(name: String): List<String> = name.split(' ').filter { it.isNotEmpty() }

    private fun widthPx(text: String, glyphPx: Float, perChar: Float): Float =
        text.length * glyphPx * perChar

    /**
     * A hundredth of a pixel of slack on "does this still fit", for the same reason
     * [FitOneLineTextSizeTest] compares its sizes to within a thousandth: the rungs are reached by
     * dividing and multiplying Floats, and the same rung reached from two different font scales is
     * not the same bit pattern. Without it the sweep below finds the ties — 14 characters at 0.66 of
     * a 23.8636 px glyph come to 220.4997 px against a place of 220.5 — and reads the last bit of a
     * Float as a reader getting a smaller name. A real layout measures in whole pixels and cannot
     * tell two sizes a ten-thousandth apart from each other at all.
     */
    private val tiePx = 0.01f

    /**
     * Whether [name] lettered in glyphs [glyphPx] tall fits a place [capPx] wide and [lines] lines
     * tall — the component's rule, modelled: on one line the whole name has to fit the width, and on
     * more than one every WORD has to fit it on its own before the wrapped name is counted at all.
     */
    private fun fits(name: String, glyphPx: Float, capPx: Float, lines: Int, perChar: Float): Boolean {
        if (lines == 1) return widthPx(name, glyphPx, perChar) <= capPx + tiePx
        if (words(name).any { widthPx(it, glyphPx, perChar) > capPx + tiePx }) return false
        return linesNeeded(name, glyphPx, capPx, perChar) <= lines
    }

    /// How many lines [name] is laid out on, greedily, once every word is known to fit one.
    private fun linesNeeded(name: String, glyphPx: Float, capPx: Float, perChar: Float): Int {
        var lines = 1
        var line = ""
        for (word in words(name)) {
            val joined = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || widthPx(joined, glyphPx, perChar) <= capPx + tiePx) {
                line = joined
            } else {
                lines++
                line = word
            }
        }
        return lines
    }

    /**
     * The size the row of [names] is lettered in at [fontScale], in PIXELS on the screen — the only
     * unit in which two font scales can be compared at all, and the one the reader sees. Every name
     * is measured against every other, which is what letters the row in one size.
     */
    private fun renderedPx(
        fontScale: Float,
        capPx: Float,
        names: List<String>,
        lines: Int,
        perChar: Float = this.perChar,
    ): Float {
        val floorSp = floorDp / fontScale
        val sp = fittedFontSize(styleSp, floorSp) { candidate ->
            val glyphPx = candidate * fontScale * density
            names.all { fits(it, glyphPx, capPx, lines, perChar) }
        }
        return sp * fontScale * density
    }

    @Test
    fun `at the accessibility maximum every language letters its names visibly larger`() {
        for ((language, names) in namesByLanguage) {
            val everyday = renderedPx(1.0f, railThirdPx, names, stepNameLines)
            val largest = renderedPx(2.0f, railThirdPx, names, stepNameLines)
            assertTrue(
                "A reader who doubles the system font must get visibly more to read. In " +
                    "$language the rail letters ${names.joinToString(", ")} in $everyday px at " +
                    "the default setting and in $largest px at the accessibility maximum — " +
                    "${"%.2f".format(largest / everyday)}x, which is the card's complaint and not " +
                    "its fix",
                largest >= everyday * 1.2f,
            )
        }
    }

    @Test
    fun `a second line never letters a name smaller than one line did`() {
        // Two lines is a relaxation of one, so it can only ever let a name live on a higher rung.
        // Worth asserting anyway: the word rule is a NEW way for a size to be rejected, and one
        // written a shade too strictly would take growth away instead of giving it.
        for ((language, names) in namesByLanguage) {
            for (perChar in perChars) {
                for (capPx in (20..800).map { it / 2f }) {
                    for (fontScale in fontScales) {
                        val oneLine = renderedPx(fontScale, capPx, names, 1, perChar)
                        val twoLines = renderedPx(fontScale, capPx, names, stepNameLines, perChar)
                        assertTrue(
                            "In $language a place $capPx px wide at font scale $fontScale letters " +
                                "the names in $oneLine px on one line and in $twoLines px on two: " +
                                "offering a name more room must not cost it size",
                            twoLines >= oneLine - 0.001f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a name of one word is lettered in exactly what one line holds, never broken to fill two`() {
        // German's guarantee, and the second acceptance criterion's: "Stellenanzeige" has no gap to
        // be broken at, and a layout that is handed two lines will break a word that is too wide at
        // a character boundary rather than overflow. The size must stay the one-line size, so that
        // what the reader gets is a whole word on one line and not "Stellenanze" over "ige".
        val unbreakable = listOf("Stellenanzeige", "Abgleich", "Bewerbung")
        for (perChar in perChars) {
            for (capPx in (20..800).map { it / 2f }) {
                for (fontScale in fontScales) {
                    assertEquals(
                        "A place $capPx px wide at font scale $fontScale letters " +
                            "${unbreakable.first()} differently on two lines than on one, and the " +
                            "only way a single word fills two lines is broken in half",
                        renderedPx(fontScale, capPx, unbreakable, 1, perChar).toDouble(),
                        renderedPx(fontScale, capPx, unbreakable, stepNameLines, perChar).toDouble(),
                        0.001,
                    )
                }
            }
        }
    }

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String = File(path).readText().replace("\r\n", "\n")

    private val activity: String = source("src/main/java/de/bewerbo/app/MainActivity.kt")
    private val components: String = source("src/main/java/de/bewerbo/app/ui/components/Components.kt")

    /// How many lines the rail offers a step name. The constant is the rail's own and stays private
    /// to the screen, so it is read out of the source the way [BottomBarFitTest] reads the
    /// component's ladder: the arithmetic below then follows the rail instead of restating it.
    private val stepNameLines: Int = run {
        val found = Regex("""private const val StepNameLines = ([0-9]+)""").find(activity)
        assertTrue("The rail no longer says how many lines a step name may use", found != null)
        found!!.groupValues[1].toInt()
    }

    /// The rail: from its own declaration to the bottom bar's, which is what follows it.
    private val rail: String = run {
        val start = activity.indexOf("private fun FlowRail(")
        assertTrue("The application flow no longer has a rail", start >= 0)
        val end = activity.indexOf("private fun BottomBar(", start)
        assertTrue("The rail runs into the end of the file", end > start)
        activity.substring(start, end)
    }

    @Test
    fun `the rail asks for the second line its names need to grow`() {
        assertTrue(
            "The rail must hand its step names more than one line. On one line the size is capped " +
                "by what a third of the screen holds, and in English and Russian the longest name " +
                "fills that third at the DEFAULT font setting already — so there is nothing left " +
                "for the accessibility maximum to grow into:\n$rail",
            rail.contains("maxLines = StepNameLines"),
        )
        assertTrue(
            "StepNameLines must be more than one, or asking for it changes nothing",
            stepNameLines > 1,
        )
    }

    @Test
    fun `the lines a name is offered are the lines it is measured in and rendered on`() {
        assertTrue(
            "The number of lines must reach the measurement. Measured on one line and rendered on " +
                "two the component answers for a text nobody sees, and the name is lettered at the " +
                "one-line size on a box two lines tall:\n$components",
            components.contains("maxLines: Int = 1,") &&
                components.contains("constraints.maxWidth, floor, maxLines,") &&
                components.contains("maxLines = maxLines,"),
        )
        assertTrue(
            "A text that may wrap has to be ALLOWED to wrap: with softWrap off the second line is " +
                "never used and the name is cut at the end of the first:\n$components",
            components.contains("softWrap = maxLines > 1,"),
        )
    }

    @Test
    fun `a name is broken between its words and never inside one`() {
        assertTrue(
            "Every word must be required to fit the width on its own. A box two lines tall is room " +
                "enough for Android to break a word that is wider than the line at a character " +
                "boundary instead of overflowing, so measuring the whole name reports no overflow " +
                "at a size at which \"Stellenanzeige\" comes out as \"Stellenanze\" over " +
                "\"ige\":\n$components",
            components.contains("(maxLines > 1 && words(text).any { spills(measurer, it, at, maxWidth, 1) })"),
        )
    }
}
