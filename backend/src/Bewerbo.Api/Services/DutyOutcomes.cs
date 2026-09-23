using System.Text.RegularExpressions;
using Bewerbo.Api.Text;

namespace Bewerbo.Api.Services;

/// <summary>
/// Turns a duty the user listed under a position into the sentence a German reader weighs: a
/// result, not a responsibility.
///
/// German CVs say what was done — "Schulungen durchgeführt", "Kunden betreut" — where a CV
/// translated from elsewhere says what somebody was in charge of — "Verantwortlich für die
/// Durchführung von Schulungen". The two carry the same facts and read completely differently, and
/// the second is what makes an applicant look passive. The move is always the same one: drop the
/// responsibility framing and put the noun back into the verb it came from.
///
/// Rule-based on purpose, like <see cref="GapWording"/>: this is a fixed grammatical move on the
/// user's own words, not a free rewrite. Nothing is invented — no number, no achievement, no claim
/// the user did not type — and a line the move does not fit returns an empty string, so the caller
/// can say so rather than put a guess in front of a recruiter.
///
/// Whatever this returns is only ever a SUGGESTION: the user reads the original beside it and
/// decides which of the two reaches the document.
/// </summary>
public static class DutyOutcomes
{
    /// <summary>
    /// The nominal head of a duty and the participle it becomes, longest head first so that
    /// "Einführung" is not read as a compound of "Führung".
    /// </summary>
    private static readonly (string Noun, string Participle)[] Nouns =
    [
        ("Dokumentation", "dokumentiert"),
        ("Unterstützung", "unterstützt"),
        ("Durchführung", "durchgeführt"),
        ("Einarbeitung", "eingearbeitet"),
        ("Koordination", "koordiniert"),
        ("Beschaffung", "beschafft"),
        ("Bearbeitung", "bearbeitet"),
        ("Entwicklung", "entwickelt"),
        ("Organisation", "organisiert"),
        ("Optimierung", "optimiert"),
        ("Überwachung", "überwacht"),
        ("Auswertung", "ausgewertet"),
        ("Abwicklung", "abgewickelt"),
        ("Erstellung", "erstellt"),
        ("Einführung", "eingeführt"),
        ("Verwaltung", "verwaltet"),
        ("Betreuung", "betreut"),
        ("Umsetzung", "umgesetzt"),
        ("Kontrolle", "kontrolliert"),
        ("Reparatur", "repariert"),
        ("Beratung", "beraten"),
        ("Schulung", "geschult"),
        ("Planung", "geplant"),
        ("Wartung", "gewartet"),
        ("Prüfung", "geprüft"),
        ("Leitung", "geleitet"),
        ("Führung", "geführt"),
        ("Montage", "montiert"),
        ("Analyse", "analysiert"),
        ("Pflege", "gepflegt"),
    ];

    /// <summary>
    /// The phrases a duty is wrapped in. Stripping one is half the move on its own: what is left is
    /// the work itself, and the work is what the reader is after.
    /// </summary>
    private static readonly string[] Framings =
        ["Verantwortlich für", "Verantwortung für", "Zuständig für", "Zuständigkeit für", "Betraut mit"];

    /// <summary>
    /// The labels people put in front of a list. Unlike a <see cref="Framings"/> phrase these are
    /// ordinary words, so they only count as a label when a colon follows: "Tätigkeiten: Montage"
    /// is a heading, "Tätigkeiten im Lager" is the duty.
    /// </summary>
    private static readonly string[] Labels = ["Aufgaben", "Aufgabe", "Tätigkeiten", "Tätigkeit"];

    /// <summary>The articles that sit between the framing and the work, and inside the object.</summary>
    private static readonly string[] Articles =
        ["die", "der", "des", "den", "dem", "das", "eines", "einer", "einem", "einen", "eine", "ein"];

    /// <summary>
    /// The verbal particles a German noun begins with. A head whose compound prefix is one of these
    /// is a different noun, not a compound of the one in the table: "Überprüfung" is not a
    /// "Prüfung" of something called "Über", and "Anleitung" is not a "Leitung".
    /// </summary>
    private static readonly string[] Particles =
        ["über", "unter", "ein", "aus", "ver", "ent", "durch", "vor", "nach", "wieder", "rück"];

    /// <summary>
    /// The prepositions that merely attach the object to the noun and drop away with it. A
    /// local preposition — "Betreuung im Innendienst" — is not on this list: it belongs to the
    /// duty and stays, and its dative stays correct because it keeps the word that governs it.
    /// </summary>
    private static readonly string[] Prepositions = ["von", "für"];

    /// <summary>
    /// The endings of a plural that already ends in -n, so that its dative is the same word.
    ///
    /// After "von" the object stands in the dative, and the dative plural carries an -n a bullet
    /// must not: "Erstellung von Monatsberichten" is "Monatsberichte erstellt", not "Monatsberichten
    /// erstellt". Which of the two a word is cannot be read off its letters — "Kunden" IS the
    /// plural, "Berichten" is not — so the move is offered only where the two are the same word.
    /// Everything else gets no rewrite, which is the honest answer and the one this class gives
    /// everywhere else.
    /// </summary>
    private static readonly string[] DativeSafeEndings =
    [
        "ungen", "ionen", "heiten", "keiten", "schaften", "innen",
        "anten", "enten", "isten", "unden", "egen", "inen", "agen", "ien",
    ];

    /// <summary>
    /// The duty written as a result, or an empty string when the move does not fit this line.
    ///
    /// Empty rather than an echo of the input: a rewrite that changed nothing is not a choice the
    /// user can make, and pretending otherwise is how a suggestion turns into noise.
    /// </summary>
    public static string Rewrite(string? duty)
    {
        if (string.IsNullOrWhiteSpace(duty)) return "";

        // A line the user has not written in German cannot be moved into a German participle, and
        // this writer does not translate — the same line ScriptCheck draws for the letter.
        if (!ScriptCheck.IsLatin(duty)) return "";

        var original = duty.Trim();
        var rest = StripLeadingBullet(original).TrimEnd('.', ';', ',');
        // The label comes off first, so that "Aufgaben: Verantwortlich für den Empfang" is still
        // read as the claim it carries.
        StripLabel(ref rest);
        var claimed = StripResponsibility(ref rest);
        rest = StripArticle(rest);
        if (rest.Length == 0) return "";

        var head = rest.Split(' ', 2);
        var outcome = FromNoun(head[0], head.Length > 1 ? head[1].Trim() : "");

        // "Verantwortlich für den Empfang" carries no noun this table knows, but the user's own
        // framing already said what they did with it — so the active form is theirs, not invented.
        // ONLY a responsibility phrase licenses this. A list label says nothing about who answered
        // for what, and reading it as though it did turns "Tätigkeiten: Stapler fahren" into
        // "Stapler fahren verantwortet" — a sentence that is neither German nor the user's.
        if (outcome.Length == 0 && claimed) outcome = $"{rest} verantwortet";

        return outcome.Equals(original, StringComparison.Ordinal) ? "" : outcome;
    }

    /// <summary>
    /// Every duty line beside the result proposed for it, in the order they were typed. A line with
    /// no proposal keeps its place with an empty outcome, because the screen shows the two side by
    /// side and a dropped row would silently renumber them.
    /// </summary>
    public static IReadOnlyList<(string Original, string Outcome)> RewriteAll(IEnumerable<string> duties) =>
        duties.Select(d => (d, Rewrite(d))).ToList();

    private static string StripLeadingBullet(string line) => Regex.Replace(line, @"^[-–—•*]\s*", "");

    /// <summary>
    /// Removes a responsibility framing and says whether there was one. The answer is what licenses
    /// the "… verantwortet" fallback, so a list label is deliberately not stripped here.
    /// </summary>
    private static bool StripResponsibility(ref string line)
    {
        foreach (var framing in Framings)
        {
            if (!line.StartsWith(framing, StringComparison.OrdinalIgnoreCase)) continue;

            var after = line[framing.Length..].TrimStart(' ');
            if (after.Length == 0) continue;

            line = after;
            return true;
        }
        return false;
    }

    /// <summary>Removes a list label, which says nothing about the work beyond where it begins.</summary>
    private static void StripLabel(ref string line)
    {
        foreach (var label in Labels)
        {
            if (!line.StartsWith(label + ":", StringComparison.OrdinalIgnoreCase)) continue;

            var after = line[(label.Length + 1)..].TrimStart(' ');
            if (after.Length == 0) continue;

            line = after;
            return;
        }
    }

    private static string StripArticle(string line) => StripLeadingWord(line, Articles, out _);

    /// <summary>The line without its leading <paramref name="words"/> entry, which is handed back.</summary>
    private static string StripLeadingWord(string line, string[] words, out string? stripped)
    {
        var parts = line.Split(' ', 2);
        if (parts.Length == 2 && words.Contains(parts[0], StringComparer.OrdinalIgnoreCase))
        {
            stripped = parts[0].ToLowerInvariant();
            return parts[1].Trim();
        }
        stripped = null;
        return line.Trim();
    }

    /// <summary>
    /// What the noun governs, in the form a bullet may put in front of a participle — or an empty
    /// string when that form cannot be derived with certainty from what was typed.
    /// </summary>
    private static string ObjectOf(string phrase)
    {
        var rest = StripLeadingWord(phrase, Prepositions, out var preposition);
        rest = StripLeadingWord(rest, Articles, out var article);
        if (rest.Length == 0) return "";

        if (article is "des" or "eines")
        {
            // "Wartung des Fahrzeugs" is "Fahrzeug gewartet": the genitive -s belonged to the
            // article that has just gone. Only on a single word — in "des gesamten Fuhrparks" the
            // adjective carries the case too, and undoing that is not a letter-level move.
            if (rest.Contains(' ')) return "";
            rest = Regex.Replace(rest, "(es|s)$", "");
        }

        if (preposition == "von" && !IsDativeSafe(rest)) return "";

        return rest;
    }

    /// <summary>Whether the last word of an object reads the same in the dative as in the plural.</summary>
    private static bool IsDativeSafe(string phrase)
    {
        var last = phrase.Split(' ')[^1].ToLowerInvariant();
        return !last.EndsWith('n') || DativeSafeEndings.Any(e => last.EndsWith(e, StringComparison.Ordinal));
    }

    /// <summary>
    /// The participle behind the head noun, with whatever the noun was about put in front of it.
    ///
    /// The head is matched as the TAIL of a compound as well as on its own, the way
    /// <see cref="PostingParser"/> reads an institution word: "Kundenbetreuung im Innendienst" is
    /// the same duty as "Betreuung von Kunden im Innendienst", and is how most people write it.
    /// </summary>
    private static string FromNoun(string head, string tail)
    {
        foreach (var (noun, participle) in Nouns)
        {
            if (!head.EndsWith(noun, StringComparison.OrdinalIgnoreCase)) continue;

            var prefix = head[..^noun.Length].TrimEnd('s');
            if (prefix.Length > 0)
            {
                // Too short to be a word of its own, or a particle: this head is a different noun
                // that merely ends in the same letters, so no other entry may claim it either.
                if (prefix.Length < 3 || Particles.Contains(prefix.ToLowerInvariant())) return "";

                return string.Join(" ", new[] { prefix, tail, participle }.Where(s => s.Length > 0));
            }

            var subject = ObjectOf(tail);
            return subject.Length == 0 ? "" : $"{subject} {participle}";
        }
        return "";
    }
}
