package de.bewerbo.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.bewerbo.app.R
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

// ---------------------------------------------------------------------------------------------
// THE RULE, in one sentence:
//
//   A German word stays in the interface only when the user will meet THAT WORD outside the app —
//   in a job advert, in the documents an employer asks for, or as the name of an institution, a
//   law or a standard. Everything the app is merely naming for itself stands in the user's
//   language.
//
// Both halves matter. Translating "Anschreiben" away would rob the user of the word the advert
// uses; leaving "Zeitstrahl", "Abgleich" or "Maschinenlesbarkeit" in place taught them nothing and
// made every screen read as three languages at once. The list below is the whole of the German
// this interface is allowed to contain, and GermanTermsTest fails the build when a locale carries
// a German word that is not on it.
//
// A term is not simply dropped on the user either: each one is explained ONCE, in the user's own
// language, where it first appears — that is what [TermNote] is for.
// ---------------------------------------------------------------------------------------------

/**
 * One German word the interface keeps, with the one-line explanation shown where it first appears.
 *
 * [otherForms] are the inflections and compounds that mean the same term — "Zeugnisse" and
 * "Arbeitszeugnis" are the same word to a reader, and the test has to accept them without the
 * list growing an entry that needs its own explanation.
 */
data class GermanTerm(
    val word: String,
    val slug: String,
    @StringRes val gloss: Int,
    val otherForms: List<String> = emptyList(),
)

/// The closed list. Nothing German may appear in the interface that is not on it.
val GERMAN_TERMS = listOf(
    GermanTerm(
        "Bewerbungsmappe", "bewerbungsmappe", R.string.term_bewerbungsmappe,
        listOf("Bewerbungsunterlagen"),
    ),
    GermanTerm("Lebenslauf", "lebenslauf", R.string.term_lebenslauf),
    GermanTerm("Anschreiben", "anschreiben", R.string.term_anschreiben),
    GermanTerm("Motivationsschreiben", "motivationsschreiben", R.string.term_motivationsschreiben),
    GermanTerm("Anlagenverzeichnis", "anlagenverzeichnis", R.string.term_anlagenverzeichnis),
    GermanTerm(
        "Zeugnis", "zeugnis", R.string.term_zeugnis,
        listOf("Zeugnisse", "Arbeitszeugnis", "Arbeitszeugnisse"),
    ),
    GermanTerm(
        "Nachweis", "nachweis", R.string.term_nachweis,
        listOf("Nachweise", "Sprachnachweis"),
    ),
    GermanTerm("Referenznummer", "referenznummer", R.string.term_referenznummer),
    GermanTerm("anabin", "anabin", R.string.term_anabin),
    GermanTerm("DIN 5008", "din_5008", R.string.term_din_5008, listOf("DIN")),
    GermanTerm("AGG", "agg", R.string.term_agg),
    // The three the legal pages brought with them. Each is met outside the app before it is met
    // inside it: every German service footer carries AGB and Impressum, and DSGVO is the name of
    // the law the user's own rights come from.
    GermanTerm("AGB", "agb", R.string.term_agb),
    GermanTerm("Impressum", "impressum", R.string.term_impressum),
    GermanTerm("DSGVO", "dsgvo", R.string.term_dsgvo),
)

/// The term by its [slug], so a screen names the one it introduces rather than an index.
fun germanTerm(slug: String): GermanTerm =
    GERMAN_TERMS.first { it.slug == slug }

/**
 * The explanation of a kept German term, on the screen where the user first meets it.
 *
 * Deliberately a muted line rather than a [de.bewerbo.app.ui.components.Callout]: a callout is the
 * app raising something, and these are footnotes. Nine of them in boxes would turn every screen
 * into a tutorial. It matches the note under the Referenz field on the Stellenanzeige screen,
 * which is the same kind of aside.
 */
@Composable
fun TermNote(term: GermanTerm, modifier: Modifier = Modifier) {
    Text(
        "${term.word} — ${stringResource(term.gloss)}",
        style = MaterialTheme.typography.bodySmall,
        color = LocalSemanticColors.current.muted,
        modifier = modifier
            .padding(top = Space.xs)
            .testTag("term_note_${term.slug}"),
    )
}
