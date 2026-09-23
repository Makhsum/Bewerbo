using System.Text.RegularExpressions;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;

namespace Bewerbo.Api.Services;

/// <summary>
/// One document the posting asks to see, and whether the Mappe can produce it.
/// </summary>
public record DemandedDocument(
    DocumentKind Kind,
    /// <summary>What to file, in the words the Anlagenverzeichnis will use for it.</summary>
    string Title,
    /// <summary>
    /// What follows the kind's name in <paramref name="Title"/> — the language and level of a
    /// Sprachnachweis, and nothing for the other kinds. The screen writes the kind in the user's
    /// language and appends these, because two of the four kinds are not German terms.
    /// </summary>
    IReadOnlyList<string> TitleArgs,
    /// <summary>
    /// The posting's own sentence that asks for it. The demand is only worth showing if the user
    /// can check where it comes from — the same discipline the extracted fields are held to.
    /// </summary>
    string Quote,
    /// <summary>False means outstanding: the posting wants it and the Mappe has nothing for it.</summary>
    bool OnFile);

/// <summary>
/// Which documents a posting demands, and which of them the Mappe already holds.
///
/// This is the other half of what a German advert asks. The Abgleich answers "can this applicant
/// do the job"; a posting also says which papers it wants to see, and that is not the same list —
/// an advert that states no requirement at all still ends in "Bitte senden Sie uns Ihre
/// Zeugnisse", and that closing sentence is deliberately cut off the requirements list.
///
/// So the demand is read from the whole advert text, not only from the extracted requirements.
/// A Sprachnachweis is the exception: which language and level is wanted is a requirement-level
/// reading, and it is the one the Abgleich already performs.
/// </summary>
public static class DocumentDemandService
{
    public static IReadOnlyList<DemandedDocument> Demands(
        Profile profile, string sourceText, IEnumerable<ExtractedRequirement> requirements)
    {
        var requirementTexts = requirements.Select(r => r.Text).ToList();
        // The advert and its requirements are searched as one body of text: a posting that lists
        // "Zeugnisse" as a bullet and one that asks for them in its closing line make the same
        // demand, and the user should not see a different Mappe for the two.
        var haystack = string.Join("\n", requirementTexts.Prepend(sourceText));

        var demands = new List<DemandedDocument>();

        // The Mappe's own order, so the section reads the same way the list below it does.
        foreach (var (kind, pattern) in Patterns)
        {
            var match = Regex.Match(haystack, pattern, RegexOptions.IgnoreCase);
            if (!match.Success) continue;

            demands.Add(new DemandedDocument(
                kind, KindTitle(kind), [], Sentence(haystack, match.Index),
                profile.Documents.Any(d => d.Kind == kind)));
        }

        demands.AddRange(LanguageDemands(profile, requirementTexts));
        return demands;
    }

    /// <summary>
    /// What asks for a document, per kind. Anchored on word boundaries throughout: without them
    /// "Zeugnis" matches inside "Arbeitszeugnisse" and every posting demands both.
    /// </summary>
    private static readonly (DocumentKind Kind, string Pattern)[] Patterns =
    [
        // "vollständige Bewerbungsunterlagen" is the German convention for "with your Zeugnisse",
        // and it is how most adverts ask, so it counts as the same demand.
        (DocumentKind.Arbeitszeugnis,
            @"\b(Arbeitszeugnis\w*|Zeugnis\w*|Referenzen|Referenzschreiben|vollständige\w*\s+(Bewerbungs)?unterlagen)\b"),
        (DocumentKind.Zertifikat,
            @"\b(Zertifikat\w*|Fortbildungsnachweis\w*|Weiterbildungsnachweis\w*|Qualifikationsnachweis\w*)\b"),
        // Bare "Anerkennung" is NOT a demand for a document: "Wertschätzung und Anerkennung" is a
        // stock phrase in the benefits half of a German advert, and it would have put an
        // outstanding anabin extract on the Mappe of every applicant who read one.
        (DocumentKind.AnabinAuszug,
            @"\b(anabin|ZAB|Zeugnisbewertung|Anerkennungsbescheid|Gleichwertigkeit\w*)\b"
            + @"|\bAnerkennung\b.{0,40}?\b(Abschluss\w*|Berufsqualifikation\w*|Diplom\w*)\b"),
    ];

    /// <summary>
    /// A Sprachnachweis is demanded per language, because that is how it is filed. Whether it is on
    /// file is read from the language's own CertificateOnFile flag and NOT from a document of kind
    /// Sprachnachweis in the Mappe: the flag is what the Abgleich, the Readiness score and the
    /// letter writer all read, so anything else would say "on file" on a screen while the
    /// requirement it belongs to stayed offen.
    /// </summary>
    private static IEnumerable<DemandedDocument> LanguageDemands(
        Profile profile, IReadOnlyList<string> requirements)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var requirement in requirements)
        {
            var wanted = RequirementMatcher.WantedLanguage(requirement);
            if (wanted is null || !seen.Add(wanted.Value.Language)) continue;

            var skill = profile.Languages.FirstOrDefault(l =>
                l.Language.Contains(wanted.Value.Language, StringComparison.OrdinalIgnoreCase));

            yield return new DemandedDocument(
                DocumentKind.Sprachnachweis,
                // The title run 102 files a Nachweis under, so the row names the document that
                // would close it rather than a category.
                $"Sprachnachweis {wanted.Value.Language} {wanted.Value.Level}",
                [wanted.Value.Language, wanted.Value.Level],
                requirement,
                skill?.CertificateOnFile ?? false);
        }
    }

    /// <summary>The kind's own German name — the word the Anlagenverzeichnis prints for its group.</summary>
    private static string KindTitle(DocumentKind kind) => kind switch
    {
        DocumentKind.Arbeitszeugnis => "Arbeitszeugnis",
        DocumentKind.Zertifikat => "Zertifikat",
        DocumentKind.Sprachnachweis => "Sprachnachweis",
        DocumentKind.AnabinAuszug => "anabin-Auszug",
        _ => "Unterlage",
    };

    /// <summary>
    /// The sentence the match sits in, so the row can quote the demand instead of asserting it.
    /// Cut at the sentence marks a posting actually uses, and capped: a bullet list written as one
    /// paragraph would otherwise put the whole advert on one row.
    /// </summary>
    private static string Sentence(string text, int index)
    {
        var start = text.LastIndexOfAny(['.', '!', '?', '\n', ';', ':'], Math.Max(index - 1, 0)) + 1;
        var end = text.IndexOfAny(['.', '!', '?', '\n', ';'], index);
        if (end < 0) end = text.Length;

        // A bullet is where the line begins, not part of what it says, so the marker is dropped:
        // the quote is shown to the user as a sentence.
        var sentence = text[start..end].Trim().TrimStart('-', '*', '•', '–', '—').Trim();
        return sentence.Length <= 120 ? sentence : sentence[..119].TrimEnd() + "…";
    }
}
