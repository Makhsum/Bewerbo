using Bewerbo.Api.Llm;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

[Route("api/health")]
public class HealthController(ILanguageModel model) : BewerboController
{
    [HttpGet("")]
    public IActionResult Get() => Ok(new
    {
        status = "ok",
        // Which writer will run, said out loud: a letter written by the rule-based writer and one
        // written by the model are both real output, but nobody should have to guess which they got.
        writer = model.IsConfigured ? "model" : "regeln",
    });
}
