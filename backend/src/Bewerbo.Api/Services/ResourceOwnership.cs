using Bewerbo.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Services;

/// <summary>What the <c>{id}</c> in a controller's routes names.</summary>
public enum OwnedResource
{
    Profile,
    Posting,
    Application,
    Document,
}

/// <summary>
/// Which profile a record belongs to — the one question that decides whether a signed-in caller
/// may name it.
///
/// One sentence in one place for the reason <see cref="ProfileAdoption"/> is one: it is asked of
/// every route in the API, and thirty-five copies of it would be thirty-five chances to write the
/// thirty-sixth without one. <see cref="Controllers.OwnershipFilter"/> is what asks it.
///
/// A record that is GONE and a record that belongs to somebody else answer the same way — null and
/// an id that does not match — and the filter above turns both into the same 404. That is
/// deliberate: an answer that separated them would let anybody with a handful of guessed ids find
/// out which of them name real Bewerbungen, which is exactly what this exists to stop.
/// </summary>
public static class ResourceOwnership
{
    public static async Task<Guid?> OwnerOfAsync(BewerboDbContext db, OwnedResource resource, Guid id) =>
        resource switch
        {
            // A profile owns itself. Its id is the one the rest of the API is addressed by, so the
            // question "whose is this profile" is the question "is this the caller's own id".
            OwnedResource.Profile => await db.Profiles
                .Where(p => p.Id == id).Select(p => (Guid?)p.Id).FirstOrDefaultAsync(),
            OwnedResource.Posting => await db.Postings
                .Where(p => p.Id == id).Select(p => (Guid?)p.ProfileId).FirstOrDefaultAsync(),
            OwnedResource.Application => await db.Applications
                .Where(a => a.Id == id).Select(a => (Guid?)a.ProfileId).FirstOrDefaultAsync(),
            // The scan hangs off the document and carries no profile of its own, so the three scan
            // routes are answered by the document they are addressed through.
            OwnedResource.Document => await db.Documents
                .Where(d => d.Id == id).Select(d => (Guid?)d.ProfileId).FirstOrDefaultAsync(),
            _ => null,
        };
}
