package de.bewerbo.app

import de.bewerbo.app.ui.components.fittedFontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The size a shrink-to-fit line of text is lettered in, at every system font size.
 *
 * The three step names of the rail above the application steps are lettered in one size — the size
 * the longest of them needs in its third of the screen — and that size did not grow with the
 * system font. In English and Russian the names came out some 6 % SMALLER at the accessibility
 * maximum than at the default setting, and in German they shrank again between 150 % and 200 %:
 * the reader who turns the font up in order to read got less to read.
 *
 * Two things did it, and both are in [de.bewerbo.app.ui.components.FitOneLineText]:
 *
 * 1. The ladder was walked down from the size the system asked for, in steps OF that size, so its
 *    rungs stood at a different place on the screen at every font scale. Which rung a word landed
 *    on was then a coin toss decided by the font scale, and the spread between two tosses is one
 *    whole step — 6 %.
 * 2. `labelMedium` carries Material's `letterSpacing = 0.5.sp` and the tracking was left at the
 *    size the style came with, so it grew with the font scale while the glyphs were being shrunk.
 *    The same word in the same place then needed more width at 2.0 than at 1.5 and the ladder
 *    answered with a lower rung.
 *
 * The layout itself takes a measured screen and is checked on the emulator; the arithmetic that
 * decides it is checked here, the way [SegmentedControlFitTest] checks the segmented control's.
 * The rail's own half of the fix — that it opens a group and hands every name all three words — is
 * [FlowRailFitTest]'s, and the component's floor and ladder are [BottomBarFitTest]'s.
 */
class FitOneLineTextSizeTest {

    /// The rail's own numbers on the emulator it was measured on: `labelMedium` is 12.sp, the
    /// component's floor is 9.dp, and the device is 1080x2400 at density 2.625.
    private val styleSp = 12f
    private val floorDp = 9f
    private val density = 2.625f

    /// The system font sizes Android offers, from the smallest to the accessibility maximum.
    private val fontScales = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)

    /**
     * The size a name is lettered in at [fontScale], in PIXELS on the screen — the only unit in
     * which two font scales can be compared at all, and the one the reader sees.
     *
     * [capPx] is the largest size the name's third of the rail has room for. It does not move when
     * the font scale does: the place is a Dp wide and stays that many pixels, and the tracking is
     * taken down with the glyphs, so what fits is a property of the word and its place alone.
     */
    private fun renderedPx(fontScale: Float, capPx: Float): Float {
        val floorSp = floorDp / fontScale
        val sp = fittedFontSize(styleSp, floorSp) { candidate ->
            candidate * fontScale * density <= capPx
        }
        return sp * fontScale * density
    }

    /// Every width a place can have, from one too narrow for the floor to one with room to spare,
    /// in steps far finer than a rung of the ladder.
    private val capsPx: List<Float> = (20..800).map { it / 4f }

    @Test
    fun `raising the system font never letters a name smaller than a lower setting did`() {
        for (capPx in capsPx) {
            var lower = fontScales.first()
            for (higher in fontScales.drop(1)) {
                val before = renderedPx(lower, capPx)
                val after = renderedPx(higher, capPx)
                assertTrue(
                    "A place $capPx px wide letters the name in $before px at font scale $lower " +
                        "and in $after px at $higher. A reader who turns the system font up gets " +
                        "less to read than before, which is the one thing shrinking to fit may " +
                        "never do",
                    // A thousandth of a pixel: the rungs are reached by dividing and
                    // multiplying Floats, and a Float that has been round-tripped is not the
                    // bit pattern it started as. Nothing that small is on the screen.
                    after >= before - 0.001f,
                )
                lower = higher
            }
        }
    }

    @Test
    fun `a name with room to spare is lettered in exactly the size the system asked for`() {
        for (fontScale in fontScales) {
            val asked = styleSp * fontScale * density
            assertEquals(
                "Nothing is shrinking a name that fits its place twice over, so it must be " +
                    "lettered in the size the system asked for at font scale $fontScale — not on " +
                    "the rung below it",
                asked.toDouble(),
                renderedPx(fontScale, asked * 2f).toDouble(),
                0.001,
            )
        }
    }

    @Test
    fun `a place too narrow for the floor letters the name at the floor, not below it`() {
        for (fontScale in fontScales) {
            assertEquals(
                "Below the floor the word is ellipsised rather than made illegible, and the floor " +
                    "is a Dp: the same pixels at font scale $fontScale as at every other",
                (floorDp * density).toDouble(),
                renderedPx(fontScale, 1f).toDouble(),
                0.001,
            )
        }
    }

    @Test
    fun `a place that holds the name at one font scale holds it at every higher one`() {
        // The size a place settles on is the reason: once the name no longer fits at the size the
        // system asks for, the same rung answers every higher scale, so the word stays whole.
        for (capPx in capsPx.filter { it >= floorDp * density }) {
            for (fontScale in fontScales) {
                assertTrue(
                    "A place $capPx px wide letters the name in ${renderedPx(fontScale, capPx)} " +
                        "px at font scale $fontScale, which does not fit: a name that is not cut " +
                        "off is the other half of the card",
                    renderedPx(fontScale, capPx) <= capPx + 0.001f,
                )
            }
        }
    }

    @Test
    fun `the tracking is taken down with the glyphs, or the place shrinks as the font grows`() {
        // The one half of the fix that no arithmetic here can stand in for: what a word needs is
        // measured, and it is measured with the style the text is then rendered in.
        val components = File("src/main/java/de/bewerbo/app/ui/components/Components.kt")
            .readText().replace("\r\n", "\n")
        assertTrue(
            "A shrunk style must carry a shrunk letterSpacing. Left at the size the style came " +
                "with, an sp tracking grows with the system font while the glyphs are being " +
                "shrunk, the same word needs more width at every higher font scale, and the size " +
                "a place has room for falls as the reader turns the font up:\n$components",
            components.contains("letterSpacing = letterSpacing * (size.value / fontSize.value)"),
        )
        assertTrue(
            "The style a candidate size is MEASURED in must be the style it is RENDERED in, or " +
                "the measurement answers for a text nobody sees:\n$components",
            components.contains("val at = style.atFontSize(candidate.sp)") &&
                components.contains("return style.atFontSize(size.sp)"),
        )
    }
}
