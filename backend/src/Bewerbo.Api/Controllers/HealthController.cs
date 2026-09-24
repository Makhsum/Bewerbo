using Bewerbo.Api.Llm;
using Bewerbo.Api.Mail;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

[Route("api/health")]
public class HealthController(ILanguageModel model, IMailSender mail) : BewerboController
{
    [HttpGet("")]
    public IActionResult Get() => Ok(new
    {
        status = "ok",
        // Which writer will run, said out loud: a letter written by the rule-based writer and one
        // written by the model are both real output, but nobody should have to guess which they got.
        writer = model.IsConfigured ? "model" : "regeln",
        // And where a password reset goes, for the same reason. "datei" means no mail server is
        // configured and the mail is being written to a file next to the binary — a working reset
        // on a dev machine, and a reset nobody receives on a real one.
        mail = mail.Kind,
    });
}
