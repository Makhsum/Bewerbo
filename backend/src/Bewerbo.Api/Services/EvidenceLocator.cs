namespace Bewerbo.Api.Services;

/// <summary>Where a quote was found in the posting, so the client can underline exactly that.</summary>
public record EvidenceSpan(int Start, int Length, string Quote);

/// <summary>
/// Turns a quoted substring into a position in the source text.
///
/// The extractor returns the words it read, not character offsets: offsets drift the moment the
/// text is re-encoded or whitespace is normalised, and a wrong highlight is worse than none. If the
/// quote cannot be found the field is shown without a highlight — never against the wrong words.
/// </summary>
public static class EvidenceLocator
{
    public static EvidenceSpan? Locate(string source, string? quote)
    {
        if (string.IsNullOrWhiteSpace(source) || string.IsNullOrWhiteSpace(quote)) return null;

        var trimmed = quote.Trim();

        var index = source.IndexOf(trimmed, StringComparison.Ordinal);
        if (index >= 0) return new EvidenceSpan(index, trimmed.Length, trimmed);

        index = source.IndexOf(trimmed, StringComparison.OrdinalIgnoreCase);
        if (index >= 0) return new EvidenceSpan(index, trimmed.Length, source.Substring(index, trimmed.Length));

        // A quote that crossed a line break in the posting arrives with a single space in it.
        // Matching whitespace-insensitively recovers those without loosening the match otherwise.
        return LocateIgnoringWhitespace(source, trimmed);
    }

    private static EvidenceSpan? LocateIgnoringWhitespace(string source, string quote)
    {
        var needle = quote.Where(c => !char.IsWhiteSpace(c)).ToArray();
        if (needle.Length == 0) return null;

        var map = new List<int>(source.Length);
        var compact = new List<char>(source.Length);
        for (var i = 0; i < source.Length; i++)
        {
            if (char.IsWhiteSpace(source[i])) continue;
            compact.Add(source[i]);
            map.Add(i);
        }

        var haystack = new string(compact.ToArray());
        var at = haystack.IndexOf(new string(needle), StringComparison.OrdinalIgnoreCase);
        if (at < 0) return null;

        var start = map[at];
        var end = map[at + needle.Length - 1];
        return new EvidenceSpan(start, end - start + 1, source.Substring(start, end - start + 1));
    }
}
