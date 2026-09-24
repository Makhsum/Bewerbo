using Bewerbo.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Services;

/// <summary>
/// Whether the nameless profile a phone is still naming may be kept by a new account.
///
/// One sentence, in one place, for the reason <see cref="AccountErasure"/> is one: it is read
/// twice and the two readings must not be able to disagree. The door asks it to decide whether to
/// OFFER to keep the Lebenslauf, and the registration asks it again to decide whether to actually
/// bind it. While those were two copies of the same condition the offer could promise what the
/// registration then silently refused — and it did: a profile id rides into a Google backup inside
/// bewerbo.xml while the session token beside it is deliberately excluded, so a restored or
/// transferred phone names a profile that is still there and already has an owner. The door
/// offered to keep it, the server quietly handed out a fresh empty one instead, and nothing on the
/// way through said the Lebenslauf had been left behind.
///
/// A profile that is gone and a profile that is spoken for get the same answer. Neither is this
/// phone's to give away, and separating them would say whether an id somebody typed names an
/// account — the thing the door's one refusal message exists to avoid saying.
/// </summary>
public static class ProfileAdoption
{
    public static async Task<bool> IsAdoptableAsync(BewerboDbContext db, Guid profileId) =>
        await db.Profiles.AnyAsync(p => p.Id == profileId)
        && !await db.Accounts.AnyAsync(a => a.ProfileId == profileId);
}
