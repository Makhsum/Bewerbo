using Bewerbo.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Services;

/// <summary>
/// Deleting an account and everything held under it — Art. 17 DSGVO, which is only kept if NOTHING
/// is left behind.
///
/// It is its own named thing rather than four lines in the controller because of the trap it exists
/// to close: a <see cref="Domain.Posting"/> carries a ProfileId but has no relationship declared in
/// <see cref="BewerboDbContext"/>, so the cascade that takes the Berufserfahrung, the Sprachen, the
/// Anlagen and the Bewerbungen does NOT take the postings. A user who asked to be erased would have
/// left the full text of every advert they pasted on the server, each one still bearing their
/// profile id. That is exactly the kind of leftover nobody notices, so it gets a test of its own.
/// </summary>
public static class AccountErasure
{
    /// <summary>
    /// Erases the account and returns false when there was none — the controller's 404.
    ///
    /// The profile is loaded WITH its collections so the delete cascades in the change tracker as
    /// well as in the database: the SQLite file a dev machine runs on is created by EnsureCreated
    /// and a foreign key there is only as good as the pragma that enforces it.
    /// </summary>
    public static async Task<bool> EraseAsync(BewerboDbContext db, Guid accountId)
    {
        var profile = await db.FullProfileAsync(accountId);
        if (profile is null) return false;

        // The postings first, and by hand: nothing else in the model knows they belong to a profile.
        var postings = await db.Postings.Where(p => p.ProfileId == accountId).ToListAsync();
        db.Postings.RemoveRange(postings);

        // The account that owns this profile, for the same reason and one more: leaving it behind
        // would leave the user's e-mail address on the server after they asked to be erased, and
        // leave every device still signed in to a profile that is gone. Its sessions go with it
        // down the cascade the model does declare.
        var accounts = await db.Accounts.Where(a => a.ProfileId == accountId).ToListAsync();
        db.Accounts.RemoveRange(accounts);

        db.Profiles.Remove(profile);
        await db.SaveChangesAsync();
        return true;
    }
}
