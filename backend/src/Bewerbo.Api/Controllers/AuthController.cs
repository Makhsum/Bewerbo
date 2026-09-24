using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Mail;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// The door: creating an account, coming back to one, getting back in without the password, and
/// leaving it.
///
/// What a signed-in user then does is unchanged — every other controller is still addressed by the
/// profile id, and <see cref="SessionDto.ProfileId"/> is what tells the client which one is theirs.
/// The account is what makes that id something the user carries rather than something one phone
/// happens to remember.
///
/// A refusal here says as little as it can get away with. "E-Mail-Adresse oder Passwort stimmt
/// nicht" is one answer for both halves on purpose: telling a stranger that an address HAS an
/// account here is telling them something about the person behind it, and the user who mistyped
/// one of the two retypes both anyway. The reset follows the same line — it answers the same way
/// whether or not the address is one this server knows.
///
/// The one controller that is open to anyone, and it has to be: these are the routes a caller
/// reaches BEFORE they have a session, and the two that do carry a token —
/// <see cref="Session"/> and <see cref="EndSession"/> — have to be able to answer for one the
/// server no longer knows. They read the header themselves; the rest of the API leaves that to
/// <see cref="Services.SessionAuthenticationHandler"/>.
/// </summary>
[AllowAnonymous]
[Route("api/auth")]
public class AuthController(BewerboDbContext db, IMailSender mail, ILogger<AuthController> log)
    : BewerboController
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
    /// A user who cannot remember their password asks for the one mail this product sends.
    ///
    /// The answer is 202 whatever happens next, and that is the whole reason this route is safe to
    /// leave open: a 404 for an address nobody has registered would make this the fastest way to
    /// find out who has an account here, which is exactly what <see cref="SignIn"/> refuses to say.
    /// The user reads the same sentence either way — the code is in the mail if the account exists,
    /// and there is no mail if it does not.
    ///
    /// The mail is sent inside the request rather than handed to a queue: there is no queue in this
    /// backend, and a user staring at a screen that says "check your mail" would rather wait the
    /// second it takes than be told it worked before it did. A send that FAILS is logged and
    /// swallowed for the reason above — a route that answers 500 for a known address and 202 for an
    /// unknown one has said which is which.
    /// </summary>
    [HttpPost("forgot-password")]
    public async Task<IActionResult> ForgotPassword([FromBody] ForgotPasswordRequest request)
    {
        var email = Normalise(request.Email);
        if (!LooksLikeEmail(email)) return RefusedProblem(EmailInvalid, EmailInvalidKind);

        var account = await db.Accounts.FirstOrDefaultAsync(a => a.Email == email);
        if (account is not null)
        {
            // One live code per account. Asking twice is what a user does when the first mail has
            // not arrived yet, and two codes that both work would double what a single guess buys.
            db.PasswordResets.RemoveRange(
                await db.PasswordResets.Where(r => r.AccountId == account.Id).ToListAsync());

            var code = ResetCode.Issue();
            db.PasswordResets.Add(new PasswordReset
            {
                AccountId = account.Id,
                CodeHash = PasswordHash.Create(code),
                ExpiresAt = DateTimeOffset.UtcNow + ResetCode.Lifetime,
            });
            await db.SaveChangesAsync();

            // account.Email and not the address as it was typed: the mail goes to the account's own
            // record of it. The two differ only in capitalisation today, and this is the line that
            // has to stay right if that ever stops being true.
            try
            {
                await mail.SendAsync(PasswordResetMail.For(account.Email, request.Language, code));
            }
            catch (Exception ex)
            {
                log.LogError(ex, "The password reset mail could not be sent.");
            }
        }

        return Accepted();
    }

    /// <summary>
    /// The code out of that mail, spent on a new password, and the user is back inside.
    ///
    /// Three things end a reset and they share one refusal: a code that is wrong, a code that is
    /// older than <see cref="ResetCode.Lifetime"/>, and a code that has already been spent. Telling
    /// them apart would tell somebody working through six-digit numbers which of their guesses was
    /// close to something, so the user is told the one thing they can act on — ask for a new one.
    ///
    /// Spending the reset deletes the row, the way signing out deletes a session: there is then
    /// nothing for the same code to match a second time. The sessions of every OTHER device go with
    /// it. Somebody resetting a password is somebody who may have lost the old one to another
    /// person, and leaving that person's phone signed in would make the reset a gesture.
    ///
    /// What comes back is an ordinary <see cref="SessionDto"/>, so the app lands where a sign-in
    /// lands: the account's own profile, with everything that was ever written into it.
    /// </summary>
    [HttpPost("reset-password")]
    public async Task<IActionResult> ResetPassword([FromBody] ResetPasswordRequest request)
    {
        if ((request.Password ?? "").Length < MinimumPasswordLength)
        {
            return RefusedProblem(PasswordTooShort, PasswordTooShortKind);
        }

        var email = Normalise(request.Email);
        var reset = await db.PasswordResets
            .Include(r => r.Account)
            .FirstOrDefaultAsync(r => r.Account!.Email == email);

        if (reset is null || !ResetCode.IsLive(reset, DateTimeOffset.UtcNow))
        {
            return RefusedProblem(ResetCodeRejected, ResetCodeRejectedKind);
        }

        if (!PasswordHash.Verify((request.Code ?? "").Trim(), reset.CodeHash))
        {
            // Written down before the refusal goes out. What makes five guesses five is that the
            // fifth one is still counted on the way to being refused.
            reset.Attempts++;
            await db.SaveChangesAsync();
            return RefusedProblem(ResetCodeRejected, ResetCodeRejectedKind);
        }

        var account = reset.Account!;
        account.Password = PasswordHash.Create(request.Password!);
        db.PasswordResets.Remove(reset);
        db.AuthTokens.RemoveRange(
            await db.AuthTokens.Where(t => t.AccountId == account.Id).ToListAsync());

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
    /// Whether the nameless profile this phone is still naming may be kept by a new account.
    ///
    /// The door writes "the Lebenslauf on this phone does not belong to an account yet — creating
    /// an account keeps it" above the form, and that sentence has to be true at the moment it is
    /// read. A stored profile id outlives the state it was written in: it rides into a Google
    /// backup inside bewerbo.xml while the token beside it is deliberately excluded, so a restored
    /// or transferred phone holds the id of a profile that already HAS an owner. Offering to keep
    /// that one promised what <see cref="AdoptOrCreateProfileAsync"/> then silently refused, and
    /// the user arrived at an empty Lebenslauf having been told the opposite a tap earlier.
    ///
    /// A profile that is gone and a profile that is spoken for get the same answer. Neither is
    /// this phone's to give away, and telling the two apart would say whether an id somebody typed
    /// names an account — the thing the refusal in <see cref="SignIn"/> exists to avoid saying.
    /// </summary>
    [HttpGet("adoptable/{profileId:guid}")]
    public async Task<IActionResult> Adoptable(Guid profileId) =>
        Ok(new AdoptableDto(await ProfileAdoption.IsAdoptableAsync(db, profileId)));

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
        if (adopt is { } id && await ProfileAdoption.IsAdoptableAsync(db, id)) return id;

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

    internal const string ResetCodeRejected =
        "Der Code stimmt nicht oder gilt nicht mehr. Fordern Sie einen neuen an.";
    internal const string ResetCodeRejectedKind = "reset_code_rejected";
}
