using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Recognition;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using QuestPDF.Fluent;
using Microsoft.EntityFrameworkCore;
using static Bewerbo.Api.Endpoints.DtoMapping;

namespace Bewerbo.Api.Endpoints;

public static class ProfileEndpoints
{
    public static void MapProfileEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/profile").WithTags("Profil");

        group.MapPost("", async (PersonDto person, BewerboDbContext db, ILanguageModel model) =>
        {
            var profile = new Profile();
            Apply(profile, person);
            db.Profiles.Add(profile);
            await db.SaveChangesAsync();
            return Results.Created($"/api/profile/{profile.Id}", await Load(db, profile.Id, model.IsConfigured));
        });

        group.MapGet("/{id:guid}", async (Guid id, BewerboDbContext db, ILanguageModel model) =>
            await Load(db, id, model.IsConfigured) is { } dto ? Results.Ok(dto) : Results.NotFound());

        group.MapPatch("/{id:guid}/sections/person", async (Guid id, PersonDto person, BewerboDbContext db, ILanguageModel model) =>
        {
            var profile = await db.Profiles.FindAsync(id);
            if (profile is null) return Results.NotFound();
            Apply(profile, person);
            await db.SaveChangesAsync();
            return Results.Ok(await Load(db, id, model.IsConfigured));
        });

        group.MapPatch("/{id:guid}/sections/berufserfahrung",
            async (Guid id, ExperienceDto[] entries, BewerboDbContext db, ILanguageModel model) =>
            {
                var profile = await Full(db, id);
                if (profile is null) return Results.NotFound();

                db.Experience.RemoveRange(profile.Experience);
                foreach (var e in entries)
                {
                    db.Experience.Add(new ExperienceEntry
                    {
                        ProfileId = id,
                        Position = e.Position,
                        Employer = e.Employer,
                        Location = e.Location,
                        From = ParseDate(e.From),
                        To = ParseNullableDate(e.To),
                        Workload = e.Workload,
                        Industry = e.Industry,
                        Duties = e.Duties,
                        ReferenceOnFile = e.ReferenceOnFile,
                    });
                }
                await db.SaveChangesAsync();
                return Results.Ok(await Load(db, id, model.IsConfigured));
            });

        group.MapPatch("/{id:guid}/sections/ausbildung",
            async (Guid id, EducationDto[] entries, BewerboDbContext db, ILanguageModel model) =>
            {
                var profile = await Full(db, id);
                if (profile is null) return Results.NotFound();

                db.Education.RemoveRange(profile.Education);
                foreach (var e in entries)
                {
                    db.Education.Add(new EducationEntry
                    {
                        ProfileId = id,
                        Degree = e.Degree,
                        Institution = e.Institution,
                        Location = e.Location,
                        Country = e.Country,
                        From = ParseDate(e.From),
                        To = ParseNullableDate(e.To),
                        AnabinAssessment = e.AnabinAssessment,
                        GermanEquivalent = e.GermanEquivalent,
                        // An equivalence is only ever stored as confirmed when the user confirmed
                        // it. Nothing here may set that flag on the user's behalf.
                        EquivalenceConfirmed = e.EquivalenceConfirmed
                                               && !string.IsNullOrWhiteSpace(e.GermanEquivalent),
                    });
                }
                await db.SaveChangesAsync();
                return Results.Ok(await Load(db, id, model.IsConfigured));
            });

        group.MapPatch("/{id:guid}/sections/sprachen",
            async (Guid id, LanguageDto[] entries, BewerboDbContext db, ILanguageModel model) =>
            {
                var profile = await Full(db, id);
                if (profile is null) return Results.NotFound();

                db.Languages.RemoveRange(profile.Languages);
                foreach (var l in entries)
                {
                    db.Languages.Add(new LanguageSkill
                    {
                        ProfileId = id, Language = l.Language, Level = l.Level,
                        CertificateOnFile = l.CertificateOnFile,
                    });
                }
                await db.SaveChangesAsync();
                return Results.Ok(await Load(db, id, model.IsConfigured));
            });

        group.MapGet("/{id:guid}/timeline", async (Guid id, BewerboDbContext db) =>
        {
            var profile = await Full(db, id);
            if (profile is null) return Results.NotFound();

            var view = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
            return Results.Ok(new TimelineDto(
                view.FirstYear, view.LastYear,
                view.Periods.Select(p => new TimelinePeriodDto(
                    p.Kind, p.Label, Iso(p.From), Iso(p.To), p.Ongoing)).ToList(),
                view.Gaps.Select(g => new GapDto(
                    Iso(g.From), Iso(g.To), g.Months, g.Explained, g.Reason, g.GermanWording)).ToList()));
        });

        // The reason for a gap, in the user's own language — and the German wording it becomes.
        group.MapPost("/{id:guid}/gaps", async (Guid id, GapUpdateDto update, BewerboDbContext db) =>
        {
            var profile = await Full(db, id);
            if (profile is null) return Results.NotFound();

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
            return Results.Ok(new GapDto(Iso(from), Iso(to), TimelineService.MonthsBetween(from, to),
                !string.IsNullOrWhiteSpace(update.Reason), update.Reason, german));
        });

        // A German wording proposed for a reason, without storing anything. The client shows it
        // under the input so the user reads what will appear before it appears.
        group.MapGet("/gap-wording", (string reason) =>
            Results.Ok(new { reason, german = GapWording.Suggest(reason) }));

        // The Lebenslauf on its own — the first thing a user can hold, before any posting exists.
        group.MapPost("/{id:guid}/lebenslauf", async (
            Guid id, BewerboDbContext db, IApplicationWriter writer, CancellationToken ct) =>
        {
            var profile = await Full(db, id);
            if (profile is null) return Results.NotFound();

            var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
            var cv = await writer.WriteCvAsync(profile, timeline, ct);
            var pdf = new LebenslaufDocument(cv, profile, profile.Template).GeneratePdf();

            var name = $"Lebenslauf_{profile.FirstName}_{profile.LastName}.pdf";
            return Results.File(pdf, "application/pdf", name);
        });

        app.MapGet("/api/recognition/degrees", (string? country, string? q) =>
            Results.Ok(AnabinCatalog.Search(country, q)));
    }

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

    internal static Task<Profile?> Full(BewerboDbContext db, Guid id) =>
        db.Profiles
            .Include(p => p.Experience)
            .Include(p => p.Education)
            .Include(p => p.Languages)
            .Include(p => p.Gaps)
            .Include(p => p.Documents)
            .Include(p => p.Applications)
            .FirstOrDefaultAsync(p => p.Id == id);

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

    private static async Task<ProfileDto?> Load(BewerboDbContext db, Guid id, bool modelConfigured)
    {
        var profile = await Full(db, id);
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
            TranslationStatus(profile, modelConfigured));
    }
}
