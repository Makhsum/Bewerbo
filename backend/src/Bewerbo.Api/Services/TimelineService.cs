using Bewerbo.Api.Domain;

namespace Bewerbo.Api.Services;

public record TimelinePeriod(string Kind, string Label, DateOnly From, DateOnly To, bool Ongoing);

public record TimelineGap(DateOnly From, DateOnly To, int Months, string? Reason, string? GermanWording)
{
    public bool Explained => !string.IsNullOrWhiteSpace(Reason);
}

public record TimelineView(
    int FirstYear,
    int LastYear,
    IReadOnlyList<TimelinePeriod> Periods,
    IReadOnlyList<TimelineGap> Gaps);

/// <summary>
/// Draws the profile as two lanes and finds what is missing between them. A German reader treats an
/// unexplained gap as an alarm, so the product's job is to find it before the recruiter does.
/// </summary>
public static class TimelineService
{
    /// <summary>
    /// Shorter than this and nobody asks. Two months is the threshold a German HR reader starts
    /// noticing at, which is why the mockup's example gap is 08/2023 – 09/2023.
    /// </summary>
    public const int MinimumGapMonths = 2;

    public static TimelineView Build(Profile profile, DateOnly today)
    {
        var periods = new List<TimelinePeriod>();

        foreach (var e in profile.Education.OrderBy(x => x.From))
        {
            periods.Add(new TimelinePeriod("ausbildung", $"{e.Institution} — {e.Degree}",
                e.From, e.To ?? today, e.To is null));
        }
        foreach (var x in profile.Experience.OrderBy(x => x.From))
        {
            periods.Add(new TimelinePeriod("beruf", $"{x.Employer}",
                x.From, x.To ?? today, x.To is null));
        }

        var gaps = FindGaps(periods, today, profile.Gaps);

        var firstYear = periods.Count == 0 ? today.Year : periods.Min(p => p.From.Year);
        var lastYear = periods.Count == 0 ? today.Year : periods.Max(p => p.To.Year);
        return new TimelineView(firstYear, lastYear, periods, gaps);
    }

    /// <summary>
    /// Merges every period into covered intervals and reports what falls between them. Overlapping
    /// entries — a job held while still studying — must not produce a phantom gap, which is why the
    /// intervals are merged first rather than compared pairwise.
    /// </summary>
    public static IReadOnlyList<TimelineGap> FindGaps(
        IEnumerable<TimelinePeriod> periods, DateOnly today, IEnumerable<GapExplanation> known)
    {
        var ordered = periods.OrderBy(p => p.From).ToList();
        if (ordered.Count == 0) return [];

        var gaps = new List<TimelineGap>();
        var coveredTo = ordered[0].To;

        foreach (var period in ordered.Skip(1))
        {
            if (period.From > coveredTo)
            {
                var from = coveredTo;
                var to = period.From;
                var months = MonthsBetween(from, to);
                if (months >= MinimumGapMonths)
                {
                    var match = known.FirstOrDefault(g => Overlaps(g, from, to));
                    gaps.Add(new TimelineGap(from, to, months, match?.Reason, match?.GermanWording));
                }
            }
            if (period.To > coveredTo) coveredTo = period.To;
        }

        // The gap that runs from the last entry up to today. It is the one a German recruiter sees
        // first — a CV that simply stops three years ago is the loudest question on the page — and
        // walking only the spaces BETWEEN entries never reaches it.
        if (today > coveredTo)
        {
            var months = MonthsBetween(coveredTo, today);
            if (months >= MinimumGapMonths)
            {
                var match = known.FirstOrDefault(g => Overlaps(g, coveredTo, today));
                gaps.Add(new TimelineGap(coveredTo, today, months, match?.Reason, match?.GermanWording));
            }
        }

        return gaps;
    }

    public static int MonthsBetween(DateOnly from, DateOnly to) =>
        Math.Max(0, (to.Year - from.Year) * 12 + to.Month - from.Month);

    private static bool Overlaps(GapExplanation g, DateOnly from, DateOnly to) =>
        g.From <= to && g.To >= from;
}
