using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using Microsoft.EntityFrameworkCore;
using QuestPDF.Fluent;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

[Route("api/profile")]
[IdNames(OwnedResource.Profile)]
public class ProfileController(BewerboDbContext db, ILanguageModel model) : BewerboController
{
    /// <summary>
    /// The person a profile is about, written in one request, onto the profile the caller owns.
    ///
    /// It used to write onto a <c>new Profile()</c>, which was right while a profile WAS the
    /// account and one phone's first run was where it came from. Since the door exists it made a
    /// record nobody could reach: <see cref="OwnershipFilter"/> refuses every later address of a
    /// profile that is not the caller's to the very caller who had just made it, and a profile no
    /// account owns is by definition adoptable, so <see cref="Services.ProfileAdoption"/> then
    /// offered what was in it to the next person who registered.
    ///
    /// An account owns exactly one profile — <see cref="Domain.Account.ProfileId"/> — and
    /// <see cref="AuthController.Register"/> is the one place it comes into being. There is no
    /// second one for this route to create, so it writes onto the first: the same profile every
    /// other route of this controller is addressed by, and one the caller can read, change and
    /// erase the moment this answer comes back.
    ///
    /// Still a 201 at that address, and still nothing in the request naming an owner. Who the
    /// profile belongs to is the server's answer and never the client's claim — the reason
    /// <see cref="BewerboController.SignedInProfileId"/> exists — and a client that sent this
    /// before sends exactly the same thing now.
    /// </summary>
    [HttpPost("")]
    public async Task<IActionResult> Create([FromBody] PersonDto person)
    {
        var profile = await db.Profiles.FindAsync(SignedInProfileId);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        Apply(profile, person);
        await db.SaveChangesAsync();
        return Created($"/api/profile/{profile.Id}", await Load(profile.Id));
    }

    [HttpGet("{id:guid}")]
    public async Task<IActionResult> Get(Guid id) =>
        await Load(id) is { } dto ? Ok(dto) : NotFoundProblem(ProfileMissing, ProfileMissingKind);

    [HttpPatch("{id:guid}/sections/person")]
    public async Task<IActionResult> PatchPerson(Guid id, [FromBody] PersonDto person)
    {
        var profile = await db.Profiles.FindAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);
        Apply(profile, person);
        await db.SaveChangesAsync();
        return Ok(await Load(id));
    }

    [HttpPatch("{id:guid}/sections/berufserfahrung")]
    public async Task<IActionResult> PatchExperience(Guid id, [FromBody] ExperienceDto[] entries)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        // A positional record deserialises an omitted member as null even where the type says it is
        // not nullable, so leaving "industry" out of the JSON used to reach the database as null and
        // come back as a 500 with an empty body — no clue which field was at fault. Say what is
        // wrong, and treat the genuinely optional ones as empty.
        if (Missing(entries, e => e.Position, "position") is { } bad)
        {
            return InvalidRequest("position", bad);
        }

        db.Experience.RemoveRange(profile.Experience);
        foreach (var e in entries)
        {
            db.Experience.Add(new ExperienceEntry
            {
                ProfileId = id,
                Position = e.Position,
                Employer = e.Employer ?? "",
                Location = e.Location ?? "",
                From = ParseDate(e.From),
                To = ParseNullableDate(e.To),
                Workload = e.Workload ?? "",
                Industry = e.Industry ?? "",
                Duties = e.Duties ?? "",
                ReferenceOnFile = e.ReferenceOnFile,
            });
        }
        await db.SaveChangesAsync();
        return Ok(await Load(id));
    }

    [HttpPatch("{id:guid}/sections/ausbildung")]
    public async Task<IActionResult> PatchEducation(Guid id, [FromBody] EducationDto[] entries)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        if (Missing(entries, e => e.Degree, "degree") is { } bad)
        {
            return InvalidRequest("degree", bad);
        }

        db.Education.RemoveRange(profile.Education);
        foreach (var e in entries)
        {
            db.Education.Add(new EducationEntry
            {
                ProfileId = id,
                Degree = e.Degree,
                Institution = e.Institution ?? "",
                Location = e.Location ?? "",
                Country = e.Country ?? "",
                From = ParseDate(e.From),
                To = ParseNullableDate(e.To),
                AnabinAssessment = e.AnabinAssessment,
                GermanEquivalent = e.GermanEquivalent,
                // An equivalence is only ever stored as confirmed when the user confirmed it.
                // Nothing here may set that flag on the user's behalf.
                EquivalenceConfirmed = e.EquivalenceConfirmed
                                       && !string.IsNullOrWhiteSpace(e.GermanEquivalent),
                ZabAssessmentPending = e.ZabAssessmentPending,
            });
        }
        await db.SaveChangesAsync();
        return Ok(await Load(id));
    }

    [HttpPatch("{id:guid}/sections/sprachen")]
    public async Task<IActionResult> PatchLanguages(Guid id, [FromBody] LanguageDto[] entries)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        if (Missing(entries, l => l.Language, "language") is { } bad)
        {
            return InvalidRequest("language", bad);
        }

        db.Languages.RemoveRange(profile.Languages);
        // The position in the array is the user's order, and the only place it exists — the section
        // is replaced whole on every save, so nothing else remembers that Muttersprache came first.
        for (var i = 0; i < entries.Length; i++)
        {
            var l = entries[i];
            db.Languages.Add(new LanguageSkill
            {
                ProfileId = id, Ordinal = i, Language = l.Language, Level = l.Level ?? "",
                CertificateOnFile = l.CertificateOnFile,
            });
        }
        await db.SaveChangesAsync();
        return Ok(await Load(id));
    }

    [HttpGet("{id:guid}/timeline")]
    public async Task<IActionResult> GetTimeline(Guid id)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        var view = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        return Ok(new TimelineDto(
            view.FirstYear, view.LastYear,
            view.Periods.Select(p => new TimelinePeriodDto(
                p.Kind, p.Label, Iso(p.From), Iso(p.To), p.Ongoing)).ToList(),
            view.Gaps.Select(g => new GapDto(
                Iso(g.From), Iso(g.To), g.Months, g.Explained, g.Reason, g.GermanWording)).ToList()));
    }

    // The reason for a gap, in the user's own language — and the German wording it becomes.
    [HttpPost("{id:guid}/gaps")]
    public async Task<IActionResult> PostGap(Guid id, [FromBody] GapUpdateDto update)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        var from = ParseDate(update.From);
        var to = ParseDate(update.To);
        var existing = profile.Gaps.FirstOrDefault(g => g.From == from && g.To == to);

        // The suggestion is only used when the client did not send a wording the user accepted.
        var german = string.IsNullOrWhiteSpace(update.GermanWording)
            ? GapWording.Suggest(update.Reason)
            : update.GermanWording;

        if (existing is null)
        {
            db.Gaps.Add(new GapExplanation
            {
                ProfileId = id, From = from, To = to, Reason = update.Reason, GermanWording = german,
            });
        }
        else
        {
            existing.Reason = update.Reason;
            existing.GermanWording = german;
        }

        await db.SaveChangesAsync();
        return Ok(new GapDto(Iso(from), Iso(to), TimelineService.MonthsBetween(from, to),
            !string.IsNullOrWhiteSpace(update.Reason), update.Reason, german));
    }

    // A German wording proposed for a reason, without storing anything. The client shows it under
    // the input so the user reads what will appear before it appears.
    [HttpGet("gap-wording")]
    public IActionResult GetGapWording([FromQuery, BindRequired] string reason) =>
        Ok(new { reason, german = GapWording.Suggest(reason) });

    // The duties of a position written as results, without storing anything — the same bargain the
    // gap wording strikes: the user reads the original beside the rewrite and decides which of the
    // two is saved. A POST rather than a query string because the duties are several lines of the
    // user's own prose.
    [HttpPost("duty-outcomes")]
    public IActionResult PostDutyOutcomes([FromBody] DutyOutcomesRequest request)
    {
        // Split by the entry's own DutyLines, so "one duty per line" is defined in exactly one place.
        var lines = new ExperienceEntry { Duties = request.Duties ?? "" }.DutyLines;
        return Ok(new DutyOutcomesDto(
            DutyOutcomes.RewriteAll(lines).Select(l => new DutyOutcomeDto(l.Original, l.Outcome)).ToList()));
    }

    // The Lebenslauf on its own — the first thing a user can hold, before any posting exists.
    [HttpPost("{id:guid}/lebenslauf")]
    public async Task<IActionResult> PostLebenslauf(Guid id, [FromServices] IApplicationWriter writer,
        CancellationToken ct)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var cv = await writer.WriteCvAsync(profile, timeline, ct);
        var pdf = new LebenslaufDocument(cv, profile, profile.Template, writer.LastSource).GeneratePdf();

        var name = $"Lebenslauf_{profile.FirstName}_{profile.LastName}.pdf";
        return File(pdf, "application/pdf", name);
    }

    // -- the account's own data ------------------------------------------------------------------

    /// <summary>
    /// Everything this installation holds about the account — Art. 15 and Art. 20 DSGVO in one
    /// answer, because they are the same question asked twice: the settings screen lists the
    /// categories off this body, and saves the very same body as the file the user takes away.
    ///
    /// The postings and the applications are read separately from the profile because they are not
    /// part of it: a <see cref="Posting"/> carries a ProfileId with no relationship behind it.
    /// That is the same fact <see cref="AccountErasure"/> exists for, seen from the other side.
    /// </summary>
    [HttpGet("{id:guid}/data")]
    public async Task<IActionResult> GetData(Guid id)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        var postings = await db.Postings.Where(p => p.ProfileId == id).ToListAsync();
        var applications = await db.Applications.Where(a => a.ProfileId == id).ToListAsync();
        // The scans belong in the answer to "what do you hold about me": a copy of a Zeugnis is
        // the most personal thing on this server. Their metadata and not their bytes — the file
        // itself is fetched one at a time, and a base64 Zeugnis inside a JSON export is a file
        // nobody can open.
        var scans = await db.ScanSummariesAsync(id);

        var person = new PersonDto(profile.InputLanguage, profile.FirstName, profile.LastName,
            profile.Street, profile.PostalCode, profile.City, profile.Phone, profile.Email,
            profile.BirthDate is null ? null : Iso(profile.BirthDate.Value),
            profile.Template.ToString());

        return Ok(new DataExportDto(
            profile.Id,
            DateTimeOffset.UtcNow.ToString("o"),
            Categories(profile, person, postings.Count, applications.Count),
            person,
            profile.Experience.Select(e => e.ToDto()).ToList(),
            profile.Education.Select(e => e.ToDto()).ToList(),
            profile.Languages.Select(l => l.ToDto()).ToList(),
            profile.Gaps.Select(g => new GapDto(Iso(g.From), Iso(g.To),
                TimelineService.MonthsBetween(g.From, g.To),
                !string.IsNullOrWhiteSpace(g.Reason), g.Reason, g.GermanWording)).ToList(),
            profile.Documents.Select(d => d.ToDto(scans.GetValueOrDefault(d.Id))).ToList(),
            postings.Select(p => new ExportedPostingDto(p.Id, p.Company, p.JobTitle, p.Reference,
                p.ParsedAt.ToString("o"), p.SourceText)).ToList(),
            applications.Select(a =>
            {
                var letter = System.Text.Json.JsonSerializer.Deserialize<LetterContent>(a.LetterJson)
                             ?? new LetterContent();
                return new ExportedApplicationDto(a.Id, a.PostingId, a.Tone.ToString(),
                    a.Status.ToString(), a.CreatedAt.ToString("o"),
                    a.SentAt?.ToString("o"),
                    new LetterDto(letter.Salutation, letter.Subject, letter.Paragraphs,
                        letter.Closing, letter.Attachments));
            }).ToList()));
    }

    /// <summary>
    /// Whether this account has agreed to Bewerbo holding the scans of its documents, and when.
    ///
    /// Addressed by the PROFILE id like everything else about the account here — the account says
    /// which profile is the user's, and the rest of the API goes on speaking about the profile;
    /// see <see cref="Contracts.SessionDto"/>. The Documents screen asks this before it offers to
    /// store the first scan, and the upload is refused server-side while it is false, so the
    /// disclosure cannot be skipped by a client that forgets to show it.
    /// </summary>
    [HttpGet("{id:guid}/scan-consent")]
    public async Task<IActionResult> GetScanConsent(Guid id)
    {
        var account = await db.Accounts.FirstOrDefaultAsync(a => a.ProfileId == id);
        return account is null
            ? NotFoundProblem(ProfileMissing, ProfileMissingKind)
            : Ok(Consent(account));
    }

    /// <summary>
    /// Records that the user read the disclosure and agreed — the moment the first scan is allowed
    /// to leave the phone.
    ///
    /// Idempotent, and the FIRST agreement is the one kept: agreeing again must not move the date,
    /// because the date is what the user is entitled to be told back about their own consent.
    /// </summary>
    [HttpPost("{id:guid}/scan-consent")]
    public async Task<IActionResult> AgreeToScans(Guid id)
    {
        var account = await db.Accounts.FirstOrDefaultAsync(a => a.ProfileId == id);
        if (account is null) return NotFoundProblem(ProfileMissing, ProfileMissingKind);

        account.ScansAgreedAt ??= DateTimeOffset.UtcNow;
        await db.SaveChangesAsync();
        return Ok(Consent(account));
    }

    private static ScanConsentDto Consent(Domain.Account account) =>
        new(account.ScansAgreedAt is not null, account.ScansAgreedAt?.ToString("o"));

    /// <summary>
    /// Erases the account and everything held under it — Art. 17 DSGVO. The work is in
    /// <see cref="AccountErasure"/>, which is where the one record the cascade does not reach is
    /// dealt with.
    /// </summary>
    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id) =>
        await AccountErasure.EraseAsync(db, id)
            ? NoContent()
            : NotFoundProblem(ProfileMissing, ProfileMissingKind);

    /// <summary>
    /// What is held, counted by category. Keys and not sentences: the screen writes the names, and a
    /// category the user has nothing in is still listed — "no documents" is an answer to "what do
    /// you have about me", and leaving it out would read as something withheld.
    ///
    /// <c>person</c> is counted in FILLED DETAILS rather than as one record, because that is the
    /// question being asked: "how much of me is here", not "is there a row".
    /// </summary>
    private static List<DataCategoryDto> Categories(
        Domain.Profile profile, PersonDto person, int postings, int applications)
    {
        var details = new[]
        {
            person.FirstName, person.LastName, person.Street, person.PostalCode, person.City,
            person.Phone, person.Email, person.BirthDate,
        };

        return
        [
            new DataCategoryDto("person", details.Count(d => !string.IsNullOrWhiteSpace(d))),
            new DataCategoryDto("berufserfahrung", profile.Experience.Count),
            new DataCategoryDto("ausbildung", profile.Education.Count),
            new DataCategoryDto("sprachen", profile.Languages.Count),
            new DataCategoryDto("luecken", profile.Gaps.Count),
            new DataCategoryDto("anlagen", profile.Documents.Count),
            new DataCategoryDto("stellenanzeigen", postings),
            new DataCategoryDto("bewerbungen", applications),
        ];
    }

    internal const string ProfileMissing = "Es gibt kein Profil mit dieser Id.";
    internal const string ProfileMissingKind = "profile_missing";

    private static void Apply(Profile profile, PersonDto person)
    {
        profile.InputLanguage = person.InputLanguage;
        profile.FirstName = person.FirstName;
        profile.LastName = person.LastName;
        profile.Street = person.Street;
        profile.PostalCode = person.PostalCode;
        profile.City = person.City;
        profile.Phone = person.Phone;
        profile.Email = person.Email;
        profile.BirthDate = ParseNullableDate(person.BirthDate);
        profile.Template = ParseEnum(person.Template, CvTemplate.Klassisch);
    }

    /// <summary>
    /// The one field an entry cannot be without, checked before anything reaches the database.
    /// Returns the message to send back, or null when every entry has it.
    /// </summary>
    private static string? Missing<T>(T[] entries, Func<T, string?> required, string name)
    {
        for (var i = 0; i < entries.Length; i++)
        {
            if (string.IsNullOrWhiteSpace(required(entries[i])))
            {
                return $"Entry {i} has no \"{name}\".";
            }
        }
        return null;
    }

    /// <summary>
    /// What the user typed that is still in their own script. Counted rather than assumed, so the
    /// client can say "3 Einträge warten auf die Übersetzung" instead of a vague warning.
    /// </summary>
    internal static TranslationDto TranslationStatus(Profile profile, bool modelConfigured)
    {
        var freeText = profile.Experience.SelectMany(e => e.DutyLines)
            .Concat(profile.Experience.Select(e => e.Position))
            .Concat(profile.Education.Select(e => e.Degree))
            .Where(s => !string.IsNullOrWhiteSpace(s));

        var pending = Text.ScriptCheck.Untranslated(freeText);
        return new TranslationDto(modelConfigured, pending.Count, pending.Take(3).ToList());
    }

    private async Task<ProfileDto?> Load(Guid id)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return null;

        // Which documents have a copy stored — the one thing about the Mappe that is a fact of the
        // ACCOUNT rather than of this phone, and therefore the one thing the screen cannot work out
        // for itself. Metadata only; see ScanSummariesAsync for why it is not an Include.
        var scans = await db.ScanSummariesAsync(id);

        return new ProfileDto(
            profile.Id,
            new PersonDto(profile.InputLanguage, profile.FirstName, profile.LastName, profile.Street,
                profile.PostalCode, profile.City, profile.Phone, profile.Email,
                profile.BirthDate is null ? null : Iso(profile.BirthDate.Value),
                profile.Template.ToString()),
            profile.Experience.OrderByDescending(e => e.From).Select(e => e.ToDto()).ToList(),
            profile.Education.OrderByDescending(e => e.From).Select(e => e.ToDto()).ToList(),
            profile.Languages.Select(l => l.ToDto()).ToList(),
            profile.Documents.Select(d => d.ToDto(scans.GetValueOrDefault(d.Id))).ToList(),
            ReadinessService.Completeness(profile),
            TranslationStatus(profile, model.IsConfigured));
    }
}
