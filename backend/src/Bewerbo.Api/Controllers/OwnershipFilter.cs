using System.Reflection;
using Bewerbo.Api.Data;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Abstractions;
using Microsoft.AspNetCore.Mvc.Controllers;
using Microsoft.AspNetCore.Mvc.Filters;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// What the <c>{id}</c> of this controller's routes names, so that
/// <see cref="OwnershipFilter"/> can ask who owns it.
///
/// On the CONTROLLER and not on each action, because every controller here addresses one kind of
/// record: <c>{id}</c> is a Bewerbung in <see cref="ApplicationsController"/> and a Dokument in
/// <see cref="DocumentsController"/>, on every route either of them has. An action that names a
/// profile calls its parameter <c>profileId</c> and needs no declaration at all — see the filter.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public sealed class IdNamesAttribute(OwnedResource resource) : Attribute
{
    public OwnedResource Resource { get; } = resource;
}

/// <summary>
/// The second half of what a sign-in has to mean on the server. <c>[Authorize]</c> on
/// <see cref="BewerboController"/> says that a caller is signed in; this says that what they named
/// is theirs.
///
/// Without it the door would still rest on the client: a signed-in user could read and change any
/// profile, Stellenanzeige, Bewerbung or Zeugnis in the installation by putting somebody else's id
/// in the address, and the only thing standing in the way would be that the app does not offer to.
///
/// It runs for EVERY action rather than being applied per route, and reads the ids out of the
/// bound arguments by name — <c>profileId</c> is always a profile, <c>id</c> is whatever the
/// controller's <see cref="IdNamesAttribute"/> declares. That is what makes a route added later
/// covered by default instead of by somebody remembering; the alternative, a line in each of the
/// thirty-odd actions, is a rule that holds until the thirty-first.
///
/// An id that belongs to somebody else is answered exactly as an id that does not exist: the 404
/// the route would have written anyway, with the same kind, so the app already knows what to say
/// about it. Telling the two apart would turn this API into a way of finding out which guessed
/// ids name real people's Bewerbungen.
///
/// The two requests that name a record in their BODY — creating a Bewerbung, storing a
/// Stellenanzeige — are checked in their own actions instead, where the record has just been
/// loaded. There is no third.
/// </summary>
public sealed class OwnershipFilter(BewerboDbContext db) : IAsyncActionFilter
{
    public async Task OnActionExecutionAsync(ActionExecutingContext context, ActionExecutionDelegate next)
    {
        // A route that is open to anyone is skipped even when the caller DID send a valid token:
        // GET /api/auth/adoptable asks about a profile that by definition belongs to nobody, and
        // an ownership check there would refuse the one question it exists to answer.
        var signedIn = context.HttpContext.User.SignedInProfileId();
        if (signedIn is null || context.ActionDescriptor.EndpointMetadata.OfType<IAllowAnonymous>().Any())
        {
            await next();
            return;
        }

        foreach (var (name, resource) in Named(context.ActionDescriptor))
        {
            if (!context.ActionArguments.TryGetValue(name, out var value) || value is not Guid id) continue;
            if (await ResourceOwnership.OwnerOfAsync(db, resource, id) == signedIn) continue;

            context.Result = Refuse(context, resource);
            return;
        }

        await next();
    }

    /// <summary>
    /// The arguments of this action that name a record, and what each of them names.
    /// </summary>
    private static IEnumerable<(string Name, OwnedResource Resource)> Named(ActionDescriptor action)
    {
        yield return ("profileId", OwnedResource.Profile);

        if ((action as ControllerActionDescriptor)?.ControllerTypeInfo
                .GetCustomAttribute<IdNamesAttribute>() is { } declared)
        {
            yield return ("id", declared.Resource);
        }
    }

    /// <summary>
    /// The refusal, written through the controller's own <see cref="BewerboController.NotFoundProblem"/>
    /// so that it is the same answer in the same shape the route itself would have given for an id
    /// that is not there.
    /// </summary>
    private static ObjectResult Refuse(ActionExecutingContext context, OwnedResource resource)
    {
        var controller = (BewerboController)context.Controller;
        return resource switch
        {
            OwnedResource.Profile => controller.NotFoundProblem(
                ProfileController.ProfileMissing, ProfileController.ProfileMissingKind),
            OwnedResource.Posting => controller.NotFoundProblem(
                PostingsController.PostingMissing, PostingsController.PostingMissingKind),
            OwnedResource.Application => controller.NotFoundProblem(
                ApplicationsController.ApplicationMissing, ApplicationsController.ApplicationMissingKind),
            OwnedResource.Document => controller.NotFoundProblem(
                DocumentsController.DocumentMissing, DocumentsController.DocumentMissingKind),
            _ => controller.NotFoundProblem(
                ProfileController.ProfileMissing, ProfileController.ProfileMissingKind),
        };
    }
}
