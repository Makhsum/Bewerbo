using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// The door: creating an account, coming back to one, and leaving it.
///
/// Four routes and no more. What a signed-in user then does is unchanged — every other controller
/// is still addressed by the profile id, and <see cref="SessionDto.ProfileId"/> is what tells the
/// client which one is theirs. The account is what makes that id something the user carries rather
/// than something one phone happens to remember.
///
/// A refusal here says as little as it can get away with. "E-Mail-Adresse oder Passwort stimmt
/// nicht" is one answer for both halves on purpose: telling a stranger that an address HAS an
/// account here is telling them something about the person behind it, and the user who mistyped
/// one of the two retypes both anyway.
/// </summary>
[Route("api/auth")]
public class AuthController(BewerboDbContext db) : BewerboController
{
    [HttpPost("register")]
    public async Task<IActionResult> Register([FromBody] RegisterRequest request)
    {
        var email = Normalise(request.Email);
        if (!LooksLikeEmail(email)) return RefusedProblem(EmailInvalid, EmailInvalidKind);
        if ((request.Password ?? "").Length < MinimumPasswordLength)
        {
            return RefusedProblem(PasswordTooShort, PasswordTooShortKind);
        }

        if (await db.Accounts.AnyAsync(a => a.Email == email))
        {
            return RefusedProblem(EmailTaken, EmailTakenKind);
        }

        var account = new Account
        {
            Email = email,
            Password = PasswordHash.Create(request.Password!),
            ProfileId = await AdoptOrCreateProfileAsync(request.AdoptProfileId),
        };
        db.Accounts.Add(account);

        var session = Issue(account);
        await db.SaveChangesAsync();
        return Created($"/api/profile/{account.ProfileId}", session);
    }

    [HttpPost("sign-in")]
    public async Task<IActionResult> SignIn([FromBody] CredentialsRequest request)
    {
        var account = await db.Accounts.FirstOrDefaultAsync(a => a.Email == Normalise(request.Email));

        // Verified even when there is no account, against a record that cannot match: a sign-in
        // that comes back instantly for an unknown address and slowly for a known one tells the
        // caller which is which, whatever the message says.
        var correct = PasswordHash.Verify(request.Password ?? "", account?.Password ?? NoSuchPassword);
        if (account is null || !correct)
        {
            return RefusedProblem(CredentialsRejected, CredentialsRejectedKind);
        }

        var session = Issue(account);
        await db.SaveChangesAsync();
        return Ok(session);
    }

    /// <summary>
    /// Who the token names — what the app asks at every launch before it draws anything.
    ///
    /// The answer is the same shape a sign-in returns, token and all, so the client has one thing
    /// to store and one thing to read it back into.
    /// </summary>
    [HttpGet("session")]
    public async Task<IActionResult> Session()
    {
        var token = SessionToken.FromHeader(Request.Headers.Authorization);
        var account = token is null ? null : await AccountFor(token);
        return account is null
            ? NotSignedInProblem(SessionInvalid, SessionInvalidKind)
            : Ok(new SessionDto(token!, account.Id, account.Email, account.ProfileId));
    }

    /// <summary>
    /// Ends this device's session and no other: the row for THIS token goes, and the same account
    /// signed in on another phone stays signed in.
    ///
    /// A token the server does not know is still a 204. The caller asked to be signed out and they
    /// are — reporting a failure would only invite a client to keep a token it cannot use.
    ///
    /// Named for what it does to the record rather than "SignOut": ControllerBase already has a
    /// method of that name, and hiding it would leave the next reader of this file wondering which
    /// of the two a bare call reaches.
    /// </summary>
    [HttpPost("sign-out")]
    public async Task<IActionResult> EndSession()
    {
        var token = SessionToken.FromHeader(Request.Headers.Authorization);
        if (token is not null)
        {
            var hash = SessionToken.HashOf(token);
            db.AuthTokens.RemoveRange(await db.AuthTokens.Where(t => t.TokenHash == hash).ToListAsync());
            await db.SaveChangesAsync();
        }

        return NoContent();
    }

    /// <summary>
    /// The profile the new account owns: the one this phone was already working on, or a fresh one.
    ///
    /// The adoption is refused silently rather than reported, and that is deliberate. An id that
    /// names nothing, or names a profile that already has an owner, is not something the user can
    /// act on — and a message about it would be a message about whether somebody else's account
    /// exists. They get an account either way; what they do not get is somebody else's Lebenslauf.
    /// </summary>
    private async Task<Guid> AdoptOrCreateProfileAsync(Guid? adopt)
    {
        if (adopt is { } id
            && await db.Profiles.AnyAsync(p => p.Id == id)
            && !await db.Accounts.AnyAsync(a => a.ProfileId == id))
        {
            return id;
        }

        var profile = new Profile();
        db.Profiles.Add(profile);
        return profile.Id;
    }

    /// <summary>
    /// The account a token belongs to, or null. Nothing here trusts the token itself — the hash is
    /// what the row carries, so a database read out of the server says who is signed in and not how
    /// to sign in as them.
    /// </summary>
    private async Task<Account?> AccountFor(string token)
    {
        var hash = SessionToken.HashOf(token);
        return await db.AuthTokens.Where(t => t.TokenHash == hash)
            .Select(t => t.Account)
            .FirstOrDefaultAsync();
    }

    /// <summary>
    /// A new session for this device, added to the change tracker. The caller saves — a token that
    /// reached a phone and not the database would be a sign-in that stops working on the next call.
    /// </summary>
    private SessionDto Issue(Account account)
    {
        var token = SessionToken.Issue();
        db.AuthTokens.Add(new AuthToken { AccountId = account.Id, TokenHash = SessionToken.HashOf(token) });
        return new SessionDto(token, account.Id, account.Email, account.ProfileId);
    }

    /// <summary>The address as it is stored and compared: trimmed and lower-cased.</summary>
    private static string Normalise(string? email) => (email ?? "").Trim().ToLowerInvariant();

    /// <summary>
    /// Enough of an address to be worth a sign-in attempt, and no more. Whether it is deliverable is
    /// not something a regular expression finds out, and refusing a real address because it has an
    /// unusual shape is the worse of the two mistakes.
    /// </summary>
    private static bool LooksLikeEmail(string email)
    {
        var at = email.IndexOf('@');
        if (at <= 0 || at != email.LastIndexOf('@') || email.Any(char.IsWhiteSpace)) return false;

        var host = email[(at + 1)..];
        var dot = host.IndexOf('.');
        return dot > 0 && dot < host.Length - 1;
    }

    /// <summary>What the mockup writes under the password field, and the one rule applied to it.</summary>
    internal const int MinimumPasswordLength = 8;

    /// <summary>
    /// A record no password verifies against, so that the unknown-address case costs the same work
    /// as the wrong-password one. Written in the format <see cref="PasswordHash"/> reads, with a
    /// hash of the right length that nothing derives to.
    /// </summary>
    private static readonly string NoSuchPassword = PasswordHash.Create(Guid.NewGuid().ToString());

    internal const string EmailInvalid = "Das ist keine E-Mail-Adresse.";
    internal const string EmailInvalidKind = "email_invalid";

    internal const string PasswordTooShort = "Das Passwort braucht mindestens acht Zeichen.";
    internal const string PasswordTooShortKind = "password_too_short";

    internal const string EmailTaken = "Zu dieser E-Mail-Adresse gibt es schon ein Konto.";
    internal const string EmailTakenKind = "email_taken";

    internal const string CredentialsRejected = "E-Mail-Adresse oder Passwort stimmt nicht.";
    internal const string CredentialsRejectedKind = "credentials_rejected";

    internal const string SessionInvalid = "Diese Anmeldung gilt nicht mehr.";
    internal const string SessionInvalidKind = "session_invalid";
}
