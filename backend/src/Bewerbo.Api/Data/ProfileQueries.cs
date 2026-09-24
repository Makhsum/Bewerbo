using Bewerbo.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Data;

/// <summary>
/// The one read of a profile the whole API shares. Four controllers need the same profile with the
/// same six collections attached, and a profile loaded without them is not wrong in an obvious way
/// — it is a Lebenslauf with no jobs in it. So the Include list lives here once rather than being
/// written out at each call site.
///
/// Each collection is ordered here, for the same reason. An unordered Include is returned in key
/// order, and every key in this model is a <see cref="Guid.NewGuid"/> — so the Sprachen the user
/// listed as Ukrainisch, Deutsch, Englisch came back as Englisch, Deutsch, Ukrainisch and went into
/// the Lebenslauf that way. The rule is one line long: order by what the entry means, then by Id so
/// that entries sharing a date still come out the same way twice.
///
/// The sorting happens after the read rather than in the query, and deliberately. SQLite refuses to
/// ORDER BY a DateTimeOffset, which both AddedAt and CreatedAt are — so an ordered Include compiles,
/// runs on the PostgreSQL the product is hosted on, and throws on the SQLite a developer gets by
/// default. A profile carries tens of rows, not thousands; sorting them here costs nothing and
/// behaves the same on both providers.
/// </summary>
/// <summary>
/// What is known about one stored scan WITHOUT reading it: its type, the name it had on the device,
/// how big it is and when it arrived. The bytes are deliberately not here — see
/// <see cref="ProfileQueries.ScanSummariesAsync"/>.
/// </summary>
public record ScanSummary(string ContentType, string FileName, int SizeBytes, DateTimeOffset AddedAt);

public static class ProfileQueries
{
    public static async Task<Profile?> FullProfileAsync(this BewerboDbContext db, Guid id)
    {
        var profile = await db.Profiles
            .Include(p => p.Experience)
            .Include(p => p.Education)
            .Include(p => p.Languages)
            .Include(p => p.Gaps)
            .Include(p => p.Documents)
            .Include(p => p.Applications)
            .FirstOrDefaultAsync(p => p.Id == id);

        if (profile is null) return null;

        // Newest first — the order a Lebenslauf lists them in, and the one every reader of these
        // two collections already sorted for itself.
        profile.Experience = [.. profile.Experience.OrderByDescending(e => e.From).ThenBy(e => e.Id)];
        profile.Education = [.. profile.Education.OrderByDescending(e => e.From).ThenBy(e => e.Id)];
        // The user's own order. Which language comes first is a statement about the person, so it
        // is stored when they send it rather than re-derived from the level.
        profile.Languages = [.. profile.Languages.OrderBy(l => l.Ordinal).ThenBy(l => l.Id)];
        profile.Gaps = [.. profile.Gaps.OrderBy(g => g.From).ThenBy(g => g.Id)];
        // The order they were added to the Mappe, which is what AddedAt has always recorded.
        profile.Documents = [.. profile.Documents.OrderBy(d => d.AddedAt).ThenBy(d => d.Id)];
        profile.Applications = [.. profile.Applications.OrderByDescending(a => a.CreatedAt).ThenBy(a => a.Id)];

        return profile;
    }

    /// <summary>
    /// Which of this profile's documents have a scan stored, and what each one is — keyed by the
    /// document id.
    ///
    /// Not an <c>Include</c> on <see cref="FullProfileAsync"/>, and that is the whole point of it
    /// being a query of its own. The bytes live on the same row, so an Include would read every
    /// scan of the profile into memory on a call that wanted a title and a page count — and the
    /// app asks for the profile again after every single save. The projection below names four
    /// columns, so the Content column is never in the SELECT at all.
    /// </summary>
    public static async Task<IReadOnlyDictionary<Guid, ScanSummary>> ScanSummariesAsync(
        this BewerboDbContext db, Guid profileId)
    {
        var scans = await db.DocumentScans
            .Where(s => s.Document!.ProfileId == profileId)
            .Select(s => new { s.DocumentId, s.ContentType, s.FileName, s.SizeBytes, s.AddedAt })
            .ToListAsync();

        return scans.ToDictionary(
            s => s.DocumentId,
            s => new ScanSummary(s.ContentType, s.FileName, s.SizeBytes, s.AddedAt));
    }
}
