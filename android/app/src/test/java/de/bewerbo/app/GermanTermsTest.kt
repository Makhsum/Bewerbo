package de.bewerbo.app

import de.bewerbo.app.ui.GERMAN_TERMS
import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The one rule for which German words stay, with an enforcement mechanism rather than a promise.
 *
 * The interface used to speak three languages at once — "Создать Anschreiben", "Zeitstrahl
 * 2026 – 2026", a Russian screen with a tab called "Mappe" — because two half-truths were both
 * written down: that the German terms of art stay German, and that everything is translated.
 * Neither said WHICH words, so every new string was a fresh judgement call and half of them went
 * the wrong way.
 *
 * The rule is in ui/GermanTerms.kt: a German word stays only when the user will meet that word
 * outside the app. This fails the build when a locale carries one that is not on the list, the
 * same way [EmojiFreeStringsTest] does for emoji and [UiLanguagesTest] does for a language
 * offered without resources.
 */
class GermanTermsTest {

    /// The default locale's resources live in values/, every other one in values-<tag>/.
    // The test runs with the module directory as the working directory.
    private fun resourceDirFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag")

    private fun stringsFileFor(tag: String): File = File(resourceDirFor(tag), "strings.xml")

    private fun pluralsFileFor(tag: String): File = File(resourceDirFor(tag), "plurals.xml")

    /// Latin words that are not German and not a term: the product's own name, file formats, the
    /// country codes in an example, and the date placeholders of a hint.
    private val ALLOWED_WORDS = setOf(
        "Bewerbo", "PDF", "JPEG", "PNG", "KB", "MM", "YYYY", "JJJJ", "UA", "RU", "DE", "EU",
    )

    /// The German chrome this card translated away. Naming it is what keeps it from drifting back
    /// one string at a time; values-de is exempt, because there it is simply German.
    private val TRANSLATED_AWAY = listOf(
        "Zeitstrahl", "Abgleich", "Berufserfahrung", "Ausbildung", "Maschinenlesbarkeit",
        "Textprüfung", "Betreffzeile", "Pensum", "Unternehmen", "Arbeitgebertyp", "Eintritt",
        "Anerkennung", "Vorlage", "Lücke", "Beruf", "Konzern", "Mittelstand", "Entwurf",
        "Versendet", "Wartend", "Einladung", "Absage", "Klassisch", "Fachlich", "Sachlich",
    )

    private fun localeTags(): List<String> = UI_LANGUAGES.map { it.first }

    /// Every form of every kept term: the word itself and the inflections the list declares.
    private fun keptWords(): Set<String> =
        GERMAN_TERMS.flatMap { listOf(it.word) + it.otherForms }
            .flatMap { it.split(" ") }
            .toSet()

    /// The values of the <string> elements of one file, by key, minus what is not the app's own
    /// prose: a string marked translatable="false" is the German of the letter itself, and the
    /// text inside »…« is quoted from that letter rather than written to the user.
    private fun translatableStrings(file: File): Map<String, String> {
        val body = file.readText()
        return Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .filterNot { it.groupValues[2].contains("translatable=\"false\"") }
            .associate { it.groupValues[1] to it.groupValues[3] }
    }

    /// The <item> texts of one plurals.xml, keyed "name/quantity".
    ///
    /// A count is user-facing prose like any other string — "1 Lücke" on a Russian screen is the
    /// same defect as "LÜCKE" is — and this file was outside the scan until now, so the rule held
    /// everywhere except the one place a number is written out.
    private fun pluralItems(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .flatMap { plural ->
                Regex("""<item quantity="([^"]+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(plural.groupValues[2])
                    .map { "${plural.groupValues[1]}/${it.groupValues[1]}" to it.groupValues[2] }
            }
            .toMap()
    }

    /// Everything one locale puts in front of the user: both resource files, one map.
    private fun userFacingText(tag: String): Map<String, String> =
        translatableStrings(stringsFileFor(tag)) + pluralItems(pluralsFileFor(tag))

    private fun scannableText(value: String): String = value
        .replace(Regex("""»[^«]*«"""), " ")
        .replace(Regex("""%\d+\$[a-z]"""), " ")
        .replace("%%", " ")

    private fun latinWordsIn(value: String): List<String> =
        Regex("""[A-Za-zÄÖÜäöüß]{2,}""").findAll(scannableText(value)).map { it.value }.toList()

    @Test
    fun `every string is translated in every language the picker offers`() {
        val keys = translatableStrings(stringsFileFor("en")).keys
        val pluralNames = pluralItems(pluralsFileFor("en")).keys.map { it.substringBefore("/") }.toSet()
        val missing = mutableListOf<String>()

        localeTags().filterNot { it == "en" }.forEach { tag ->
            val translated = translatableStrings(stringsFileFor(tag)).keys
            (keys - translated).forEach { missing += "values-$tag/strings.xml: $it" }

            // A plural falls back as a whole, so what has to exist is the name, not every
            // quantity: Russian needs four where English has two.
            val counts = pluralItems(pluralsFileFor(tag)).keys.map { it.substringBefore("/") }.toSet()
            (pluralNames - counts).forEach { missing += "values-$tag/plurals.xml: $it" }
        }

        // A key that falls back used to fall back to a GERMAN default, so the gap did not look
        // like a gap — it looked like a term of art. That is where "VORLAGE" and "Zeitstrahl"
        // came from on a Russian screen.
        assertTrue(
            "These strings have no translation and would fall back to the default locale:\n" +
                missing.sorted().joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun `a Cyrillic locale carries no German word that is not a kept term`() {
        val allowed = keptWords() + ALLOWED_WORDS
        val offenders = mutableListOf<String>()

        listOf("ru", "uk").forEach { tag ->
            userFacingText(tag).forEach { (key, value) ->
                latinWordsIn(value)
                    .filterNot { it in allowed }
                    .forEach { offenders += "values-$tag: $key carries \"$it\"" }
            }
        }

        assertTrue(
            "A German word may stand in the user's sentence only if it is on the list in " +
                "ui/GermanTerms.kt — see the rule there. Add it with its explanation, or " +
                "translate it:\n" + offenders.sorted().joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the German chrome that was translated away does not come back`() {
        val offenders = mutableListOf<String>()

        localeTags().filterNot { it == "de" }.forEach { tag ->
            userFacingText(tag).forEach { (key, value) ->
                val text = scannableText(value)
                TRANSLATED_AWAY.forEach { word ->
                    if (Regex("""\b$word\b""").containsMatchIn(text)) {
                        offenders += "values-$tag: $key carries \"$word\""
                    }
                }
            }
        }

        assertTrue(
            "These words describe what the app is doing, not what a posting says. They belong " +
                "in the user's language:\n" + offenders.sorted().joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every kept term is explained in every language`() {
        val missing = mutableListOf<String>()

        localeTags().forEach { tag ->
            val strings = translatableStrings(stringsFileFor(tag))
            GERMAN_TERMS.forEach { term ->
                val key = "term_${term.slug}"
                if (strings[key].isNullOrBlank()) {
                    missing += "values-$tag/strings.xml: $key"
                }
            }
        }

        // A term with no explanation is the half of the rule that is easy to forget: keeping the
        // German word is only defensible because the user is told once what it means.
        assertTrue(
            "These kept terms have no explanation, so TermNote would draw the word alone:\n" +
                missing.sorted().joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun `the test can actually see the strings files`() {
        // Without this, a scan that found nothing because it looked in the wrong directory would
        // pass silently — which is the usual way a rule like this stops being enforced.
        localeTags().forEach {
            assertTrue("values for '$it' not found", stringsFileFor(it).isFile)
        }
        assertTrue("A locale we do not have must not be found", !stringsFileFor("xx").isFile)
        assertTrue(
            "No strings read out of values/strings.xml",
            translatableStrings(stringsFileFor("en")).size > 100,
        )
        // The plurals were outside the scan once. A reader that finds nothing in them would pass
        // every file just as silently as one pointed at the wrong directory.
        localeTags().forEach {
            assertTrue("No plurals read out of values-$it", pluralItems(pluralsFileFor(it)).size >= 8)
        }
    }

    @Test
    fun `the detector recognises what it is looking for`() {
        // The guard on the guard: a scanner that matches nothing passes every file.
        assertTrue("Latin word not detected", latinWordsIn("Файл Zeitstrahl тут").contains("Zeitstrahl"))
        assertTrue("Umlaut word not detected", latinWordsIn("Eine Lücke").contains("Lücke"))

        // And what is legitimately not a German word must not be flagged.
        assertTrue("Format specifier flagged", latinWordsIn("Шаг %1\$d из %2\$d").isEmpty())
        assertTrue("Escaped percent flagged", latinWordsIn("Заполнено на %1\$d %%").isEmpty())
        assertTrue("Quoted letter text flagged", latinWordsIn("никакого »Hiermit bewerbe ich«").isEmpty())
        assertTrue("Cyrillic flagged", latinWordsIn("Создать письмо").isEmpty())

        // And the kept terms have to be recognised as kept, or the second test flags every one.
        val kept = keptWords()
        listOf("Anschreiben", "Lebenslauf", "anabin", "Zeugnisse", "Sprachnachweis", "DIN", "AGG")
            .forEach { assertTrue("'$it' not recognised as a kept term", it in kept) }
        assertTrue("'Zeitstrahl' must not be a kept term", "Zeitstrahl" !in kept)
    }
}
