using System.Text.Json;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Contracts;

namespace Bewerbo.Api.Services;

/// <summary>
/// What the Bewerbungsmappe still needs — the "Nächste Schritte" — and the three counted things
/// they break out of: profile completeness, gaps explained, evidence on file.
///
/// There used to be a weighted score out of 100 over the top of these. It was an invented measure:
/// on an empty profile it said "0 of 100" and named nothing a user could do about it, and the three
/// counts it was averaged from are the only part of it that was ever attributable. So the score is
/// gone and what is outstanding is the whole of the answer — each shortfall produces the step that
/// closes it, pointing at the screen that does the work.
/// </summary>
public static class ReadinessService
{
    public static OverviewDto Build(Profile profile, TimelineView timeline, IReadOnlyList<Posting> postings)
    {
        var completeness = Completeness(profile);

        var gapsTotal = timeline.Gaps.Count;
        var gapsExplained = timeline.Gaps.Count(g => g.Explained);

        var (evidenceOnFile, evidenceExpected) = Evidence(profile);

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
                    a.SentAt?.ToString("dd.MM.yyyy"),
                    OpenSteps(a));
            })
            .ToList();

        return new OverviewDto(
            $"{profile.FirstName} {profile.LastName}".Trim(),
            profile.City,
            applications.Count,
            // Which of the two the Übersicht offers as the first step. Without one Berufserfahrung
            // there is no Lebenslauf to produce, so beginning an application there leads to a path
            // that cannot finish — the same thing the "beruf" step below says in words.
            profile.Experience.Count > 0,
            // What the Anschreiben itself is still waiting for, as keys. A superset of the line
            // above: the flow may be worth beginning before the Briefkopf is filled in, the letter
            // may not be written then. See LetterBlockers.
            LetterBlockers(profile).Select(b => b.Key).ToList(),
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
            // All() over an empty list is true, so an empty profile used to score this point and
            // open at "8 % complete" — a number the user cannot account for, since they have
            // entered nothing. The point is for entries that HAVE duty lines.
            profile.Experience.Count > 0 && profile.Experience.All(e => e.DutyLines.Any()),
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

    /// <summary>
    /// The person's own details that are still empty. They went uncounted while they were half of
    /// <see cref="Completeness"/>, so the Übersicht could say nothing was outstanding over a profile
    /// with no name and no Anschrift — and the Briefkopf of every Anschreiben is made of exactly
    /// these.
    ///
    /// The item names go out as the KEYS the client looks up, not as the words: "Anschrift" read as
    /// "Anschrift" on an English screen, which is the whole of what a DTO's Kind and Args exist to
    /// stop. The German Label stays beside each key as the fallback sentence's wording.
    /// </summary>
    private static List<(string Key, string Label)> PersonMissing(Profile profile) => new[]
    {
        (Missing: string.IsNullOrWhiteSpace(profile.FirstName)
                  || string.IsNullOrWhiteSpace(profile.LastName), Key: "name", Label: "Name"),
        (Missing: string.IsNullOrWhiteSpace(profile.Street)
                  || string.IsNullOrWhiteSpace(profile.City), Key: "anschrift", Label: "Anschrift"),
        (Missing: string.IsNullOrWhiteSpace(profile.Phone)
                  && string.IsNullOrWhiteSpace(profile.Email), Key: "kontakt", Label: "Kontakt"),
    }.Where(p => p.Missing).Select(p => (p.Key, p.Label)).ToList();

    /// <summary>
    /// What stands between this profile and an Anschreiben: the Briefkopf's own fields and one
    /// Berufserfahrung. Empty means the letter may be written.
    ///
    /// This is a stricter thing than <see cref="OverviewDto.CanStartApplication"/>, which asks only
    /// whether beginning an application is worth it. A letter written before these are filled in
    /// carries no sender address and no name under the closing, restates the advert for want of
    /// anything to say about the applicant, and lists a Lebenslauf as an Anlage that the empty
    /// profile cannot produce — a document that costs the applicant the position.
    ///
    /// It is the one rule for this, and both the screen that offers the letter and the route that
    /// writes it read it here, so the button and the refusal can never mean different things.
    /// </summary>
    public static List<(string Key, string Label)> LetterBlockers(Profile profile)
    {
        var blockers = PersonMissing(profile);
        if (profile.Experience.Count == 0) blockers.Add(("beruf", "Berufserfahrung"));
        return blockers;
    }

    private static List<NextStepDto> NextSteps(Profile profile, TimelineView timeline)
    {
        var steps = new List<NextStepDto>();

        var personMissing = PersonMissing(profile);

        if (personMissing.Count > 0)
        {
            steps.Add(new NextStepDto(
                "person",
                "Angaben zur Person vervollständigen",
                $"Der Briefkopf nach DIN 5008 braucht noch: {string.Join(", ", personMissing.Select(p => p.Label))}",
                "attention",
                "profil",
                "person",
                personMissing.Select(p => p.Key).ToList()));
        }

        foreach (var gap in timeline.Gaps.Where(g => !g.Explained))
        {
            steps.Add(new NextStepDto(
                $"gap_{gap.From:yyyyMM}",
                $"Lücke {gap.From:MM\\/yyyy} – {gap.To:MM\\/yyyy} benennen",
                "Ohne Grund liest ein deutscher Leser die Lücke als Warnsignal",
                "attention",
                "profil",
                "gap",
                [$"{gap.From:MM\\/yyyy}", $"{gap.To:MM\\/yyyy}"]));
        }

        foreach (var degree in profile.Education.Where(e =>
                     !e.EquivalenceConfirmed && !string.IsNullOrWhiteSpace(e.Country)
                     && !e.Country.Equals("DE", StringComparison.OrdinalIgnoreCase)))
        {
            // Both are outstanding, and they are not the same step. A user who has applied for the
            // Zeugnisbewertung and is waiting on it cannot go and look the answer up, and being
            // told to reads as though the app had not noticed they already acted.
            var waiting = degree.ZabAssessmentPending;
            steps.Add(new NextStepDto(
                $"anerkennung_{degree.Id}",
                waiting ? "Zeugnisbewertung abwarten" : "Anerkennung bestätigen",
                waiting
                    ? $"Die ZAB hat die Bewertung für {degree.Institution} noch nicht zurückgeschickt"
                    : $"anabin-Eintrag für {degree.Institution} prüfen und zitieren",
                "info",
                "profil",
                waiting ? "anerkennung_offen" : "anerkennung",
                [degree.Institution]));
        }

        foreach (var language in profile.Languages.Where(l =>
                     !l.CertificateOnFile && !l.Level.Contains("Mutter", StringComparison.OrdinalIgnoreCase)))
        {
            steps.Add(new NextStepDto(
                $"nachweis_{language.Id}",
                $"Sprachzertifikat {language.Level} fehlt",
                $"{language.Language} {language.Level} ist im Profil angegeben, aber nicht belegt",
                "attention",
                "mappe",
                "sprachnachweis",
                [language.Language, language.Level]));
        }

        if (profile.Experience.Count == 0)
        {
            steps.Add(new NextStepDto("beruf", "Berufserfahrung eintragen",
                "Ohne einen Eintrag lässt sich kein Lebenslauf erzeugen", "attention", "profil",
                "beruf", []));
        }

        return steps;
    }

    /// <summary>
    /// What ONE application still needs, so the list says where each employer stands rather than
    /// only that it exists. Same shape as <see cref="NextSteps"/>, and for the same reason: a step
    /// that does not name the screen it is done on is a complaint, not a step.
    ///
    /// Capped at three. The list is read at a glance, and a row carrying eight lines is not.
    /// </summary>
    private static List<NextStepDto> OpenSteps(Application application)
    {
        var steps = new List<NextStepDto>();

        // The draft comes first: it is what makes the application unfinished, and it is the one
        // step that is still outstanding even when everything else is proven.
        if (application.Status == ApplicationStatus.Entwurf)
        {
            steps.Add(new NextStepDto($"versand_{application.Id}", "Noch nicht versendet",
                "Mappe exportieren und den Status auf Versendet setzen", "info", "bewerbung",
                "versand", []));
        }

        // MatchJson defaults to "{}", which would throw here as a list and take the whole
        // Übersicht down with it. One row without its steps is the smaller loss.
        var requirements = application.MatchJson.TrimStart().StartsWith('[')
            ? JsonSerializer.Deserialize<List<RequirementDto>>(application.MatchJson) ?? []
            : [];

        foreach (var requirement in requirements.Where(r => r.State == "offen"))
        {
            steps.Add(new NextStepDto(
                $"beleg_{application.Id}_{requirements.IndexOf(requirement)}",
                $"Nachweis fehlt: {requirement.Text}",
                // The Abgleich already wrote down what is missing and where it is recorded. The
                // Mappe is where a Nachweis is added, which is where the Abgleich sends it too.
                requirement.Evidence,
                "attention",
                "mappe",
                // The requirement and its evidence are quoted from the posting, so they stay as
                // they are in every language — only the sentence around them is translated.
                "beleg",
                [requirement.Text]));
        }

        return steps.Take(3).ToList();
    }
}
