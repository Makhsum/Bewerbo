using Bewerbo.Api.Data;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace Bewerbo.Api.Controllers;

[Route("api/overview")]
public class OverviewController(BewerboDbContext db) : BewerboController
{
    [HttpGet("{profileId:guid}")]
    public async Task<IActionResult> Get(Guid profileId)
    {
        var profile = await db.FullProfileAsync(profileId);
        if (profile is null) return NotFoundProblem(ProfileController.ProfileMissing);

        var timeline = TimelineService.Build(profile, DateOnly.FromDateTime(DateTime.Today));
        var postings = db.Postings.Where(p => p.ProfileId == profileId).ToList();
        return Ok(ReadinessService.Build(profile, timeline, postings));
    }
}
