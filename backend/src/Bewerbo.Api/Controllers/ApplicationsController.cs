using System.Text.Json;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Bewerbo.Api.Text;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

[Route("api/applications")]
[IdNames(OwnedResource.Application)]
public class ApplicationsController(BewerboDbContext db) : BewerboController
{
    [HttpPost("")]
    public async Task<IActionResult> Create([FromBody] CreateApplicationRequest request,
        [FromServices] IApplicationWriter writer, CancellationToken ct)
    {
        var profile = await db.FullProfileAsync(request.ProfileId);
        var posting = await db.Postings.FindAsync(request.PostingId);
        // The two ids this API takes in a BODY rather than in the address, so OwnershipFilter does
        // not see them. A record of somebody else's is refused as one that is not there, exactly as
        // it is everywhere else — see that filter for why the two answer alike.
        if (profile is null || profile.Id != SignedInProfileId) return NotFoundProblem(ProfileController.ProfileMissing, ProfileController.ProfileMissingKind);
        if (posting is null || posting.ProfileId != SignedInProfileId) return NotFoundProblem(PostingsController.PostingMissing, PostingsController.PostingMissingKind);
        if (Incomplete(profile) is { } refusal) return refusal;

        var match = MatchFor(profile, posting);
        var tone = ParseEnum(request.Tone, LetterTone.Sachlich);
        var letter = await writer.WriteLetterAsync(profile, posting, match, tone, ct);

        var application = new Application
        {
            ProfileId = profile.Id,
            PostingId = posting.Id,
            Tone = tone,
            Source = writer.LastSource,
            LetterJson = JsonSerializer.Serialize(letter),
            MatchJson = JsonSerializer.Serialize(match.Requirements.Select(r =>
                new RequirementDto(r.Text, StateName(r.State), r.Evidence, r.Action, r.Language,
                    r.EvidenceKind, r.EvidenceArgs, r.ActionKind, r.ActionArgs))),
        };
        db.Applications.Add(application);
        await db.SaveChangesAsync();

        return Created($"/api/applications/{application.Id}",
            ToDto(application, profile, posting, letter));
    }

    [HttpGet("{id:guid}")]
    public async Task<IActionResult> Get(Guid id)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing, ApplicationMissingKind);
        var (application, profile, posting, letter) = loaded.Value;
        return Ok(ToDto(application, profile, posting, letter));
    }

    // Re-writing the letter is its own action, because the user changing the tone should not
    // silently lose the version they already read.
    [HttpPost("{id:guid}/regenerate")]
    public async Task<IActionResult> Regenerate(Guid id, [FromQuery] string? tone,
        [FromServices] IApplicationWriter writer, CancellationToken ct)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing, ApplicationMissingKind);
        var (application, profile, posting, _) = loaded.Value;
        // The same guard as on the way in: a profile can be emptied again after the letter was
        // written, and re-writing it then would produce exactly the document Create refuses.
        if (Incomplete(profile) is { } refusal) return refusal;

        application.Tone = ParseEnum(tone, application.Tone);
        var match = MatchFor(profile, posting);
        var letter = await writer.WriteLetterAsync(profile, posting, match, application.Tone, ct);
        application.LetterJson = JsonSerializer.Serialize(letter);
        // The new letter may have come from the other writer — a model that answered this time
        // after being rejected by the Floskel check last time, or a key that has since been set.
        application.Source = writer.LastSource;
        await db.SaveChangesAsync();

        return Ok(ToDto(application, profile, posting, letter));
    }

    // The Prüfung: every check rule-based and countable.
    [HttpPost("{id:guid}/review")]
    public async Task<IActionResult> Review(Guid id)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing, ApplicationMissingKind);
        var (_, _, posting, letter) = loaded.Value;

        var review = TextReview.Run(letter, posting.ContactName, posting.Reference);
        return Ok(new ReviewDto(review.Passed, review.HintCount,
            review.Checks.Select(c => new ReviewCheckDto(c.Key, c.Title, c.Verdict, c.Detail, c.Items,
                c.DetailKind, c.DetailArgs)).ToList(),
            review.NotChecked));
    }

    // Maschinenlesbarkeit: render the real file and read it back.
    [HttpPost("{id:guid}/ats-check")]
    public async Task<IActionResult> AtsCheck(Guid id, [FromServices] IApplicationWriter writer,
        CancellationToken ct)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing, ApplicationMissingKind);
        var (_, profile, posting, letter) = loaded.Value;

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var cv = await writer.WriteCvAsync(profile, timeline, ct);
        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv,
            profile.Documents.ToList(), DateOnly.FromDateTime(DateTime.Today),
            // The whole Mappe, scans included — this check reads the file that is actually sent,
            // and the page count and size it reports are what the export panel then shows. Leaving
            // the scans out here would have that panel name the figures of a shorter file.
            scans: await ScansOf(profile));

        var result = AtsTextCheck.Run(pdf, profile);
        return Ok(new AtsDto(result.Passed, result.PageCount, result.SizeBytes,
            MergedApplicationDocument.FileName(profile, posting),
            result.Findings.Select(f => new AtsFindingDto(f.Key, f.Label, f.Verdict, f.Detail, f.DetailKind, f.DetailArgs, f.Target)).ToList()));
    }

    // The export: ONE file, named as the card requires.
    [HttpGet("{id:guid}/pdf")]
    public async Task<IActionResult> Pdf(Guid id, [FromQuery] string? parts, [FromQuery] bool? inspector,
        [FromServices] IApplicationWriter writer, CancellationToken ct)
    {
        var loaded = await LoadAsync(id);
        if (loaded is null) return NotFoundProblem(ApplicationMissing, ApplicationMissingKind);
        var (_, profile, posting, letter) = loaded.Value;

        // The parts are the user's choice now, so an empty one is reachable — and the renderer
        // throws on it. Said here, where it is an answer, rather than as a 500.
        var chosen = ParseParts(parts);
        if (chosen == ApplicationParts.None) return InvalidRequest("parts", "Keine Mappenteile gewählt.");

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var cv = await writer.WriteCvAsync(profile, timeline, ct);

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv,
            profile.Documents.ToList(), DateOnly.FromDateTime(DateTime.Today),
            chosen, inspector ?? false,
            // Read only when the copies were actually asked for: this is the one query in the API
            // that pulls the scan bytes for a whole profile at once.
            chosen.HasFlag(ApplicationParts.Scans) ? await ScansOf(profile) : null);

        return File(pdf, "application/pdf", MergedApplicationDocument.FileName(profile, posting));
    }

    [HttpPost("{id:guid}/status")]
    public async Task<IActionResult> SetStatus(Guid id, [FromBody] StatusRequest request)
    {
        var application = await db.Applications.FindAsync(id);
        if (application is null) return NotFoundProblem(ApplicationMissing, ApplicationMissingKind);

        application.Status = ParseEnum(request.Status, application.Status);
        application.SentAt = application.Status == ApplicationStatus.Entwurf
            ? null
            : application.SentAt ?? DateTimeOffset.UtcNow;
        await db.SaveChangesAsync();
        return Ok(new { id, status = application.Status.ToString() });
    }

    internal const string ApplicationMissing = "Es gibt keine Bewerbung mit dieser Id.";
    internal const string ApplicationMissingKind = "application_missing";
    internal const string ProfileIncompleteKind = "profile_incomplete";

    /// <summary>
    /// The refusal to write an Anschreiben for a profile that cannot carry one, or null when it
    /// can. The rule itself lives in <see cref="ReadinessService.LetterBlockers"/>, which is also
    /// what the Übersicht sends the screen, so the disabled button and this answer are the same
    /// judgement rather than two that can drift apart.
    ///
    /// A refusal, not a validation error: the request is well-formed and names records that exist.
    /// The kind is what the client writes its own sentence from; the German detail is the fallback.
    /// </summary>
    private ObjectResult? Incomplete(Domain.Profile profile)
    {
        var blockers = ReadinessService.LetterBlockers(profile);
        if (blockers.Count == 0) return null;

        return RefusedProblem(
            $"Für ein Anschreiben fehlt noch: {string.Join(", ", blockers.Select(b => b.Label))}",
            ProfileIncompleteKind);
    }

    /// <summary>
    /// The stored copies of this profile's documents, WITH their bytes — the only read in the API
    /// that wants them, and therefore the only one that pays for them. Every other answer about a
    /// document carries <see cref="Data.ScanSummary"/> instead.
    /// </summary>
    private async Task<IReadOnlyList<DocumentScan>> ScansOf(Domain.Profile profile) =>
        await db.DocumentScans.Where(s => s.Document!.ProfileId == profile.Id).ToListAsync();

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
                "scans" => ApplicationParts.Scans,
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

    // The writer comes off the application itself rather than being passed in: a caller that has
    // not just written a letter has nothing truthful to pass, and the one that had to invent
    // something passed "gespeichert" — which the screen reads as "not the model", so every
    // reopened application told the reader the rules had written it.
    private static ApplicationDto ToDto(Application application, Domain.Profile profile, Posting posting,
        LetterContent letter)
    {
        var requirements = JsonSerializer.Deserialize<List<RequirementDto>>(application.MatchJson) ?? [];
        return new ApplicationDto(
            application.Id, application.ProfileId, application.PostingId,
            application.Tone.ToString(), application.Status.ToString(), application.Source,
            new LetterDto(letter.Salutation, letter.Subject, letter.Paragraphs, letter.Closing, letter.Attachments),
            MergedApplicationDocument.FileName(profile, posting),
            requirements);
    }
}
