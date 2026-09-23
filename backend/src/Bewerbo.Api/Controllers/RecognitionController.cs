using Bewerbo.Api.Recognition;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

[Route("api/recognition")]
public class RecognitionController : BewerboController
{
    [HttpGet("degrees")]
    public IActionResult Degrees([FromQuery] string? country, [FromQuery] string? q) =>
        Ok(AnabinCatalog.Search(country, q));
}
