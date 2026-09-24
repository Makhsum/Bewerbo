package de.bewerbo.app

import de.bewerbo.app.ui.UI_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The last sentence read before the data is gone has to be true, in every language.
 *
 * It was not: the confirmation promised that Bewerbo opens an empty account afterwards, from the
 * build in which there was no door and an account was something the app made for itself. Deleting
 * the account now ends the session — [de.bewerbo.app.data.AppViewModel.deleteAccount] forgets it on
 * this device — and the user lands on the sign-in screen. A wrong promise in front of something
 * irreversible is the one place where "somebody will notice" is not good enough, so it gets a gate
 * rather than a corrected sentence, the same way the emoji rule does in [EmojiFreeStringsTest] and
 * the German terms do in [GermanTermsTest].
 */
class DeleteAccountDialogTest {

    /// The default locale's resources live in values/, every other one in values-<tag>/ — the same
    /// mapping [LegalPagesTest] uses, and for the same reason: there is no values-en.
    private fun stringsFileFor(tag: String): File =
        File(if (tag == "en") "src/main/res/values" else "src/main/res/values-$tag", "strings.xml")

    private fun stringValue(tag: String, key: String): String =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFileFor(tag).readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()

    /// Where the user lands, in the words each language already uses for that screen — signin_title
    /// is "Anmelden", "Вход", "Вхід". The check is on the word, not on a whole sentence: how the
    /// sentence is phrased is the writer's business, that it names the door is not.
    private val NAMES_THE_DOOR = mapOf(
        "en" to "sign-in",
        "de" to "Anmelde",
        "ru" to "вход",
        "uk" to "вход",
    )

    /// The promise that is now false, in the words it was written in. Named rather than merely
    /// deleted, because a sentence nobody is stopping comes back the next time this dialog is
    /// reworded — the same reason GermanTermsTest keeps its list of translated-away words.
    private val PROMISES_AN_EMPTY_ACCOUNT = mapOf(
        "en" to listOf("empty account", "begin again"),
        "de" to listOf("leeres Konto", "neu beginnen"),
        "ru" to listOf("пустой аккаунт", "начать заново"),
        "uk" to listOf("порожній обліковий запис", "почати заново"),
    )

    private fun localeTags(): List<String> = UI_LANGUAGES.map { it.first }

    @Test
    fun `the confirmation no longer promises an account to begin again in`() {
        val offenders = mutableListOf<String>()

        localeTags().forEach { tag ->
            val body = stringValue(tag, "settings_delete_body")
            PROMISES_AN_EMPTY_ACCOUNT.getValue(tag).forEach { claim ->
                if (body.contains(claim, ignoreCase = true)) {
                    offenders += "values-$tag: settings_delete_body promises \"$claim\""
                }
            }
        }

        assertTrue(
            "Deleting the account ends the session, so the dialog must not announce an account " +
                "the user is put into afterwards:\n" + offenders.sorted().joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the confirmation says where the user lands, in every language the picker offers`() {
        val silent = localeTags().filterNot { tag ->
            stringValue(tag, "settings_delete_body")
                .contains(NAMES_THE_DOOR.getValue(tag), ignoreCase = true)
        }

        assertTrue(
            "These languages describe the deletion without saying what follows it, which is the " +
                "half of the sentence the user is deciding on: $silent",
            silent.isEmpty(),
        )
    }

    @Test
    fun `the test can actually see what it is reading`() {
        // Without this, a check that found nothing because it looked in the wrong directory or
        // under a renamed key would pass every locale silently.
        localeTags().forEach { tag ->
            assertTrue(
                "values for '$tag' carry no settings_delete_body",
                stringValue(tag, "settings_delete_body").length > 40,
            )
            assertTrue("The dialog's title is missing in '$tag'", stringValue(tag, "settings_delete_title").isNotBlank())
        }
        assertTrue("A key that does not exist must read as empty", stringValue("en", "settings_delete_nothing").isEmpty())
    }

    @Test
    fun `every language the picker offers is covered by both lists`() {
        // A fifth language would otherwise arrive with no false promise to look for and no word to
        // land on, and both checks would pass it without reading a thing.
        assertTrue(
            "Languages with no wrong promise to guard against: ${localeTags() - PROMISES_AN_EMPTY_ACCOUNT.keys}",
            localeTags().all { it in PROMISES_AN_EMPTY_ACCOUNT },
        )
        assertTrue(
            "Languages with no word for the door: ${localeTags() - NAMES_THE_DOOR.keys}",
            localeTags().all { it in NAMES_THE_DOOR },
        )
    }
}
