package de.bewerbo.app

import de.bewerbo.app.ui.components.segmentsPerLine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many options of a segmented control share one line.
 *
 * The control gives every option the same column, so four columns of a phone-width card are 224
 * px each. "Arbeitszeugnis" and "Sprachnachweis" need a few pixels more than that, and Compose
 * broke them where they ran out: the selector read "Arbeitszeugni / s" and "Sprachnachw / eis",
 * two words the user has to recognise from a job advert.
 *
 * The layout itself cannot be checked here — it takes a measured screen — but the arithmetic that
 * decides it can, and that is where the answer is wrong or right. The widths below are pixels of
 * the 1080 px emulator the selector was measured on.
 */
class SegmentedControlFitTest {

    /// The add-a-document card, as it is measured on a 1080 px screen: the card leaves the control
    /// 896 px, and "Arbeitszeugnis" in bold needs some 230 of them — four columns are 224 each,
    /// which is where the word lost its last letter.
    private val cardWidth = 896
    private val kindWidth = 230

    @Test
    fun `four document kinds that do not fit one line read as two and two`() {
        assertEquals(
            "Three kinds fit the card's width, but a 3 + 1 control is a row with something stuck " +
                "under it. The lines are filled evenly instead",
            2,
            segmentsPerLine(cardWidth, kindWidth, 4),
        )
    }

    @Test
    fun `a control whose options fit stays the single row it has always been`() {
        assertEquals(
            "Two kinds fit 896 px side by side, and a control that fits must render exactly as " +
                "it did before the wrapping was added",
            2,
            segmentsPerLine(cardWidth, kindWidth, 2),
        )
        assertEquals(
            "The five application states are short words and share one line, as they did " +
                "before a control could wrap at all",
            5,
            segmentsPerLine(cardWidth, 170, 5),
        )
    }

    @Test
    fun `a label wider than the whole control still gets a line of its own`() {
        assertEquals(
            "One column is the floor: below it there is nothing left to take away, and the label " +
                "wraps inside its own line rather than being clipped",
            1,
            segmentsPerLine(cardWidth, 1200, 3),
        )
    }

    @Test
    fun `a control that has not been measured yet shows its options in one line`() {
        assertEquals(
            "Before the first measurement there is no width to divide, and the row as it was " +
                "written is the honest answer",
            4,
            segmentsPerLine(0, 0, 4),
        )
    }
}
