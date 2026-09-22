using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using static Bewerbo.Api.Endpoints.DtoMapping;

namespace Bewerbo.Api.Endpoints;

public static class LockerEndpoints
{
    public static void MapLockerEndpoints(this IEndpointRouteBuilder app)
    {
        // The Mappe. This holds the RECORD of a document — its title, kind, page count — which is
        // what the Anlagenverzeichnis needs. The scanned file itself is not uploaded here: keeping
        // a Zeugnis on the device rather than on a server is the narrower promise, and it is the
        // one that can be kept without a data-protection argument.
        var group = app.MapGroup("/api/documents").WithTags("Mappe");

        group.MapPost("", async (Guid profileId, DocumentDto document, BewerboDbContext db) =>
        {
            var stored = new StoredDocument
            {
                ProfileId = profileId,
                Title = document.Title,
                Kind = ParseEnum(document.Kind, DocumentKind.Sonstiges),
                Note = document.Note,
                PageCount = document.PageCount <= 0 ? 1 : document.PageCount,
            };
            db.Documents.Add(stored);
            await db.SaveChangesAsync();
            return Results.Created($"/api/documents/{stored.Id}", stored.ToDto());
        });

        group.MapGet("", async (Guid profileId, BewerboDbContext db) =>
        {
            var profile = await ProfileEndpoints.Full(db, profileId);
            return profile is null
                ? Results.NotFound()
                : Results.Ok(profile.Documents.Select(d => d.ToDto()).ToList());
        });

        group.MapDelete("/{id:guid}", async (Guid id, BewerboDbContext db) =>
        {
            var document = await db.Documents.FindAsync(id);
            if (document is null) return Results.NotFound();
            db.Documents.Remove(document);
            await db.SaveChangesAsync();
            return Results.NoContent();
        });

        app.MapGet("/api/overview/{profileId:guid}", async (Guid profileId, BewerboDbContext db) =>
        {
            var profile = await ProfileEndpoints.Full(db, profileId);
            if (profile is null) return Results.NotFound();

            var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
            var postings = db.Postings.Where(p => p.ProfileId == profileId).ToList();
            return Results.Ok(ReadinessService.Build(profile, timeline, postings));
        });
    }
}
