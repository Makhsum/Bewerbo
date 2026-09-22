using System.Text.RegularExpressions;
using Bewerbo.Api.Domain;
using UglyToad.PdfPig;

namespace Bewerbo.Api.Rendering;

public record AtsFinding(string Key, string Label, bool Found, string Detail);

public record AtsResult(bool Passed, int PageCount, int SizeBytes, IReadOnlyList<AtsFinding> Findings);

/// <summary>
/// Reads the finished PDF back and checks that the things a Bewerbermanagementsystem indexes on are
/// still there as text.
///
/// This is the only check in the product that looks at the actual output rather than at the data it
/// was rendered from. A PDF that looks right and extracts to nothing is the single most expensive
/// failure here: the application is filtered out before a human ever opens it, and nobody is told.
/// </summary>
public static class AtsTextCheck
{
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
            return new AtsResult(false, 0, pdf.Length,
            [
                new AtsFinding("lesbar", "PDF zurückgelesen", false,
                    $"Die Datei ließ sich nicht als Text öffnen: {ex.Message}"),
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

        var nameFound = Contains($"{profile.FirstName}{profile.LastName}");
        findings.Add(new AtsFinding("name", "Name als Text wiedergefunden", nameFound,
            nameFound ? $"{profile.FirstName} {profile.LastName}" : "Der Name ist im Text nicht auffindbar"));

        var employers = profile.Experience.Select(e => e.Employer).Where(e => !string.IsNullOrWhiteSpace(e)).ToList();
        var employersFound = employers.Count > 0 && employers.All(Contains);
        findings.Add(new AtsFinding("arbeitgeber", "Arbeitgeber als Text wiedergefunden", employersFound,
            employers.Count == 0
                ? "Keine Arbeitgeber im Profil"
                : employersFound
                    ? string.Join(", ", employers)
                    : "Fehlt: " + string.Join(", ", employers.Where(e => !Contains(e)))));

        // Dates are what an ATS builds the career timeline from; a CV whose periods do not extract
        // reads as a career with no dates at all.
        var periods = profile.Experience.Select(e => $"{e.From:MM/yyyy}").ToList();
        var datesFound = periods.Count > 0 && periods.All(Contains);
        findings.Add(new AtsFinding("zeitraeume", "Zeiträume als Text wiedergefunden", datesFound,
            periods.Count == 0
                ? "Keine Zeiträume im Profil"
                : datesFound ? string.Join(", ", periods) : "Mindestens ein Zeitraum extrahiert nicht"));

        var hasText = compact.Length > 200;
        findings.Add(new AtsFinding("text", "Schriften eingebettet, Text extrahierbar", hasText,
            hasText
                ? $"{compact.Length} Zeichen extrahiert"
                : "Fast kein Text extrahierbar — die Seite liegt vermutlich als Bild vor"));

        return new AtsResult(findings.All(f => f.Found), pageCount, pdf.Length, findings);
    }
}
