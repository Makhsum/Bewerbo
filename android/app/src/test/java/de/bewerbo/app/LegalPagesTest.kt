package de.bewerbo.app

import de.bewerbo.app.ui.GERMAN_TERMS
import de.bewerbo.app.ui.UI_LANGUAGES
import de.bewerbo.app.ui.screens.LegalPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The legal pages are a legal duty, not a feature, so they get a gate rather than a promise.
 *
 * Two things can quietly go missing. A page can be dropped from the settings — and an app published
 * without an Impressum or without terms is one nobody may publish. Or a page can keep its title in a
 * language and lose its body, which is the shape of gap [GermanTermsTest] was written for: it looks
 * translated from the row that leads to it and is empty when it is opened.
 */
class LegalPagesTest {

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [GermanTermsTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun keysOf(tag: String): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(stringsFileFor(tag).readText())
            .map { it.groupValues[1] }
            .toSet()

    /// The keys a page's own text is written under: "legal_agb" is the row's label, and the page is
    /// everything under its own prefix.
    private fun bodyKeysOf(page: LegalPage, tag: String): Set<String> =
        keysOf(tag).filter { it.startsWith(bodyPrefix(page)) }.toSet()

    private fun bodyPrefix(page: LegalPage): String = when (page) {
        LegalPage.Terms -> "legal_terms_"
        LegalPage.Privacy -> "legal_privacy_"
        LegalPage.Imprint -> "legal_imprint_"
    }

    @Test
    fun `the settings offer the three legal pages and nothing less`() {
        // Terms because the user asked for them, the privacy notice because the app holds a
        // Lebenslauf, the Impressum because the law requires one of a published service.
        assertEquals(
            listOf("agb", "datenschutz", "impressum"),
            LegalPage.entries.map { it.route },
        )
    }

    @Test
    fun `every legal page carries its whole text in every language the picker offers`() {
        val missing = mutableListOf<String>()

        LegalPage.entries.forEach { page ->
            val expected = bodyKeysOf(page, "en")
            UI_LANGUAGES.map { it.first }.filterNot { it == "en" }.forEach { tag ->
                (expected - bodyKeysOf(page, tag)).forEach { missing += "values-$tag: $it" }
            }
        }

        assertTrue(
            "A legal page that falls back to the default locale is a page the reader cannot read:\n" +
                missing.sorted().joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun `no legal page is an empty shell`() {
        val thin = mutableListOf<String>()

        UI_LANGUAGES.map { it.first }.forEach { tag ->
            LegalPage.entries.forEach { page ->
                val count = bodyKeysOf(page, tag).size
                // Four is not a style rule: below it a page cannot have said who is responsible,
                // what it covers, on what footing and what the reader may do about it.
                if (count < 4) thin += "values-$tag: ${page.route} has $count paragraphs"
            }
        }

        assertTrue(
            "These pages carry a title and almost nothing else:\n" + thin.sorted().joinToString("\n"),
            thin.isEmpty(),
        )
    }

    @Test
    fun `each page explains the German word its row carries`() {
        val slugs = GERMAN_TERMS.map { it.slug }.toSet()
        val unknown = LegalPage.entries.map { it.termSlug }.filterNot { it in slugs }

        // germanTerm() throws on a slug that is not on the list, and it is called while the screen is
        // being composed — so a typo here is a crash on opening the page, not a missing footnote.
        assertTrue("These term slugs are on no kept term: $unknown", unknown.isEmpty())
    }

    @Test
    fun `the test can actually see the strings files`() {
        // The guard on the guard: a scan that read nothing would pass every assertion above.
        assertTrue("No strings read out of values/", keysOf("en").size > 100)
        LegalPage.entries.forEach {
            assertTrue("No body read for ${it.route}", bodyKeysOf(it, "en").size >= 4)
        }
    }
}
