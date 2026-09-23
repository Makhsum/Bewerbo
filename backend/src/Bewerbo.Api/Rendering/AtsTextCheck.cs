using System.Text.RegularExpressions;
using Bewerbo.Api.Domain;
using UglyToad.PdfPig;

namespace Bewerbo.Api.Rendering;

/// <summary>
/// One thing a Bewerbermanagementsystem indexes on. <see cref="Key"/> names the check and
/// <see cref="DetailKind"/> what was found, so the screen writes both in the user's language;
/// <see cref="Label"/> and <see cref="Detail"/> keep the German as the fallback.
///
/// <see cref="Verdict"/> is "ok", "fehler" or "ungeprueft" — the same three-way shape and the same
/// two first words as <see cref="Text.ReviewCheck"/>. It used to be a bool, and a bool cannot say
/// the third thing: that the check could not be run at all because the profile field it reads is
/// empty. That is not a fault of the produced document, and marking it as one taught the user that
/// a red mark means nothing. <see cref="Target"/> is the screen the empty field is filled in on,
/// written the way <see cref="Contracts.NextStepDto"/> writes it.
/// </summary>
public record AtsFinding(
    string Key,
    string Label,
    string Verdict,
    string Detail,
    string DetailKind = "",
    IReadOnlyList<string>? DetailArgs = null,
    string Target = "");

public record AtsResult(int PageCount, int SizeBytes, IReadOnlyList<AtsFinding> Findings)
{
    public bool Passed => Findings.All(f => f.Verdict != "fehler");
}

/// <summary>
/// Reads the finished PDF back and checks that the things a Bewerbermanagementsystem indexes on are
/// still there as text.
///
/// This is the only check in the product that looks at the actual output rather than at the data it
/// was rendered from. A PDF that looks right and extracts to nothing is the single most expensive
/// failure here: the application is filtered out before a human ever opens it, and nobody is told.
///
/// Which is exactly why an empty profile must not fail it. Three of the five checks read a profile
/// field and look for it in the file; with nothing to look for there is no reading, and a "fehler"
/// on all three buries the one that is real.
/// </summary>
public static class AtsTextCheck
{
    /// The screen the three profile-fed checks lead to when the field they read is empty.
    private const string ProfileTarget = "profil";

    public static AtsResult Run(byte[] pdf, Profile profile)
    {
        string text;
        int pageCount;

        try
        {
            using var document = PdfDocument.Open(pdf);
            pageCount = document.NumberOfPages;
            text = string.Join("\n", document.GetPages().Select(p => p.Text));
        }
        catch (Exception ex)
        {
            return new AtsResult(0, pdf.Length,
            [
                new AtsFinding("lesbar", "PDF zurückgelesen", "fehler",
                    $"Die Datei ließ sich nicht als Text öffnen: {ex.Message}",
                    "lesbar_fehler", [ex.Message]),
            ]);
        }

        // PdfPig returns the glyph run without the spaces the eye supplies, so the comparison is
        // made on whitespace-stripped text. "Olena Kovalchuk" extracted as "OlenaKovalchuk" is a
        // pass: an ATS tokenises, it does not read.
        var compact = Regex.Replace(text, @"\s+", "");

        bool Contains(string? needle) =>
            !string.IsNullOrWhiteSpace(needle) &&
            compact.Contains(Regex.Replace(needle, @"\s+", ""), StringComparison.OrdinalIgnoreCase);

        var findings = new List<AtsFinding>();

        // The profile has a name when BOTH halves are there, the same condition
        // ReadinessService uses for the "person" step — the Briefkopf is made of both.
        var nameInProfile = !string.IsNullOrWhiteSpace(profile.FirstName)
                            && !string.IsNullOrWhiteSpace(profile.LastName);
        var nameFound = nameInProfile && Contains($"{profile.FirstName}{profile.LastName}");
        findings.Add(new AtsFinding("name", "Name als Text wiedergefunden",
            !nameInProfile ? "ungeprueft" : nameFound ? "ok" : "fehler",
            !nameInProfile
                ? "Kein Name im Profil"
                : nameFound
                    ? $"{profile.FirstName} {profile.LastName}"
                    : "Der Name ist im Text nicht auffindbar",
            !nameInProfile ? "name_keine" : nameFound ? "gefunden" : "name_fehlt",
            nameFound ? [$"{profile.FirstName} {profile.LastName}"] : [],
            !nameInProfile ? ProfileTarget : ""));

        var employers = profile.Experience.Select(e => e.Employer).Where(e => !string.IsNullOrWhiteSpace(e)).ToList();
        var employersFound = employers.Count > 0 && employers.All(Contains);
        findings.Add(new AtsFinding("arbeitgeber", "Arbeitgeber als Text wiedergefunden",
            employers.Count == 0 ? "ungeprueft" : employersFound ? "ok" : "fehler",
            employers.Count == 0
                ? "Keine Arbeitgeber im Profil"
                : employersFound
                    ? string.Join(", ", employers)
                    : "Fehlt: " + string.Join(", ", employers.Where(e => !Contains(e))),
            employers.Count == 0 ? "arbeitgeber_keine" : employersFound ? "gefunden" : "fehlt",
            employers.Count == 0
                ? []
                : employersFound
                    ? [string.Join(", ", employers)]
                    : [string.Join(", ", employers.Where(e => !Contains(e)))],
            employers.Count == 0 ? ProfileTarget : ""));

        // Dates are what an ATS builds the career timeline from; a CV whose periods do not extract
        // reads as a career with no dates at all.
        var periods = profile.Experience.Select(e => $"{e.From:MM/yyyy}").ToList();
        var datesFound = periods.Count > 0 && periods.All(Contains);
        findings.Add(new AtsFinding("zeitraeume", "Zeiträume als Text wiedergefunden",
            periods.Count == 0 ? "ungeprueft" : datesFound ? "ok" : "fehler",
            periods.Count == 0
                ? "Keine Zeiträume im Profil"
                : datesFound ? string.Join(", ", periods) : "Mindestens ein Zeitraum extrahiert nicht",
            periods.Count == 0 ? "zeitraeume_keine" : datesFound ? "gefunden" : "zeitraeume_fehlt",
            periods.Count == 0 || !datesFound ? [] : [string.Join(", ", periods)],
            periods.Count == 0 ? ProfileTarget : ""));

        // This one reads the file and nothing else, so it stays a two-way verdict: an empty profile
        // still produces a document, and a document that extracts to nothing is a real fault.
        var hasText = compact.Length > 200;
        findings.Add(new AtsFinding("text", "Schriften eingebettet, Text extrahierbar",
            hasText ? "ok" : "fehler",
            hasText
                ? $"{compact.Length} Zeichen extrahiert"
                : "Fast kein Text extrahierbar — die Seite liegt vermutlich als Bild vor",
            hasText ? "text_ok" : "text_fehlt",
            hasText ? [$"{compact.Length}"] : []));

        return new AtsResult(pageCount, pdf.Length, findings);
    }
}
