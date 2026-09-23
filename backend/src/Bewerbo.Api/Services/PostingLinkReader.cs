using System.Net;
using System.Net.Sockets;
using System.Text.RegularExpressions;

namespace Bewerbo.Api.Services;

/// <summary>
/// Turns the page behind a link into the plain advert text the rest of the posting path already
/// knows how to read.
///
/// A posting reached by link is the same posting as one pasted by hand — <see cref="PostingParser"/>
/// reads it, the client shows it, the user corrects it. So this class produces TEXT and nothing
/// else: it does not extract fields, it does not guess which part of the page is the advert, and it
/// stores nothing. Everything downstream stays unchanged.
///
/// The stripping is done by hand, for the same reason the parser is: a job portal serves markup
/// nobody can predict, and the small number of things that actually matter — the blocks that are
/// not prose, the tags that end a line, the entities — are matchable. A DOM library would be a
/// dependency for the same result.
/// </summary>
public static partial class PostingLinkReader
{
    /// <summary>
    /// How short an answer may be and still be an advert. A page that yields less than this is a
    /// cookie wall, a redirect notice or a login form — not a posting, and telling the user so is
    /// more use than handing the parser forty characters to find nothing in.
    /// </summary>
    public const int MinimumUsefulLength = 120;

    /// <summary>
    /// The readable text of an HTML page, or of a plain-text body handed over unchanged.
    /// </summary>
    public static string ToText(string body)
    {
        // Nothing that looks like markup: a portal that answers text/plain has already done this
        // work, and running the stripper over it would only eat a "<" somebody wrote on purpose.
        if (!MarkupRx().IsMatch(body)) return Tidy(body);

        var text = CommentRx().Replace(body, " ");

        // The blocks that are never the advert. They have to go WITH their content — a <script>
        // whose tags were merely stripped leaves its JavaScript standing in the text, and that is
        // the single biggest source of rubbish in a scraped page.
        text = NonProseBlockRx().Replace(text, "\n");

        // A tag that ends a line has to become one. Without this the whole advert arrives as a
        // single paragraph, and the parser reads its requirement lists off LINES.
        text = LineBreakingTagRx().Replace(text, "\n");

        text = TagRx().Replace(text, "");
        text = WebUtility.HtmlDecode(text);

        return Tidy(text);
    }

    /// <summary>
    /// Whether what came back is worth handing to the parser. Separate from <see cref="ToText"/>
    /// so that the caller decides what to say about it: this class has no opinion on HTTP.
    /// </summary>
    public static bool IsUsable(string text) => text.Length >= MinimumUsefulLength;

    /// <summary>
    /// The address, as an absolute http(s) URI, or null when it is not one.
    ///
    /// Only those two schemes: the server fetches whatever this returns, and a "link" naming
    /// file:// or a host on the machine's own network is not a job advert. A bare "example.com/x"
    /// is accepted and read as https, because that is how an address arrives when it was copied
    /// out of a message rather than out of a browser.
    /// </summary>
    public static Uri? ReadAddress(string? url)
    {
        var trimmed = url?.Trim();
        if (string.IsNullOrEmpty(trimmed)) return null;

        // An address has no spaces in it. Checked before anything else because Uri.TryCreate is
        // lenient enough to turn a sentence into a host plus an escaped path, and a prose answer
        // would then be fetched instead of reported as the mistake it is.
        if (trimmed.Any(char.IsWhiteSpace)) return null;

        if (!trimmed.Contains("://", StringComparison.Ordinal)) trimmed = "https://" + trimmed;

        return Uri.TryCreate(trimmed, UriKind.Absolute, out var uri)
            && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps)
            && !string.IsNullOrEmpty(uri.Host)
            ? uri
            : null;
    }

    /// <summary>
    /// Whether an address the host name resolved to is one this server may fetch.
    ///
    /// The address comes from the user and the fetch is made by the SERVER, which sits wherever it
    /// is deployed — so "http://10.0.0.5/admin" or a name that resolves to 127.0.0.1 would have
    /// Bewerbo read a machine on its own network and hand the answer back as an advert. A job
    /// posting is on the public internet; nothing is lost by saying so.
    ///
    /// Kept here, and taking an already-resolved address, so that it can be tested: the name
    /// lookup itself is the caller's, because this class does no I/O.
    /// </summary>
    public static bool IsPublicAddress(IPAddress address)
    {
        if (IPAddress.IsLoopback(address)) return false;

        if (address.IsIPv4MappedToIPv6) address = address.MapToIPv4();

        if (address.AddressFamily == AddressFamily.InterNetwork)
        {
            var octets = address.GetAddressBytes();
            return octets[0] switch
            {
                10 => false,                                      // 10.0.0.0/8
                127 => false,                                     // loopback, for a mapped address
                169 when octets[1] == 254 => false,               // 169.254.0.0/16, link-local
                172 when octets[1] is >= 16 and <= 31 => false,   // 172.16.0.0/12
                192 when octets[1] == 168 => false,               // 192.168.0.0/16
                0 => false,                                       // 0.0.0.0/8
                _ => true,
            };
        }

        return !address.IsIPv6LinkLocal && !address.IsIPv6SiteLocal
            && !address.IsIPv6UniqueLocal && !address.Equals(IPAddress.IPv6Any);
    }

    /// <summary>
    /// Whitespace as a reader expects it: no trailing spaces, no runs of blank lines, and the
    /// non-breaking space that fills a layout table turned into an ordinary one so the parser's
    /// patterns match across it.
    /// </summary>
    private static string Tidy(string text)
    {
        var lines = text
            .Replace(' ', ' ')
            .Replace("\r\n", "\n")
            .Split('\n')
            .Select(line => SpacesRx().Replace(line, " ").Trim());

        return BlankRunRx().Replace(string.Join("\n", lines), "\n\n").Trim();
    }

    [GeneratedRegex(@"<[a-z!/]", RegexOptions.IgnoreCase)]
    private static partial Regex MarkupRx();

    [GeneratedRegex(@"<!--.*?-->", RegexOptions.Singleline)]
    private static partial Regex CommentRx();

    // Content and all. <head> carries the title and the meta tags, none of which is the advert;
    // <svg> carries path data that survives tag-stripping as a wall of coordinates.
    [GeneratedRegex(@"<(script|style|noscript|svg|head|iframe|template)\b[^>]*>.*?</\1\s*>",
        RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex NonProseBlockRx();

    // <br> and the END of every block element. The opening tag is deliberately not here: a line
    // break before AND after each block would double every blank line for nothing.
    [GeneratedRegex(@"<br\s*/?>|</(p|div|li|ul|ol|tr|td|th|h[1-6]|section|article|header|footer|nav|main|blockquote)\s*>",
        RegexOptions.IgnoreCase)]
    private static partial Regex LineBreakingTagRx();

    [GeneratedRegex(@"<[^>]+>")]
    private static partial Regex TagRx();

    [GeneratedRegex(@"[ \t\f\v]+")]
    private static partial Regex SpacesRx();

    [GeneratedRegex(@"\n{3,}")]
    private static partial Regex BlankRunRx();
}
