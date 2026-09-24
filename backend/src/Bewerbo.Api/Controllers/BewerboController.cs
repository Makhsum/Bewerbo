using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// What every controller of this API has in common: the attribute routing [ApiController] asks for,
/// and the two error answers that are written by hand rather than produced by the framework.
///
/// Both go out as ProblemDetails, and that is the whole point of them being here. The framework
/// already answers in that shape by itself — a body that is not JSON, a route value that is not a
/// Guid, an exception nobody caught — so a hand-written 404 that returned an empty body, or a 400
/// that returned a bare JSON string, was a second and a third shape for a client to know about.
/// There is one now.
///
/// Authorisation lives here as well, and it is the [Authorize] this file always said it would be:
/// one attribute that reaches every route in the API, with an [AllowAnonymous] on the few that stay
/// open — the door itself, the health check, the legal facts — as the readable exception. A
/// controller written later is covered by deriving from this class rather than by being remembered.
/// Who the caller is comes from <see cref="Services.SessionAuthenticationHandler"/>; whether what
/// they named is theirs comes from <see cref="OwnershipFilter"/>.
/// </summary>
[ApiController]
[Authorize]
public abstract class BewerboController : ControllerBase
{
    /// <summary>
    /// The profile of the account whose token this request carried — the one id this caller is
    /// entitled to name.
    ///
    /// <see cref="Guid.Empty"/> where there is no session, which is a value no record has: a
    /// comparison against it fails, so a route reading this without one refuses rather than
    /// matching something by accident. Every route but the open ones is behind the [Authorize]
    /// above and always has a session.
    /// </summary>
    protected Guid SignedInProfileId => User.SignedInProfileId() ?? Guid.Empty;

    /// <summary>
    /// The record the route names does not exist. The title is left to the framework on purpose, so
    /// that a 404 the API writes and a 404 the router writes for an address that matches nothing
    /// read alike; what this route knows goes into the detail.
    ///
    /// <paramref name="kind"/> goes out beside the detail as an extension member, for the reason
    /// every user-facing sentence of this API now carries one: the server never learns the
    /// interface language, so the detail is German on every screen. The client writes the sentence
    /// from the kind and keeps the German as its fallback. See <see cref="Contracts.NextStepDto"/>.
    ///
    /// Reachable from <see cref="OwnershipFilter"/> as well as from the routes, so that an id
    /// belonging to somebody else is refused in exactly the shape an id that is not there is.
    /// </summary>
    protected internal ObjectResult NotFoundProblem(string detail, string kind)
    {
        var problem = Problem(statusCode: StatusCodes.Status404NotFound, detail: detail);
        if (problem.Value is ProblemDetails details)
        {
            details.Extensions["kind"] = kind;
        }

        return problem;
    }

    /// <summary>
    /// The request parsed but the API will not act on it. Reported under the same "errors" member
    /// the framework fills for a binding failure, keyed by the field at fault, so a client reads
    /// both the same way.
    /// </summary>
    protected ActionResult InvalidRequest(string field, string detail)
    {
        ModelState.AddModelError(field, detail);
        return ValidationProblem();
    }

    /// <summary>
    /// The caller is not signed in: no token, or one that has been revoked or never existed.
    ///
    /// A 401 and not a 404, because the difference matters to the client — a session that has ended
    /// puts the door back up, while a record that is missing does not. Carries a
    /// <paramref name="kind"/> for the reason the other two do.
    /// </summary>
    protected ObjectResult NotSignedInProblem(string detail, string kind)
    {
        var problem = Problem(statusCode: StatusCodes.Status401Unauthorized, detail: detail);
        if (problem.Value is ProblemDetails details)
        {
            details.Extensions["kind"] = kind;
        }

        return problem;
    }

    /// <summary>
    /// The request was understood and could not be carried out — the page behind a link did not
    /// answer, what came back was not an advert. A 400 like <see cref="InvalidRequest"/>, but
    /// carrying a <paramref name="kind"/> the way <see cref="NotFoundProblem"/> does, because the
    /// client has to be able to say which of them happened in the user's own language; the "errors"
    /// shape has nowhere to put that.
    /// </summary>
    protected ObjectResult RefusedProblem(string detail, string kind)
    {
        var problem = Problem(statusCode: StatusCodes.Status400BadRequest, detail: detail);
        if (problem.Value is ProblemDetails details)
        {
            details.Extensions["kind"] = kind;
        }

        return problem;
    }
}
