package de.bewerbo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Which render the Bewerbungsmappe's preview on screen belongs to.
 *
 * The two guards [PreviewResponsiveTest] and [PreviewFailureTest] put in compared the chosen parts
 * the render had been asked for. That tells two renders of DIFFERENT selections apart and nothing
 * else: `refreshPreview` runs in viewModelScope and a render the chips have moved on from is never
 * cancelled, so toggling one export chip off and on again leaves two renders of the SAME parts in
 * flight — a run watched three cache files at one instant. Both passed a guard that reads the
 * selection, so whichever finished LAST won, and that is the slow one started first: its failure
 * replaced pages the newer render had already drawn, and the user read "Die Seiten konnten nicht
 * angezeigt werden" over a preview that had been there a second earlier. Mirrored, its pages landed
 * under the newer render's failure card, which keeps them hidden.
 *
 * Read from the source the same way the two classes above read their own halves: the render has a
 * number, the state carries the number of the one it is waiting for, every step that publishes
 * compares against it, and no selection is asked that question anywhere any more.
 */
class PreviewSupersededTest {

    // The test runs with the module directory as the working directory. The line endings are
    // levelled because core.autocrlf is on for this repository on Windows: a checkout writes the
    // very same source with \r\n, and the searches below are for lines.
    private fun source(path: String): String =
        File("src/main/java/de/bewerbo/app/$path").readText().replace("\r\n", "\n")

    private fun viewModel(): String = source("data/AppViewModel.kt")

    /// A declaration and what follows it up to the line that closes it — the same reading
    /// [PreviewFailureTest] does, and `indent` is the closing brace's own.
    private fun body(source: String, declaration: String, indent: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        val end = source.indexOf("\n$indent}\n", start)
        assertTrue("$declaration is never closed", end > start)
        return source.substring(start, end)
    }

    private fun refreshPreview(): String =
        body(viewModel(), "fun refreshPreview(parts: String) = launch(", "    ")

    @Test
    fun `the state says which render the preview is waiting for, not which selection`() {
        val state = viewModel().substringBefore("\nclass AppViewModel(")

        assertTrue(
            "The screen has to be able to name the render whose outcome it is waiting for. A " +
                "selection cannot: two renders of the same parts read identically, and the " +
                "Bewerbung screen produces exactly those two on a chip toggled off and on:\n" +
                state.substringAfter("val previewFailure").take(600),
            state.contains("val previewRender: Int = 0,"),
        )
        assertEquals(
            "and the selection must not be left standing beside it — a second identity for the " +
                "same render is the one the next reader guards on by mistake:",
            0,
            Regex("""previewParts""").findAll(viewModel()).count(),
        )
    }

    @Test
    fun `a render takes its number once, before it can be overtaken`() {
        val refresh = refreshPreview()

        assertEquals(
            "The number is taken ONCE and held in the coroutine. Read a second time it is the " +
                "counter as it stands then, which is the newer render's number, and the older " +
                "render would pass every guard again:\n" + refresh,
            1,
            Regex("""val render = \+\+previewRenders""").findAll(refresh).count(),
        )

        val taken = refresh.indexOf("val render = ++previewRenders")
        val claimed = refresh.indexOf("previewRender = render")
        val fetched = refresh.indexOf("api.applicationPdf(")
        assertTrue("The render no longer takes a number of its own", taken >= 0)
        assertTrue(
            "The state has to carry the number of the render it is waiting for, and carry it " +
                "from the update that clears the pages — that update is where a render takes the " +
                "screen over from the one before:\n" + refresh,
            claimed > taken,
        )
        assertTrue(
            "and it has to take it over BEFORE the download, not after: everything in flight at " +
                "that moment is what this render supersedes:\n" + refresh,
            claimed < fetched,
        )
    }

    @Test
    fun `both ways a render can end are published only by the render the screen waits for`() {
        val refresh = refreshPreview()
        val said = body(viewModel(), "private fun previewFailed(render: Int, reason: String)", "    ")

        assertTrue(
            "The pages of a render a later one has taken over may not reach the state — under a " +
                "newer failure card they are hidden for good, and over a newer selection they are " +
                "a picture of a file the chips no longer describe:\n" + refresh,
            refresh.contains("if (_state.value.previewRender == render) _state.update { it.copy(previewPages = pages) }"),
        )
        assertTrue(
            "and its failure may not either, which is the complaint this card was opened for: " +
                "\"Die Seiten konnten nicht angezeigt werden\" over a preview that was drawn a " +
                "second earlier:\n" + said,
            said.contains("if (_state.value.previewRender == render)"),
        )

        assertEquals(
            "Both failures go through previewFailed, so the guard above is the only one needed — " +
                "a previewFailure written anywhere else in the view model is a way past it:\n" + said,
            1,
            Regex("""previewFailure = reason""").findAll(viewModel()).count(),
        )
        assertEquals(
            "and both of them say so by its number, not by the parts it was asked for:\n" + refresh,
            2,
            Regex("""previewFailed\(render, PREVIEW_NOT_""").findAll(refresh).count(),
        )
    }
}
