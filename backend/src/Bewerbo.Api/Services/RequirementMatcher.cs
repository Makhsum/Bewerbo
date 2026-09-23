using System.Text.RegularExpressions;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;

namespace Bewerbo.Api.Services;

/// <summary>belegt | offen | nicht_belegt — and nothing else may reach the letter writer.</summary>
public enum RequirementState { Belegt, Offen, NichtBelegt }

public record MatchedRequirement(
    string Text,
    RequirementState State,
    /// <summary>For belegt: the entry that proves it, named so the reader can check it.</summary>
    string Evidence,
    /// <summary>
    /// For offen: the one action that would close it. Empty where this requirement has nothing of
    /// its own to say — what holds for every nicht_belegt requirement alike ("wird im Anschreiben
    /// nicht behauptet") is said once by the screen that groups them, not repeated on every row.
    /// </summary>
    string Action,
    /// <summary>
    /// For offen: the language entry whose Nachweis closes this requirement, so the Abgleich can
    /// file that document instead of sending the user off to look for the right one. Empty
    /// everywhere else — a requirement with no action has nothing for the action to act on.
    /// </summary>
    string Language = "");

public record MatchResult(
    IReadOnlyList<MatchedRequirement> Requirements,
    int Covered,
    int Total)
{
    public int Percent => Total == 0 ? 0 : (int)Math.Round(100.0 * Covered / Total);

    /// <summary>
    /// The only requirements the Anschreiben is allowed to draw on. What is "nicht belegt" stays
    /// out of the letter entirely — claiming it is the failure mode the Abgleich exists to prevent.
    /// </summary>
    public IEnumerable<MatchedRequirement> Claimable =>
        Requirements.Where(r => r.State == RequirementState.Belegt);
}

/// <summary>
/// Matches the posting's own words against what the profile can actually prove.
///
/// Three states, not two: "offen" is the case where the user has the thing but has not filed the
/// evidence — a B2 certificate claimed in the profile with nothing in the Mappe. That distinction
/// is what lets the app ask for an upload instead of silently dropping the requirement.
/// </summary>
public static class RequirementMatcher
{
    public static MatchResult Match(Profile profile, IEnumerable<ExtractedRequirement> requirements)
    {
        var results = new List<MatchedRequirement>();

        foreach (var requirement in requirements)
        {
            results.Add(Classify(profile, requirement.Text));
        }

        var covered = results.Count(r => r.State == RequirementState.Belegt);
        return new MatchResult(results, covered, results.Count);
    }

    private static MatchedRequirement Classify(Profile profile, string requirement)
    {
        // A language requirement is its own case: the profile states the level, the Mappe holds the
        // certificate, and the posting cares about both.
        var language = LanguageRequirement(profile, requirement);
        if (language is not null) return language;

        // A formal German qualification ("Geprüfte Bilanzbuchhalterin (IHK)") is either held or it
        // is not. There is no honest middle, so an unmatched one is stated plainly as nicht belegt.
        var keywords = Keywords(requirement);
        if (keywords.Count == 0)
        {
            return new MatchedRequirement(requirement, RequirementState.NichtBelegt, "", "");
        }

        foreach (var entry in profile.Experience.OrderByDescending(e => e.To is null).ThenByDescending(e => e.From))
        {
            var haystack = $"{entry.Position} {entry.Employer} {entry.Industry} {entry.Duties}";
            if (!Hits(haystack, keywords)) continue;

            var period = entry.To is null
                ? $"seit {entry.From:MM\\/yyyy}"
                : $"{entry.From:MM\\/yyyy} – {entry.To:MM\\/yyyy}";
            return new MatchedRequirement(requirement, RequirementState.Belegt,
                $"{entry.Position}, {entry.Employer} · {period}", "");
        }

        foreach (var entry in profile.Education)
        {
            var haystack = $"{entry.Degree} {entry.Institution} {entry.GermanEquivalent}";
            if (!Hits(haystack, keywords)) continue;
            return new MatchedRequirement(requirement, RequirementState.Belegt,
                $"{entry.Degree}, {entry.Institution}", "");
        }

        return new MatchedRequirement(requirement, RequirementState.NichtBelegt, "", "");
    }

    private static MatchedRequirement? LanguageRequirement(Profile profile, string requirement)
    {
        // The words between the language and the level are whatever the posting chose to write:
        // "Deutschkenntnisse auf Niveau B2" alone puts 22 characters there, so a 20-character
        // window silently read the most ordinary German phrasing as "no language requirement" and
        // told the applicant their B2 was not in the profile at all.
        var m = Regex.Match(requirement,
            @"(?<lang>Deutsch|Englisch|Französisch|Russisch)\D{0,40}?(?<level>[ABC][12])",
            RegexOptions.IgnoreCase);
        if (!m.Success) return null;

        var wanted = m.Groups["lang"].Value;
        var level = m.Groups["level"].Value.ToUpperInvariant();
        var skill = profile.Languages.FirstOrDefault(l =>
            l.Language.Contains(wanted, StringComparison.OrdinalIgnoreCase));

        if (skill is null)
        {
            return new MatchedRequirement(requirement, RequirementState.NichtBelegt, "", "");
        }

        if (!AtLeast(skill.Level, level))
        {
            return new MatchedRequirement(requirement, RequirementState.NichtBelegt,
                "", $"Im Profil steht {skill.Level}");
        }

        return skill.CertificateOnFile
            ? new MatchedRequirement(requirement, RequirementState.Belegt,
                $"{skill.Language} {skill.Level} · Nachweis in der Mappe", "")
            // Stated but not evidenced: the app can close this, so it asks rather than dropping it
            // — and names the language, because the asking is only worth anything if the Abgleich
            // can then file the right Nachweis without the user hunting for it.
            : new MatchedRequirement(requirement, RequirementState.Offen,
                "Im Profil angegeben, Zertifikat fehlt in den Anlagen", "Nachweis hochladen",
                skill.Language);
    }

    private static readonly string[] Levels = ["A1", "A2", "B1", "B2", "C1", "C2"];

    private static bool AtLeast(string held, string wanted)
    {
        if (held.Contains("Mutter", StringComparison.OrdinalIgnoreCase)) return true;
        var h = Array.FindIndex(Levels, l => held.Contains(l, StringComparison.OrdinalIgnoreCase));
        var w = Array.IndexOf(Levels, wanted);
        return h >= 0 && w >= 0 && h >= w;
    }

    /// <summary>
    /// The words that carry the requirement. Stopwords and the German filler a posting is built out
    /// of ("sicher", "gute", "Kenntnisse") would match anything, so they are dropped: a requirement
    /// that only has filler left is not matched at all rather than matched loosely.
    /// </summary>
    private static List<string> Keywords(string requirement)
    {
        var stop = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "sichere", "sicheres", "sicher", "gute", "guter", "gutes", "sehr", "und", "oder",
            "der", "die", "das", "den", "dem", "ein", "eine", "einen", "einer", "mit", "von",
            "für", "nach", "an", "im", "in", "auf", "zu", "als", "sind", "ist", "sie", "wir",
            "kenntnisse", "erfahrung", "erfahrungen", "auftreten", "umgang", "bereich",
            "mindestens", "idealerweise", "wünschenswert", "abgeschlossene", "abgeschlossenes",
        };

        return Regex.Matches(requirement, @"[\wÄÖÜäöüß-]{3,}")
            .Select(m => m.Value)
            .Where(w => !stop.Contains(w))
            .ToList();
    }

    /// <summary>
    /// One carrying word is enough, because the requirements are already short phrases and the
    /// carrying word is usually the whole point of them ("DATEV", "HGB", "Jahresabschlüsse").
    /// Compound splitting on the hyphen catches "DATEV-Kenntnisse" against a duty naming "DATEV".
    /// </summary>
    private static bool Hits(string haystack, List<string> keywords) =>
        keywords.Any(k => haystack.Contains(k, StringComparison.OrdinalIgnoreCase)
                          || k.Split('-').Any(part => part.Length >= 4
                                                      && haystack.Contains(part, StringComparison.OrdinalIgnoreCase)));
}
