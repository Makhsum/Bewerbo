using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Llm;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// The conversation Bewerbo's first minutes are.
///
/// One route, and it keeps nothing: the client sends the conversation it has on screen and gets one
/// answer back. There is no <see cref="IdNamesAttribute"/> and no profile id in the body, because
/// there is no record here to name — the profile the turn is about is the caller's own, which is
/// what <see cref="BewerboController.SignedInProfileId"/> answers.
///
/// Where no model is configured this route refuses rather than degrading. The app already knows
/// from <c>GET /api/health</c> which writer is running and draws the form instead, so a refusal
/// here is for the case that changed under the app's feet — a key removed while it was open.
/// </summary>
[Route("api/assistant")]
public class AssistantController(BewerboDbContext db, ILanguageModel model) : BewerboController
{
    [HttpPost("turn")]
    public async Task<IActionResult> Turn([FromBody] AssistantTurnRequest request,
        [FromServices] IAssistantConversation conversation, CancellationToken ct)
    {
        // Before anything is read: an installation without a key has no assistant at all, and
        // saying so is the whole of the answer. See AssistantConversation for why there is no
        // rule-based one behind it.
        if (!model.IsConfigured) return RefusedProblem(AssistantUnavailable, AssistantUnavailableKind);

        var said = (request.Messages ?? [])
            .Where(m => !string.IsNullOrWhiteSpace(m.Text))
            .Select(m => new AssistantMessage(m.FromUser, m.Text.Trim()))
            .ToList();
        if (said.Count == 0) return InvalidRequest("messages", "Kein Text.");

        var profile = await db.FullProfileAsync(SignedInProfileId);
        if (profile is null) return NotFoundProblem(ProfileController.ProfileMissing, ProfileController.ProfileMissingKind);

        var reply = await conversation.TurnAsync(profile, said, request.UiLanguage ?? "en", ct);

        // A model that answered with nothing is the same fact to the user as one that is not there:
        // the assistant did not answer. Same kind, so the screen has one thing to say about it.
        return reply is null
            ? RefusedProblem(AssistantUnavailable, AssistantUnavailableKind)
            : Ok(new AssistantReplyDto(reply.Reply, reply.Missing,
                reply.Proposals
                    .Select(p => new AssistantProposalDto(
                        p.Kind, p.Source, p.Title, p.Detail, p.From, p.To))
                    .ToList()));
    }

    internal const string AssistantUnavailable =
        "Für diese Installation ist kein Sprachmodell konfiguriert.";

    internal const string AssistantUnavailableKind = "assistant_unavailable";
}
