package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import de.bewerbo.app.ui.defaultUiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The interface-language picker promises a translation, so every language on it has to have one.
 *
 * Offering a language with no values-<tag>/strings.xml is worse than not offering it: the user
 * picks their own language, the screen stays English, and nothing on it explains why. That is the
 * complaint this control was built to answer, so it gets a gate rather than a comment — the same
 * way the emoji rule does in [EmojiFreeStringsTest].
 */
class UiLanguagesTest {

    /// The default locale's strings live in values/, every other one in values-<tag>/.
    // The test runs with the module directory as the working directory.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    @Test
    fun `every offered interface language has its own strings`() {
        val missing = UI_LANGUAGES
            .map { it.first }
            .filterNot { stringsFileFor(it).isFile }

        assertTrue(
            "These languages are offered in the picker but have no strings.xml, so picking one " +
                "leaves the interface in English: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `German is offered`() {
        // The product writes German applications for people living in Germany. An interface that
        // cannot be set to German is the defect this list exists to keep fixed.
        assertTrue(
            "German is not on the interface-language list",
            UI_LANGUAGES.any { it.first == "de" },
        )
    }

    @Test
    fun `the test can actually see the strings files`() {
        // Without this, a check that found nothing because it looked in the wrong directory would
        // pass silently — which is the usual way a rule like this stops being enforced.
        assertTrue("No strings.xml found to check", stringsFileFor("en").isFile)
        assertTrue("A language that is not offered must not be found", !stringsFileFor("xx").isFile)
    }

    @Test
    fun `the first guess is the phone's language, and English when it is not offered`() {
        val phone = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("de", defaultUiLanguage())

            // Turkish is on the INPUT-language list but the interface is not translated into it,
            // so the guess must not land there.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("en", defaultUiLanguage())
        } finally {
            Locale.setDefault(phone)
        }
    }
}
