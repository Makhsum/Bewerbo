using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// The Mappe. This holds the RECORD of a document — its title, kind, page count — which is what the
/// Anlagenverzeichnis needs. The scanned file itself is not uploaded here: keeping a Zeugnis on the
/// device rather than on a server is the narrower promise, and it is the one that can be kept
/// without a data-protection argument.
/// </summary>
[Route("api/documents")]
public class DocumentsController(BewerboDbContext db) : BewerboController
{
    [HttpPost("")]
    public async Task<IActionResult> Add([FromQuery, BindRequired] Guid profileId, [FromBody] DocumentDto document)
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
        return Created($"/api/documents/{stored.Id}", stored.ToDto());
    }

    [HttpGet("")]
    public async Task<IActionResult> List([FromQuery, BindRequired] Guid profileId)
    {
        var profile = await db.FullProfileAsync(profileId);
        return profile is null
            ? NotFoundProblem(ProfileController.ProfileMissing)
            : Ok(profile.Documents.Select(d => d.ToDto()).ToList());
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id)
    {
        var document = await db.Documents.FindAsync(id);
        if (document is null) return NotFoundProblem("Es gibt kein Dokument mit dieser Id.");
        db.Documents.Remove(document);
        await db.SaveChangesAsync();
        return NoContent();
    }
}
