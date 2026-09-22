using System.Text.Json;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Bewerbo.Api.Text;
using static Bewerbo.Api.Endpoints.DtoMapping;

namespace Bewerbo.Api.Endpoints;

public static class ApplicationEndpoints
{
    public static void MapApplicationEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/applications").WithTags("Bewerbung");

        group.MapPost("", async (
            CreateApplicationRequest request, BewerboDbContext db, IApplicationWriter writer,
            CancellationToken ct) =>
        {
            var profile = await ProfileEndpoints.Full(db, request.ProfileId);
            var posting = await db.Postings.FindAsync(request.PostingId);
            if (profile is null || posting is null) return Results.NotFound();

            var match = MatchFor(profile, posting);
            var tone = ParseEnum(request.Tone, LetterTone.Sachlich);
            var letter = await writer.WriteLetterAsync(profile, posting, match, tone, ct);

            var application = new Application
            {
                ProfileId = profile.Id,
                PostingId = posting.Id,
                Tone = tone,
                LetterJson = JsonSerializer.Serialize(letter),
                MatchJson = JsonSerializer.Serialize(match.Requirements.Select(r =>
                    new RequirementDto(r.Text, PostingEndpoints.StateName(r.State), r.Evidence, r.Action))),
            };
            db.Applications.Add(application);
            await db.SaveChangesAsync();

            return Results.Created($"/api/applications/{application.Id}",
                ToDto(application, profile, posting, letter, writer.LastSource));
        });

        group.MapGet("/{id:guid}", async (Guid id, BewerboDbContext db) =>
        {
            var loaded = await LoadAsync(db, id);
            if (loaded is null) return Results.NotFound();
            var (application, profile, posting, letter) = loaded.Value;
            return Results.Ok(ToDto(application, profile, posting, letter, "gespeichert"));
        });

        // Re-writing the letter is its own action, because the user changing the tone should not
        // silently lose the version they already read.
        group.MapPost("/{id:guid}/regenerate", async (
            Guid id, string? tone, BewerboDbContext db, IApplicationWriter writer, CancellationToken ct) =>
        {
            var loaded = await LoadAsync(db, id);
            if (loaded is null) return Results.NotFound();
            var (application, profile, posting, _) = loaded.Value;

            application.Tone = ParseEnum(tone, application.Tone);
            var match = MatchFor(profile, posting);
            var letter = await writer.WriteLetterAsync(profile, posting, match, application.Tone, ct);
            application.LetterJson = JsonSerializer.Serialize(letter);
            await db.SaveChangesAsync();

            return Results.Ok(ToDto(application, profile, posting, letter, writer.LastSource));
        });

        // The Prüfung: every check rule-based and countable.
        group.MapPost("/{id:guid}/review", async (Guid id, BewerboDbContext db) =>
        {
            var loaded = await LoadAsync(db, id);
            if (loaded is null) return Results.NotFound();
            var (_, _, posting, letter) = loaded.Value;

            var review = TextReview.Run(letter, posting.ContactName, posting.Reference);
            return Results.Ok(new ReviewDto(review.Passed, review.HintCount,
                review.Checks.Select(c => new ReviewCheckDto(c.Key, c.Title, c.Verdict, c.Detail, c.Items)).ToList()));
        });

        // Maschinenlesbarkeit: render the real file and read it back.
        group.MapPost("/{id:guid}/ats-check", async (Guid id, BewerboDbContext db, IApplicationWriter writer,
            CancellationToken ct) =>
        {
            var loaded = await LoadAsync(db, id);
            if (loaded is null) return Results.NotFound();
            var (_, profile, posting, letter) = loaded.Value;

            var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
            var cv = await writer.WriteCvAsync(profile, timeline, ct);
            var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv,
                profile.Documents.ToList(), DateOnly.FromDateTime(DateTime.Today));

            var result = AtsTextCheck.Run(pdf, profile);
            return Results.Ok(new AtsDto(result.Passed, result.PageCount, result.SizeBytes,
                MergedApplicationDocument.FileName(profile, posting),
                result.Findings.Select(f => new AtsFindingDto(f.Key, f.Label, f.Found, f.Detail)).ToList()));
        });

        // The export: ONE file, named as the card requires.
        group.MapGet("/{id:guid}/pdf", async (
            Guid id, string? parts, bool? inspector, BewerboDbContext db, IApplicationWriter writer,
            CancellationToken ct) =>
        {
            var loaded = await LoadAsync(db, id);
            if (loaded is null) return Results.NotFound();
            var (_, profile, posting, letter) = loaded.Value;

            var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
            var cv = await writer.WriteCvAsync(profile, timeline, ct);

            var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv,
                profile.Documents.ToList(), DateOnly.FromDateTime(DateTime.Today),
                ParseParts(parts), inspector ?? false);

            return Results.File(pdf, "application/pdf", MergedApplicationDocument.FileName(profile, posting));
        });

        group.MapPost("/{id:guid}/status", async (Guid id, StatusRequest request, BewerboDbContext db) =>
        {
            var application = await db.Applications.FindAsync(id);
            if (application is null) return Results.NotFound();

            application.Status = ParseEnum(request.Status, application.Status);
            application.SentAt = application.Status == ApplicationStatus.Entwurf
                ? null
                : application.SentAt ?? DateTimeOffset.UtcNow;
            await db.SaveChangesAsync();
            return Results.Ok(new { id, status = application.Status.ToString() });
        });
    }

    private static ApplicationParts ParseParts(string? parts)
    {
        if (string.IsNullOrWhiteSpace(parts)) return ApplicationParts.All;

        var result = ApplicationParts.None;
        foreach (var part in parts.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            result |= part.ToLowerInvariant() switch
            {
                "anschreiben" => ApplicationParts.Anschreiben,
                "lebenslauf" => ApplicationParts.Lebenslauf,
                "anlagenverzeichnis" or "anlagen" => ApplicationParts.Anlagenverzeichnis,
                _ => ApplicationParts.None,
            };
        }
        return result == ApplicationParts.None ? ApplicationParts.All : result;
    }

    private static MatchResult MatchFor(Domain.Profile profile, Posting posting)
    {
        var requirements = JsonSerializer.Deserialize<List<ExtractedRequirement>>(posting.RequirementsJson) ?? [];
        return RequirementMatcher.Match(profile, requirements);
    }

    private static async Task<(Application, Domain.Profile, Posting, LetterContent)?> LoadAsync(
        BewerboDbContext db, Guid id)
    {
        var application = await db.Applications.FindAsync(id);
        if (application is null) return null;

        var profile = await ProfileEndpoints.Full(db, application.ProfileId);
        var posting = await db.Postings.FindAsync(application.PostingId);
        if (profile is null || posting is null) return null;

        var letter = JsonSerializer.Deserialize<LetterContent>(application.LetterJson) ?? new LetterContent();
        return (application, profile, posting, letter);
    }

    private static ApplicationDto ToDto(Application application, Domain.Profile profile, Posting posting,
        LetterContent letter, string source)
    {
        var requirements = JsonSerializer.Deserialize<List<RequirementDto>>(application.MatchJson) ?? [];
        return new ApplicationDto(
            application.Id, application.ProfileId, application.PostingId,
            application.Tone.ToString(), application.Status.ToString(), source,
            new LetterDto(letter.Salutation, letter.Subject, letter.Paragraphs, letter.Closing, letter.Attachments),
            MergedApplicationDocument.FileName(profile, posting),
            requirements);
    }
}
