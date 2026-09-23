using Bewerbo.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Data;

/// <summary>
/// The one read of a profile the whole API shares. Four controllers need the same profile with the
/// same six collections attached, and a profile loaded without them is not wrong in an obvious way
/// — it is a Lebenslauf with no jobs in it. So the Include list lives here once rather than being
/// written out at each call site.
/// </summary>
public static class ProfileQueries
{
    public static Task<Profile?> FullProfileAsync(this BewerboDbContext db, Guid id) =>
        db.Profiles
            .Include(p => p.Experience)
            .Include(p => p.Education)
            .Include(p => p.Languages)
            .Include(p => p.Gaps)
            .Include(p => p.Documents)
            .Include(p => p.Applications)
            .FirstOrDefaultAsync(p => p.Id == id);
}
