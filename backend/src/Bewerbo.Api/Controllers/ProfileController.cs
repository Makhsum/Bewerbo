using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using QuestPDF.Fluent;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

[Route("api/profile")]
public class ProfileController(BewerboDbContext db, ILanguageModel model) : BewerboController
{
    [HttpPost("")]
    public async Task<IActionResult> Create([FromBody] PersonDto person)
    {
        var profile = new Profile();
        Apply(profile, person);
        db.Profiles.Add(profile);
        await db.SaveChangesAsync();
        return Created($"/api/profile/{profile.Id}", await Load(profile.Id));
    }

    [HttpGet("{id:guid}")]
    public async Task<IActionResult> Get(Guid id) =>
        await Load(id) is { } dto ? Ok(dto) : NotFoundProblem(ProfileMissing);

    [HttpPatch("{id:guid}/sections/person")]
    public async Task<IActionResult> PatchPerson(Guid id, [FromBody] PersonDto person)
    {
        var profile = await db.Profiles.FindAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing);
        Apply(profile, person);
        await db.SaveChangesAsync();
        return Ok(await Load(id));
    }

    [HttpPatch("{id:guid}/sections/berufserfahrung")]
    public async Task<IActionResult> PatchExperience(Guid id, [FromBody] ExperienceDto[] entries)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing);

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
        if (profile is null) return NotFoundProblem(ProfileMissing);

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
            });
        }
        await db.SaveChangesAsync();
        return Ok(await Load(id));
    }

    [HttpPatch("{id:guid}/sections/sprachen")]
    public async Task<IActionResult> PatchLanguages(Guid id, [FromBody] LanguageDto[] entries)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing);

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
        if (profile is null) return NotFoundProblem(ProfileMissing);

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
        if (profile is null) return NotFoundProblem(ProfileMissing);

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

    // The Lebenslauf on its own — the first thing a user can hold, before any posting exists.
    [HttpPost("{id:guid}/lebenslauf")]
    public async Task<IActionResult> PostLebenslauf(Guid id, [FromServices] IApplicationWriter writer,
        CancellationToken ct)
    {
        var profile = await db.FullProfileAsync(id);
        if (profile is null) return NotFoundProblem(ProfileMissing);

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var cv = await writer.WriteCvAsync(profile, timeline, ct);
        var pdf = new LebenslaufDocument(cv, profile, profile.Template).GeneratePdf();

        var name = $"Lebenslauf_{profile.FirstName}_{profile.LastName}.pdf";
        return File(pdf, "application/pdf", name);
    }

    internal const string ProfileMissing = "Es gibt kein Profil mit dieser Id.";

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

        return new ProfileDto(
            profile.Id,
            new PersonDto(profile.InputLanguage, profile.FirstName, profile.LastName, profile.Street,
                profile.PostalCode, profile.City, profile.Phone, profile.Email,
                profile.BirthDate is null ? null : Iso(profile.BirthDate.Value),
                profile.Template.ToString()),
            profile.Experience.OrderByDescending(e => e.From).Select(e => e.ToDto()).ToList(),
            profile.Education.OrderByDescending(e => e.From).Select(e => e.ToDto()).ToList(),
            profile.Languages.Select(l => l.ToDto()).ToList(),
            profile.Documents.Select(d => d.ToDto()).ToList(),
            ReadinessService.Completeness(profile),
            TranslationStatus(profile, model.IsConfigured));
    }
}
