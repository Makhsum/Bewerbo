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
/// Authorisation belongs here too when it arrives: an [Authorize] on this class reaches every route
/// in the API, and an [AllowAnonymous] on the few that stay open is then the readable exception.
/// </summary>
[ApiController]
public abstract class BewerboController : ControllerBase
{
    /// <summary>
    /// The record the route names does not exist. The title is left to the framework on purpose, so
    /// that a 404 the API writes and a 404 the router writes for an address that matches nothing
    /// read alike; what this route knows goes into the detail.
    ///
    /// <paramref name="kind"/> goes out beside the detail as an extension member, for the reason
    /// every user-facing sentence of this API now carries one: the server never learns the
    /// interface language, so the detail is German on every screen. The client writes the sentence
    /// from the kind and keeps the German as its fallback. See <see cref="Contracts.NextStepDto"/>.
    /// </summary>
    protected ObjectResult NotFoundProblem(string detail, string kind)
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
}
