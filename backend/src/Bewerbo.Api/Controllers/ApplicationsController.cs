using System.Text.Json;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Bewerbo.Api.Text;
using Microsoft.AspNetCore.Mvc;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

[Route("api/applications")]
public class ApplicationsController(BewerboDbContext db) : BewerboController
{
    [HttpPost("")]
    public async Task<IActionResult> Create([FromBody] CreateApplicationRequest request,
        [FromServices] IApplicationWriter writer, CancellationToken ct)
    {
        var profile = await db.FullProfileAsync(request.ProfileId);
        var posting = await db.Postings.FindAsync(request.PostingId);
        if (profile is null) return NotFoundProblem(ProfileController.ProfileMissing);
        if (posting is null) return NotFoundProblem(PostingsController.PostingMissing);

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
                new RequirementDto(r.Text, StateName(r.State), r.Evidence, r.Action, r.Language,
                    r.EvidenceKind, r.EvidenceArgs, r.ActionKind, r.ActionArgs))),
        };
        db.Applications.Add(application);
        await db.SaveChangesAsync();

        return Created($"/api/applications/{application.Id}",
            ToDto(application, profile, posting, letter, writer.LastSource));
    }

    [HttpGet("{id:guid}")]
    public async Task<IActionResult> Get(Guid id)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing);
        var (application, profile, posting, letter) = loaded.Value;
        return Ok(ToDto(application, profile, posting, letter, "gespeichert"));
    }

    // Re-writing the letter is its own action, because the user changing the tone should not
    // silently lose the version they already read.
    [HttpPost("{id:guid}/regenerate")]
    public async Task<IActionResult> Regenerate(Guid id, [FromQuery] string? tone,
        [FromServices] IApplicationWriter writer, CancellationToken ct)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing);
        var (application, profile, posting, _) = loaded.Value;

        application.Tone = ParseEnum(tone, application.Tone);
        var match = MatchFor(profile, posting);
        var letter = await writer.WriteLetterAsync(profile, posting, match, application.Tone, ct);
        application.LetterJson = JsonSerializer.Serialize(letter);
        await db.SaveChangesAsync();

        return Ok(ToDto(application, profile, posting, letter, writer.LastSource));
    }

    // The Prüfung: every check rule-based and countable.
    [HttpPost("{id:guid}/review")]
    public async Task<IActionResult> Review(Guid id)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing);
        var (_, _, posting, letter) = loaded.Value;

        var review = TextReview.Run(letter, posting.ContactName, posting.Reference);
        return Ok(new ReviewDto(review.Passed, review.HintCount,
            review.Checks.Select(c => new ReviewCheckDto(c.Key, c.Title, c.Verdict, c.Detail, c.Items,
                c.DetailKind, c.DetailArgs)).ToList()));
    }

    // Maschinenlesbarkeit: render the real file and read it back.
    [HttpPost("{id:guid}/ats-check")]
    public async Task<IActionResult> AtsCheck(Guid id, [FromServices] IApplicationWriter writer,
        CancellationToken ct)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing);
        var (_, profile, posting, letter) = loaded.Value;

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var cv = await writer.WriteCvAsync(profile, timeline, ct);
        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv,
            profile.Documents.ToList(), DateOnly.FromDateTime(DateTime.Today));

        var result = AtsTextCheck.Run(pdf, profile);
        return Ok(new AtsDto(result.Passed, result.PageCount, result.SizeBytes,
            MergedApplicationDocument.FileName(profile, posting),
            result.Findings.Select(f => new AtsFindingDto(f.Key, f.Label, f.Found, f.Detail, f.DetailKind, f.DetailArgs)).ToList()));
    }

    // The export: ONE file, named as the card requires.
    [HttpGet("{id:guid}/pdf")]
    public async Task<IActionResult> Pdf(Guid id, [FromQuery] string? parts, [FromQuery] bool? inspector,
        [FromServices] IApplicationWriter writer, CancellationToken ct)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing);
        var (_, profile, posting, letter) = loaded.Value;

        // The parts are the user's choice now, so an empty one is reachable — and the renderer
        // throws on it. Said here, where it is an answer, rather than as a 500.
        var chosen = ParseParts(parts);
        if (chosen == ApplicationParts.None) return InvalidRequest("parts", "Keine Mappenteile gewählt.");

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var cv = await writer.WriteCvAsync(profile, timeline, ct);

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv,
            profile.Documents.ToList(), DateOnly.FromDateTime(DateTime.Today),
            chosen, inspector ?? false);

        return File(pdf, "application/pdf", MergedApplicationDocument.FileName(profile, posting));
    }

    [HttpPost("{id:guid}/status")]
    public async Task<IActionResult> SetStatus(Guid id, [FromBody] StatusRequest request)
    {
        var application = await db.Applications.FindAsync(id);
        if (application is null) return NotFoundProblem(ApplicationMissing);

        application.Status = ParseEnum(request.Status, application.Status);
        application.SentAt = application.Status == ApplicationStatus.Entwurf
            ? null
            : application.SentAt ?? DateTimeOffset.UtcNow;
        await db.SaveChangesAsync();
        return Ok(new { id, status = application.Status.ToString() });
    }

    internal const string ApplicationMissing = "Es gibt keine Bewerbung mit dieser Id.";

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

        // What was asked for, even when that is nothing. Falling back to All here made the guard in
        // Pdf unreachable and, worse, turned "parts=lebenslauf" with a typo in it into the whole
        // Mappe: the caller named the parts it wanted and got three. No parts parameter at all is a
        // different thing and still means the whole Mappe, which is what the line above says.
        return result;
    }

    private static MatchResult MatchFor(Domain.Profile profile, Posting posting)
    {
        var requirements = JsonSerializer.Deserialize<List<ExtractedRequirement>>(posting.RequirementsJson) ?? [];
        return RequirementMatcher.Match(profile, requirements);
    }

    private async Task<(Application, Domain.Profile, Posting, LetterContent)?> LoadAsync(Guid id)
    {
        var application = await db.Applications.FindAsync(id);
        if (application is null) return null;

        var profile = await db.FullProfileAsync(application.ProfileId);
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
