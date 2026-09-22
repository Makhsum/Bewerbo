using Bewerbo.Api.Domain;
using Bewerbo.Api.Endpoints;

namespace Bewerbo.Api.Services;

/// <summary>
/// The Bewerbungsmappe readiness score and the "Nächste Schritte" it breaks out into.
///
/// The score is only worth having if every point of it names the thing that would raise it. So it
/// is computed from three counted things — profile completeness, gaps explained, evidence on file —
/// and each shortfall produces the step that closes it, pointing at the screen that does the work.
/// </summary>
public static class ReadinessService
{
    public static OverviewDto Build(Profile profile, TimelineView timeline, IReadOnlyList<Posting> postings)
    {
        var completeness = Completeness(profile);

        var gapsTotal = timeline.Gaps.Count;
        var gapsExplained = timeline.Gaps.Count(g => g.Explained);

        var (evidenceOnFile, evidenceExpected) = Evidence(profile);

        // Weighted: an incomplete profile is the thing that blocks everything else, an unexplained
        // gap is what a recruiter reacts to, missing evidence only bites at the Abgleich.
        var gapScore = gapsTotal == 0 ? 100 : 100 * gapsExplained / gapsTotal;
        var evidenceScore = evidenceExpected == 0 ? 100 : 100 * evidenceOnFile / evidenceExpected;

        // An empty profile has no gaps and needs no evidence, so both of those score 100 — which
        // would hand a user who has entered nothing a readiness of 54. Until there is a career to
        // measure, the only honest number is how much of the profile is filled in.
        var hasCareer = profile.Experience.Count > 0 || profile.Education.Count > 0;
        var readiness = hasCareer
            ? (int)Math.Round(completeness * 0.5 + gapScore * 0.3 + evidenceScore * 0.2)
            : completeness;

        var steps = NextSteps(profile, timeline);

        var applications = profile.Applications
            .OrderByDescending(a => a.CreatedAt)
            .Select(a =>
            {
                var posting = postings.FirstOrDefault(p => p.Id == a.PostingId);
                return new ActiveApplicationDto(
                    a.Id,
                    posting?.JobTitle ?? "",
                    posting?.Company ?? "",
                    posting?.Reference ?? "",
                    a.Status.ToString(),
                    a.SentAt?.ToString("dd.MM.yyyy"));
            })
            .ToList();

        return new OverviewDto(
            $"{profile.FirstName} {profile.LastName}".Trim(),
            profile.City,
            applications.Count,
            readiness,
            completeness,
            gapsExplained, gapsTotal,
            evidenceOnFile, evidenceExpected,
            steps,
            applications,
            profile.Documents.Select(DtoMapping.ToDto).ToList());
    }

    /// <summary>
    /// How much of the profile a German reader would find filled in. Counted, not estimated, so
    /// that the number moves the moment the user types something.
    /// </summary>
    public static int Completeness(Profile profile)
    {
        var checks = new[]
        {
            !string.IsNullOrWhiteSpace(profile.FirstName),
            !string.IsNullOrWhiteSpace(profile.LastName),
            !string.IsNullOrWhiteSpace(profile.Street),
            !string.IsNullOrWhiteSpace(profile.City),
            !string.IsNullOrWhiteSpace(profile.Phone),
            !string.IsNullOrWhiteSpace(profile.Email),
            profile.Experience.Count > 0,
            profile.Experience.All(e => e.DutyLines.Any()),
            profile.Education.Count > 0,
            profile.Languages.Count > 0,
            profile.Languages.Any(l => l.Language.Contains("Deutsch", StringComparison.OrdinalIgnoreCase)
                                       || l.Language.Contains("German", StringComparison.OrdinalIgnoreCase)
                                       || l.Language.Contains("нем", StringComparison.OrdinalIgnoreCase)
                                       || l.Language.Contains("нім", StringComparison.OrdinalIgnoreCase)),
            profile.Documents.Count > 0,
        };
        return 100 * checks.Count(c => c) / checks.Length;
    }

    /// <summary>
    /// Evidence expected is not "every document that exists" — it is the documents this profile
    /// implies: one Zeugnis per past job, one certificate per stated language level, an anabin
    /// extract for a degree whose equivalence is claimed.
    /// </summary>
    private static (int OnFile, int Expected) Evidence(Profile profile)
    {
        var expected = 0;
        var onFile = 0;

        foreach (var entry in profile.Experience.Where(e => e.To is not null))
        {
            expected++;
            if (entry.ReferenceOnFile) onFile++;
        }

        foreach (var language in profile.Languages.Where(l =>
                     !l.Level.Contains("Mutter", StringComparison.OrdinalIgnoreCase)))
        {
            expected++;
            if (language.CertificateOnFile) onFile++;
        }

        foreach (var degree in profile.Education.Where(e => e.EquivalenceConfirmed))
        {
            expected++;
            if (profile.Documents.Any(d => d.Kind == DocumentKind.AnabinAuszug)) onFile++;
        }

        return (onFile, expected);
    }

    private static List<NextStepDto> NextSteps(Profile profile, TimelineView timeline)
    {
        var steps = new List<NextStepDto>();

        foreach (var gap in timeline.Gaps.Where(g => !g.Explained))
        {
            steps.Add(new NextStepDto(
                $"gap_{gap.From:yyyyMM}",
                $"Lücke {gap.From:MM\\/yyyy} – {gap.To:MM\\/yyyy} benennen",
                "Ohne Grund liest ein deutscher Leser die Lücke als Warnsignal",
                "attention",
                "profil"));
        }

        foreach (var degree in profile.Education.Where(e =>
                     !e.EquivalenceConfirmed && !string.IsNullOrWhiteSpace(e.Country)
                     && !e.Country.Equals("DE", StringComparison.OrdinalIgnoreCase)))
        {
            steps.Add(new NextStepDto(
                $"anerkennung_{degree.Id}",
                "Anerkennung bestätigen",
                $"anabin-Eintrag für {degree.Institution} prüfen und zitieren",
                "info",
                "profil"));
        }

        foreach (var language in profile.Languages.Where(l =>
                     !l.CertificateOnFile && !l.Level.Contains("Mutter", StringComparison.OrdinalIgnoreCase)))
        {
            steps.Add(new NextStepDto(
                $"nachweis_{language.Id}",
                $"Sprachzertifikat {language.Level} fehlt",
                $"{language.Language} {language.Level} ist im Profil angegeben, aber nicht belegt",
                "attention",
                "mappe"));
        }

        if (profile.Experience.Count == 0)
        {
            steps.Add(new NextStepDto("beruf", "Berufserfahrung eintragen",
                "Ohne einen Eintrag lässt sich kein Lebenslauf erzeugen", "attention", "profil"));
        }

        return steps;
    }
}
