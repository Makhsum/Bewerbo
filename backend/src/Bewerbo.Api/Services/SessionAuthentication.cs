using System.Security.Claims;
using System.Text.Encodings.Web;
using Bewerbo.Api.Controllers;
using Bewerbo.Api.Data;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace Bewerbo.Api.Services;

/// <summary>
/// Who is asking, read off the Authorization header on every request.
///
/// The door hands a device a token and the app keeps it; until this existed nothing on the way in
/// looked at it again. Every route of the API was addressed by a profile id alone, so anybody who
/// learned an id could read and change that profile, its Stellenanzeigen, its Bewerbungen and the
/// scans of its Zeugnisse from outside the app — the sign-in was a screen the client drew and not
/// a rule the server kept.
///
/// An authentication SCHEME and not a check written into the controllers, for the reason
/// <see cref="BewerboController"/> has always said it would be: <c>[Authorize]</c> on that class
/// reaches every route in the API at once, and an <c>[AllowAnonymous]</c> on the handful that stay
/// open is then the readable exception. A route added later is covered by inheriting the base
/// class rather than by somebody remembering.
///
/// The token is not a JWT and nothing here is cached: the session row is read on every request, so
/// signing out on one device and erasing an account genuinely end what they say they end. See
/// <see cref="SessionToken"/> for why the row holds a hash rather than the token.
/// </summary>
public static class SessionAuthentication
{
    /// <summary>The scheme name, which is also the word the header carries in front of the token.</summary>
    public const string Scheme = "Bearer";

    /// <summary>The account the token belongs to.</summary>
    public const string AccountIdClaim = "bewerbo:account";

    /// <summary>
    /// The one profile that account owns. Carried as a claim rather than looked up again per route,
    /// because it is what every ownership question in this API comes down to: the rest of the
    /// server is addressed by the profile id, and this is the id the caller is entitled to name.
    /// </summary>
    public const string ProfileIdClaim = "bewerbo:profile";

    /// <summary>
    /// The profile of the signed-in caller, or null when the request carried no valid session.
    /// Null rather than <see cref="Guid.Empty"/> here so a caller has to say what it does about
    /// the absent case; <see cref="BewerboController.SignedInProfileId"/> is the other reading.
    /// </summary>
    public static Guid? SignedInProfileId(this ClaimsPrincipal user) =>
        Guid.TryParse(user.FindFirst(ProfileIdClaim)?.Value, out var id) ? id : null;
}

/// <summary>
/// The scheme itself: an Authorization header, a session row, and the two claims that come out of
/// it.
///
/// A header that names no token and a token no session row matches are the same answer — no
/// result — and therefore the same 401. Telling them apart would say whether a token somebody
/// offered was ever a real one, which is the thing <see cref="AuthController.SignIn"/> refuses to
/// say about an e-mail address.
/// </summary>
public sealed class SessionAuthenticationHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder,
    BewerboDbContext db,
    IProblemDetailsService problems)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var token = SessionToken.FromHeader(Request.Headers.Authorization);
        if (token is null) return AuthenticateResult.NoResult();

        var hash = SessionToken.HashOf(token);
        var session = await db.AuthTokens
            .Where(t => t.TokenHash == hash)
            .Select(t => new { t.AccountId, t.Account!.ProfileId })
            .FirstOrDefaultAsync();
        if (session is null) return AuthenticateResult.NoResult();

        var identity = new ClaimsIdentity(
        [
            new Claim(SessionAuthentication.AccountIdClaim, session.AccountId.ToString()),
            new Claim(SessionAuthentication.ProfileIdClaim, session.ProfileId.ToString()),
        ], Scheme.Name);

        return AuthenticateResult.Success(
            new AuthenticationTicket(new ClaimsPrincipal(identity), Scheme.Name));
    }

    /// <summary>
    /// The 401, written by hand for the one reason every refusal in this API is: the server never
    /// learns the interface language, so the answer has to carry a <c>kind</c> the screen writes
    /// its own sentence from. The framework's own challenge is an empty body.
    ///
    /// The sentence is <see cref="AuthController.SessionInvalid"/> and not a second one saying the
    /// same thing: a session that has ended reads the same whether the app noticed it at launch or
    /// on the next call.
    /// </summary>
    protected override async Task HandleChallengeAsync(AuthenticationProperties properties)
    {
        Response.StatusCode = StatusCodes.Status401Unauthorized;
        await problems.WriteAsync(new ProblemDetailsContext
        {
            HttpContext = Context,
            ProblemDetails =
            {
                Status = StatusCodes.Status401Unauthorized,
                Detail = AuthController.SessionInvalid,
                Extensions = { ["kind"] = AuthController.SessionInvalidKind },
            },
        });
    }
}
