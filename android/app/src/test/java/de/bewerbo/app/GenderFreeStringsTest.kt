package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rule that the interface never decides what gender the user is, with an enforcement
 * mechanism rather than a promise — for the two languages that are able to break it.
 *
 * Russian and Ukrainian inflect a past-tense verb and a short adjective for the gender of whoever
 * it is about. "Я забыл пароль" and "Я забув пароль" are the masculine forms, and that is what the
 * way back into the account said: a woman read a sentence about herself she would not have
 * written. English and German carry no gender in that button at all, so neither of them could hint
 * at it, and the button went out that way.
 *
 * What this scans for is deliberately narrow. A past-tense verb about a THING is correct and the
 * files are full of it — "страница не ответила", "ZAB ещё не прислала оценку", "это письмо
 * написала языковая модель". Nothing in the shape of a word says whether it is about a thing or
 * about the reader, so a scan over endings would flag those three and be switched off within a
 * week. What CAN be told apart is the vocabulary: the forms below are ones only a person ever
 * takes, so any of them in a user-facing string is about the user.
 *
 * The voices that stay available are the ones the app already speaks in: the first person PRESENT
 * ("Не помню пароль", "Не пам'ятаю пароль"), a phrase with no verb to inflect ("У меня уже есть
 * аккаунт"), and the formal plural the rest of the interface uses ("причины, которые вы указали").
 * All three are first person or second person without choosing a gender.
 *
 * When a new string needs a person-verb this list does not have yet, add BOTH its forms here — the
 * feminine one is as wrong as the masculine when the reader turns out to be a man. It fails the
 * build the same way [EmojiFreeStringsTest] does for an emoji and [GermanTermsTest] does for a
 * German word that is not a kept term.
 */
class GenderFreeStringsTest {

    /// The two locales whose grammar can carry the defect. English and German cannot.
    private val GENDERED_LOCALES = listOf("ru", "uk")

    /// Forms only a person ever takes, both genders, for the actions a sign-in, a profile and a
    /// document list reach for. A thing is never "забыл", "уверена" or "зареєструвався".
    private val GENDERED_SELF_FORMS = setOf(
        // ru — the past tense of what the user does
        "забыл", "забыла", "помнил", "помнила", "вошёл", "вошел", "вошла", "вышел", "вышла",
        "ввёл", "ввел", "ввела", "выбрал", "выбрала", "загрузил", "загрузила",
        "прикрепил", "прикрепила", "потерял", "потеряла",
        "зарегистрировался", "зарегистрировалась", "согласился", "согласилась",
        // ru — the short adjectives and participles said about a person
        "уверен", "уверена", "согласен", "согласна", "авторизован", "авторизована",
        // uk — the past tense of what the user does
        "забув", "забула", "пам'ятав", "пам'ятала", "увійшов", "увійшла", "вийшов", "вийшла",
        "ввів", "вибрав", "вибрала", "завантажив", "завантажила",
        "прикріпив", "прикріпила", "загубив", "загубила",
        "зареєструвався", "зареєструвалася", "погодився", "погодилася",
        // uk — the short adjectives and participles said about a person
        "впевнений", "впевнена", "згоден", "згодна", "авторизований", "авторизована",
    )

    /// Both locales this scans are translations, so both live in values-<tag>/.
    // The test runs with the module directory as the working directory.
    private fun resourceDirFor(tag: String): File = File("src/main/res/values-$tag")

    private fun stringsFileFor(tag: String): File = File(resourceDirFor(tag), "strings.xml")

    private fun pluralsFileFor(tag: String): File = File(resourceDirFor(tag), "plurals.xml")

    /// The values of the <string> elements of one file, by key. A string marked
    /// translatable="false" is the German of the letter itself and is not spoken to the user.
    private fun translatableStrings(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .filterNot { it.groupValues[2].contains("translatable=\"false\"") }
            .associate { it.groupValues[1] to it.groupValues[3] }
    }

    /// The <item> texts of one plurals.xml, keyed "name/quantity". A count is user-facing prose
    /// like any other string, and it is where a sentence about the user can hide from a scan.
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

    /// The Cyrillic words of one value, lower-cased and compared whole: "ввели" is the formal
    /// plural and correct, "ввёл" is not, and a substring search cannot keep them apart. The
    /// apostrophe belongs to the Ukrainian word — it is written \' in the file and ’ in prose,
    /// and both are the same letter position here.
    private fun cyrillicWordsIn(value: String): List<String> = value
        .replace("\\", "")
        .replace('’', '\'')
        .let { Regex("""[Ѐ-ӿ']+""").findAll(it).map { m -> m.value.lowercase() }.toList() }

    @Test
    fun `no Cyrillic locale says something about the user in a gendered form`() {
        val offenders = mutableListOf<String>()

        GENDERED_LOCALES.forEach { tag ->
            userFacingText(tag).forEach { (key, value) ->
                cyrillicWordsIn(value)
                    .filter { it in GENDERED_SELF_FORMS }
                    .forEach { offenders += "values-$tag: $key says \"$it\"" }
            }
        }

        assertTrue(
            "These strings pick a gender for the reader. Russian and Ukrainian have no neutral " +
                "past tense, so say it in the present instead (\"Не помню пароль\"), or in the " +
                "formal plural the rest of the interface uses:\n" +
                offenders.sorted().joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the test can actually see the strings files`() {
        // Without this, a scan that found nothing because it looked in the wrong directory would
        // pass silently — which is the usual way a rule like this stops being enforced.
        GENDERED_LOCALES.forEach { tag ->
            assertTrue("values-$tag/strings.xml not found", stringsFileFor(tag).isFile)
            assertTrue(
                "No strings read out of values-$tag",
                translatableStrings(stringsFileFor(tag)).size > 100,
            )
            assertTrue("No plurals read out of values-$tag", pluralItems(pluralsFileFor(tag)).size >= 8)
        }
    }

    @Test
    fun `the detector recognises what it is looking for`() {
        // The guard on the guard: a scanner that matches nothing passes every file. These two are
        // the sentences this rule was written for.
        assertTrue(
            "The masculine Russian form is not detected",
            cyrillicWordsIn("Я забыл пароль").any { it in GENDERED_SELF_FORMS },
        )
        assertTrue(
            "The masculine Ukrainian form is not detected",
            cyrillicWordsIn("Я забув пароль").any { it in GENDERED_SELF_FORMS },
        )
        assertTrue(
            "The feminine form is not detected either",
            cyrillicWordsIn("Я забыла пароль").any { it in GENDERED_SELF_FORMS },
        )
        assertTrue(
            "The escaped Ukrainian apostrophe breaks the word apart",
            cyrillicWordsIn("Я не пам\\'ятав пароль").any { it in GENDERED_SELF_FORMS },
        )

        // And what the app legitimately says must not be flagged: the wording that replaced it,
        // the formal plural, and a past tense about a thing rather than about the reader.
        assertTrue(
            "The present-tense wording is flagged",
            cyrillicWordsIn("Не помню пароль").none { it in GENDERED_SELF_FORMS },
        )
        assertTrue(
            "The Ukrainian present-tense wording is flagged",
            cyrillicWordsIn("Не пам\\'ятаю пароль").none { it in GENDERED_SELF_FORMS },
        )
        assertTrue(
            "The formal plural is flagged",
            cyrillicWordsIn("причины, которые вы ввели и указали").none { it in GENDERED_SELF_FORMS },
        )
        assertTrue(
            "A past tense about a thing is flagged",
            cyrillicWordsIn("Это письмо написала языковая модель").none { it in GENDERED_SELF_FORMS },
        )
    }
}
