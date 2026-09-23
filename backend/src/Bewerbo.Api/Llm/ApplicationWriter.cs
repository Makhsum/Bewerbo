using System.Globalization;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using Bewerbo.Api.Text;

namespace Bewerbo.Api.Llm;

public interface IApplicationWriter
{
    Task<CvContent> WriteCvAsync(Profile profile, TimelineView timeline, CancellationToken ct = default);

    Task<LetterContent> WriteLetterAsync(Profile profile, Posting posting, MatchResult match,
        LetterTone tone, CancellationToken ct = default);

    /// <summary>"model" or "regeln" — shown in the report so nobody has to guess which ran.</summary>
    string LastSource { get; }
}

/// <summary>
/// Writes the German. Asks the model when one is configured and falls back to the rule-based writer
/// otherwise — and validates the model's answer against the same rules either way, because a letter
/// that comes back with "Hiermit bewerbe ich mich" in it is not usable no matter who wrote it.
/// </summary>
public class ApplicationWriter(ILanguageModel model, ILogger<ApplicationWriter> log) : IApplicationWriter
{
    public string LastSource { get; private set; } = "regeln";

    public async Task<CvContent> WriteCvAsync(Profile profile, TimelineView timeline, CancellationToken ct = default)
    {
        if (model.IsConfigured)
        {
            var system =
                "Du schreibst Inhalte für einen deutschen Lebenslauf. Gib ausschließlich Inhalt zurück, " +
                "niemals Layout. Formuliere Tätigkeiten als Ergebnisse mit konkreten Zahlen. " +
                "Benenne Lücken offen (Umzug, Sprachkurs, Anerkennungsverfahren). Kein Europass-Stil.";
            var result = await model.CompleteAsync<CvContent>(
                system, CvPrompt(profile, timeline), OutputSchemas.CvSchemaName, OutputSchemas.Cv, ct);
            if (result is { Sections.Count: > 0 })
            {
                LastSource = "model";
                return result;
            }
            log.LogInformation("Model returned no usable Lebenslauf; using the rule-based writer.");
        }

        LastSource = "regeln";
        return DeterministicCv(profile, timeline);
    }

    public async Task<LetterContent> WriteLetterAsync(Profile profile, Posting posting, MatchResult match,
        LetterTone tone, CancellationToken ct = default)
    {
        if (model.IsConfigured)
        {
            var system =
                "Du schreibst ein deutsches ANSCHREIBEN für eine Stellenbewerbung — niemals ein " +
                "Motivationsschreiben. Die Betreffzeile enthält die Referenznummer und nicht das Wort " +
                "»Betreff«. Antworte auf die Anforderungen der Anzeige mit konkreten Belegen. " +
                "Behaupte NICHTS, was nicht als belegt übergeben wurde. Verboten sind: " +
                string.Join("; ", FloskelRules.Banned.Take(8)) + ".";
            var result = await model.CompleteAsync<LetterContent>(
                system, LetterPrompt(profile, posting, match, tone),
                OutputSchemas.LetterSchemaName, OutputSchemas.Letter, ct);

            // A model answer still has to clear the rule-based check. If it does not, the
            // deterministic letter is used — a letter with a Floskel in it is not a letter.
            if (result is { Paragraphs.Count: > 0 } && FloskelRules.Find(string.Join(" ", result.Paragraphs)).Count == 0)
            {
                LastSource = "model";
                return result;
            }
            log.LogInformation("Model letter rejected by the Floskel check; using the rule-based writer.");
        }

        LastSource = "regeln";
        return DeterministicLetter(profile, posting, match, tone);
    }

    // -- prompts --------------------------------------------------------------------------------

    private static string CvPrompt(Profile profile, TimelineView timeline)
    {
        var lines = new List<string>
        {
            $"Eingabesprache des Nutzers: {profile.InputLanguage}. Übersetze alles ins Deutsche.",
            $"Person: {profile.FirstName} {profile.LastName}, {profile.PostalCode} {profile.City}",
            "",
            "Berufserfahrung:",
        };
        foreach (var e in profile.Experience.OrderByDescending(e => e.From))
        {
            lines.Add($"- {Period(e.From, e.To)} | {e.Position} | {e.Employer} | {e.Location} | " +
                      $"{e.Workload} | {e.Industry} | Aufgaben: {string.Join(" / ", e.DutyLines)}");
        }
        lines.Add("");
        lines.Add("Ausbildung:");
        foreach (var e in profile.Education.OrderByDescending(e => e.From))
        {
            var equiv = e.EquivalenceConfirmed && !string.IsNullOrWhiteSpace(e.GermanEquivalent)
                ? $" | anabin: {e.AnabinAssessment}, gleichwertig mit: {e.GermanEquivalent}"
                : " | keine bestätigte Gleichwertigkeit — NICHT behaupten";
            lines.Add($"- {Period(e.From, e.To)} | {e.Degree} | {e.Institution}, {e.Location}{equiv}");
        }
        lines.Add("");
        lines.Add("Sprachen: " + string.Join(", ", profile.Languages.Select(l => $"{l.Language} {l.Level}")));
        lines.Add("");
        lines.Add("Lücken, die benannt werden müssen:");
        foreach (var g in timeline.Gaps)
        {
            lines.Add($"- {g.From:MM/yyyy} – {g.To:MM/yyyy}: {g.GermanWording ?? g.Reason ?? "(ohne Angabe)"}");
        }
        return string.Join("\n", lines);
    }

    private static string LetterPrompt(Profile profile, Posting posting, MatchResult match, LetterTone tone)
    {
        var lines = new List<string>
        {
            $"Ton: {tone}.",
            $"Adressat: {posting.ContactName}, {posting.ContactRole}, {posting.Company}.",
            $"Stelle: {posting.JobTitle}. Referenznummer: {posting.Reference}. Eintritt: {posting.StartDate}.",
            $"Arbeitgebertyp: {posting.EmployerType}.",
            "",
            "BELEGTE Anforderungen — nur diese dürfen im Brief vorkommen:",
        };
        foreach (var r in match.Claimable) lines.Add($"- {r.Text} — belegt durch: {r.Evidence}");

        lines.Add("");
        lines.Add("NICHT BELEGT — darf im Brief NICHT behauptet werden:");
        foreach (var r in match.Requirements.Where(r => r.State != RequirementState.Belegt))
        {
            lines.Add($"- {r.Text}");
        }
        lines.Add("");
        lines.Add("Quelltext der Anzeige:");
        lines.Add(posting.SourceText);
        return string.Join("\n", lines);
    }

    // -- the rule-based writer ------------------------------------------------------------------

    private static CvContent DeterministicCv(Profile profile, TimelineView timeline)
    {
        var sections = new List<CvSection>();

        var work = new List<CvItem>();
        foreach (var e in profile.Experience.OrderByDescending(e => e.From))
        {
            var subtitle = string.Join(", ",
                new[] { e.Employer, e.Location, e.Workload }.Where(s => !string.IsNullOrWhiteSpace(s)));
            work.Add(new CvItem
            {
                Period = Period(e.From, e.To),
                Title = e.Position,
                Subtitle = subtitle,
                Bullets = e.DutyLines.ToList(),
            });

            // The gap goes in where it belongs chronologically, named, rather than being left as a
            // hole the reader discovers. The list runs newest first, so the gap follows the entry
            // it runs into.
            work.AddRange(NamedGaps(timeline.Gaps.Where(g => g.To == e.From)));
        }
        if (work.Count > 0) sections.Add(new CvSection { Title = "Berufserfahrung", Items = work });

        var education = new List<CvItem>();
        foreach (var e in profile.Education.OrderByDescending(e => e.From))
        {
            var bullets = new List<string>();
            // The equivalence is stated only where the user confirmed it. Without confirmation the
            // degree stands under its own name — a wrong equivalence is worse than none.
            if (e.EquivalenceConfirmed && !string.IsNullOrWhiteSpace(e.GermanEquivalent))
            {
                bullets.Add($"In Deutschland gleichwertig mit: {e.GermanEquivalent}" +
                            (string.IsNullOrWhiteSpace(e.AnabinAssessment)
                                ? ""
                                : $" (anabin: {e.AnabinAssessment})"));
            }
            education.Add(new CvItem
            {
                Period = Period(e.From, e.To),
                Title = e.Degree,
                Subtitle = string.Join(", ",
                    new[] { e.Institution, e.Location }.Where(s => !string.IsNullOrWhiteSpace(s))),
                Bullets = bullets,
            });

            // A study period is an entry a gap runs into just as much as a job is — the gaps are
            // found across BOTH lanes. Without this the year before a course began was named on
            // the Profil screen, counted as explained, and then missing from the document.
            education.AddRange(NamedGaps(timeline.Gaps.Where(g => g.To == e.From)));
        }
        if (education.Count > 0) sections.Add(new CvSection { Title = "Ausbildung", Items = education });

        if (profile.Languages.Count > 0)
        {
            sections.Add(new CvSection
            {
                Title = "Sprachen",
                Items = profile.Languages.Select(l => new CvItem
                {
                    Period = "",
                    Title = l.Language,
                    Subtitle = l.Level + (l.CertificateOnFile ? " (Nachweis liegt bei)" : ""),
                }).ToList(),
            });
        }

        var headline = profile.Experience
            .OrderByDescending(e => e.To is null).ThenByDescending(e => e.From)
            .FirstOrDefault()?.Position ?? "";

        return new CvContent { Headline = headline, Sections = sections };
    }

    private static LetterContent DeterministicLetter(Profile profile, Posting posting, MatchResult match,
        LetterTone tone)
    {
        var claimable = match.Claimable.ToList();
        // The most recent entry — which is NOT necessarily an ongoing one. Ordering ongoing first
        // and then taking the head returns the last job that ended when there is no current one,
        // so the tense below is decided by To, never by this position.
        var latest = profile.Experience
            .OrderByDescending(e => e.To is null).ThenByDescending(e => e.From).FirstOrDefault();
        var longest = profile.Experience
            .OrderByDescending(e => TimelineService.MonthsBetween(e.From, e.To ?? DateOnly.FromDateTime(DateTime.Today)))
            .FirstOrDefault();

        var paragraphs = new List<string>();

        // 1 — answer the posting's own words, with the evidence, not with virtues.
        // The requirement text is used exactly as the posting wrote it. Folding it into a German
        // sentence would mean lowercasing its first word, and in German that first word is usually
        // a noun — so it is introduced with a colon instead, where the capital is correct.
        var answered = claimable.Take(2).Select(r => r.Text).ToList();
        var opening = tone switch
        {
            LetterTone.Klassisch =>
                $"in Ihrer Anzeige suchen Sie eine Verstärkung für {Article(posting.JobTitle)}.",
            LetterTone.Modern =>
                $"Sie suchen jemanden für {Article(posting.JobTitle)} — das ist genau die Arbeit, die ich seit Jahren mache.",
            _ =>
                $"in Ihrer Anzeige suchen Sie jemanden für {Article(posting.JobTitle)}.",
        };
        if (answered.Count > 0)
        {
            opening += $" Sie nennen dabei: {string.Join("; ", answered)}.";
        }
        if (longest is not null)
        {
            var years = Math.Max(1, TimelineService.MonthsBetween(
                longest.From, longest.To ?? DateOnly.FromDateTime(DateTime.Today)) / 12);
            // Only a duty line that is already Latin may be quoted. Without a model there is no
            // translation, and a German sentence ending in a Cyrillic clause is a defect the
            // applicant cannot see — the sentence simply stops earlier instead.
            var duty = longest.DutyLines.FirstOrDefault(ScriptCheck.IsLatin);
            opening += $" Genau das war {years} Jahre lang meine tägliche Arbeit: als {longest.Position} " +
                       $"bei {longest.Employer} in {longest.Location}" +
                       (string.IsNullOrWhiteSpace(duty) ? "." : $" — {duty}.");
        }
        paragraphs.Add(opening);

        // 2 — what the applicant does now, with the further evidence named.
        if (latest is not null)
        {
            // A job that has ended is written in the past. Saying "Seit März 2019 arbeite ich bei
            // X" about a post left in 2023 tells the employer the applicant is still employed
            // there — an untrue claim about the one fact they are most likely to check.
            var workload = string.IsNullOrWhiteSpace(latest.Workload) ? "" : $"in {latest.Workload} ";
            var second = latest.To is null
                ? $"Seit {MonthName(latest.From)} {latest.From.Year} arbeite ich {workload}" +
                  $"bei {latest.Employer} in {latest.Location}."
                : $"Von {MonthName(latest.From)} {latest.From.Year} bis {MonthName(latest.To.Value)} " +
                  $"{latest.To.Value.Year} habe ich {workload}bei {latest.Employer} " +
                  $"in {latest.Location} gearbeitet.";

            var duties = latest.DutyLines.Where(ScriptCheck.IsLatin).Take(2).ToList();
            if (duties.Count > 0)
            {
                second += $" Meine Aufgaben dort: {string.Join("; ", duties)}.";
            }

            var degree = profile.Education.FirstOrDefault(e =>
                e.EquivalenceConfirmed && !string.IsNullOrWhiteSpace(e.GermanEquivalent));
            if (degree is not null)
            {
                second += $" Mein Abschluss von {degree.Institution} ist laut anabin einem deutschen " +
                          $"{degree.GermanEquivalent} gleichwertig.";
            }
            paragraphs.Add(second);
        }

        // 3 — the further belegt requirements, so the letter answers the posting rather than the CV.
        if (claimable.Count > 2)
        {
            var rest = claimable.Skip(2).Take(3).ToList();
            paragraphs.Add(
                "Zu den weiteren Punkten Ihrer Anzeige: " +
                string.Join(" ", rest.Select(r => $"{r.Text} — {r.Evidence}.")));
        }

        // 4 — the close. Naming the entry date answers a question the posting asked; it is not
        //     padding, and there is deliberately no "Ich freue mich auf Ihre Antwort".
        var start = string.IsNullOrWhiteSpace(posting.StartDate)
            ? "Ich kann kurzfristig anfangen."
            : $"Anfangen kann ich {EntryDate(posting.StartDate)}.";
        paragraphs.Add($"{start} Über ein Gespräch, in dem ich Ihnen zeige, wie ich die Aufgaben in " +
                       "Ihrem Team übernehmen kann, würde ich mich freuen.");

        var attachments = new List<string> { "Lebenslauf" };
        if (profile.Documents.Any(d => d.Kind == DocumentKind.Arbeitszeugnis)) attachments.Add("Arbeitszeugnisse");
        if (profile.Documents.Any(d => d.Kind == DocumentKind.Sprachnachweis)) attachments.Add("Sprachzertifikat");
        if (profile.Documents.Any(d => d.Kind == DocumentKind.AnabinAuszug)) attachments.Add("anabin-Auszug");
        if (profile.Documents.Any(d => d.Kind == DocumentKind.Zertifikat)) attachments.Add("Zertifikate");

        return new LetterContent
        {
            Salutation = Salutation(posting.ContactName),
            Subject = Subject(posting),
            Paragraphs = paragraphs,
            Closing = "Mit freundlichen Grüßen",
            Attachments = attachments,
        };
    }

    // -- the small German conventions -----------------------------------------------------------

    /// <summary>
    /// "Sehr geehrte Frau Dr. Weber" — surname only, title kept, Damen und Herren only as the
    /// fallback it is.
    /// </summary>
    public static string Salutation(string? contact)
    {
        if (string.IsNullOrWhiteSpace(contact)) return "Sehr geehrte Damen und Herren";

        var parts = contact.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        var anrede = parts[0];
        if (anrede is not ("Frau" or "Herr")) return "Sehr geehrte Damen und Herren";

        var titles = parts.Skip(1).Where(p => p.EndsWith('.')).ToList();
        var surname = parts.Skip(1).LastOrDefault(p => !p.EndsWith('.'));
        if (surname is null) return "Sehr geehrte Damen und Herren";

        var prefix = anrede == "Frau" ? "Sehr geehrte Frau" : "Sehr geehrter Herr";
        return string.Join(' ', new[] { prefix }.Concat(titles).Append(surname));
    }

    /// <summary>
    /// The Betreffzeile. It carries the Referenznummer, and it never starts with the word "Betreff"
    /// — DIN 5008 dropped that decades ago and a German reader notices it immediately.
    /// </summary>
    public static string Subject(Posting posting)
    {
        var title = string.IsNullOrWhiteSpace(posting.JobTitle) ? "die ausgeschriebene Stelle" : posting.JobTitle;
        var subject = $"Bewerbung als {title}";
        if (!string.IsNullOrWhiteSpace(posting.Reference))
        {
            subject += $" — Referenznummer {posting.Reference}";
        }
        return subject;
    }

    /// <summary>
    /// The explained gaps of a sequence, as the CV items that name them.
    ///
    /// Only a gap that HAS a German name is written. A reason the wording table does not know
    /// produced an empty title, so the Lebenslauf carried a date range with nothing beside it — a
    /// blank line where the reader was about to find an answer, which is worse than the gap it was
    /// meant to explain. The reason stays stored either way, and the gap card says on screen that
    /// it will go unnamed.
    /// </summary>
    private static IEnumerable<CvItem> NamedGaps(IEnumerable<TimelineGap> gaps)
    {
        foreach (var gap in gaps.Where(g => g.Explained))
        {
            var german = string.IsNullOrWhiteSpace(gap.GermanWording)
                ? GapWording.Suggest(gap.Reason)
                : gap.GermanWording!;
            if (string.IsNullOrWhiteSpace(german)) continue;

            yield return new CvItem
            {
                Period = Period(gap.From, gap.To),
                Title = german,
                Subtitle = "",
                IsGap = true,
            };
        }
    }

    private static string Period(DateOnly from, DateOnly? to) =>
        to is null ? $"{from:MM\\/yyyy} – heute" : $"{from:MM\\/yyyy} – {to:MM\\/yyyy}";

    private static string MonthName(DateOnly d) =>
        CultureInfo.GetCultureInfo("de-DE").DateTimeFormat.GetMonthName(d.Month);

    // There is deliberately no "lowercase the first word so it fits mid-sentence" helper here.
    // German capitalises its nouns, and a posting's requirement almost always starts with one, so
    // any such helper produces "und mitwirkung an den Abschlüssen". Fragments are introduced with a
    // colon instead, where the capital is correct to begin with.

    private static string Article(string jobTitle) =>
        string.IsNullOrWhiteSpace(jobTitle) ? "diese Aufgabe" : $"die Stelle als {jobTitle}";

    /// <summary>
    /// The Eintritt as it can stand in a sentence: "ab sofort" already can, "01.03.2026" cannot.
    /// </summary>
    /// <remarks>
    /// Most postings write the date with its preposition — "ab sofort", "zum nächstmöglichen
    /// Zeitpunkt", "zum 01.03.2026" — and then there is nothing to add. A bare date is what
    /// "Eintritt: 01.03.2026" yields, and what a user types into the correction dialog, and
    /// "Anfangen kann ich 01.03.2026." is not a German sentence.
    /// </remarks>
    public static string EntryDate(string startDate) =>
        char.IsDigit(startDate.FirstOrDefault()) ? $"am {startDate}" : startDate;
}
