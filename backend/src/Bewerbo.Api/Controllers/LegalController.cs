using Bewerbo.Api.Contracts;
using Bewerbo.Api.Legal;
using Bewerbo.Api.Llm;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// The two facts the legal pages cannot be written without, and that the app cannot know by itself:
/// who operates this installation, and whether what the user types is handed to a model outside it.
///
/// The PROSE of the terms, the privacy notice and the Impressum is interface text and lives in the
/// app's resources, in every language it is offered in. Only what varies by deployment comes from
/// here — see <see cref="LegalOptions"/> for why an address must not be compiled into the APK.
///
/// Open to anyone: the Impressum, the AGB and the privacy notice are reachable from the door, and
/// a privacy notice a user has to sign in to read is not one.
/// </summary>
[AllowAnonymous]
[Route("api/legal")]
public class LegalController(LegalOptions legal, LlmOptions model) : BewerboController
{
    [HttpGet("")]
    public IActionResult Get() => Ok(new LegalDto(
        new LegalOperatorDto(
            legal.IsStated, legal.OperatorName, legal.Street, legal.PostalCode, legal.City,
            legal.Country, legal.Email, legal.Represented, legal.Register),
        // The host that is actually called, not a name somebody typed: a privacy notice naming a
        // processor the installation does not use, or omitting the one it does, is the one mistake
        // on these pages that has consequences. Empty means the rule-based writer runs and the
        // text never leaves this server — which is what GET /api/health reports as writer=regeln.
        ModelProcessor: string.IsNullOrWhiteSpace(model.ApiKey)
            ? ""
            : new Uri(model.BaseUrl).Host));
}
