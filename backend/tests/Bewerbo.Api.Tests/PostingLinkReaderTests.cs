using Bewerbo.Api.Services;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// The page behind a link, turned into the text the parser reads.
///
/// The assertions are about what a job portal actually serves — a consent script, a layout table,
/// a requirement list in &lt;li&gt; elements — because the failure this guards against is not a
/// crash. It is an advert that arrives as one run-on line with a cookie banner's JavaScript in the
/// middle of it, out of which <see cref="PostingParser"/> then reads nothing.
/// </summary>
public class PostingLinkReaderTests
{
    [Fact]
    public void A_script_leaves_no_javascript_behind_in_the_text()
    {
        var text = PostingLinkReader.ToText(
            "<p>Wir suchen eine Pflegefachkraft.</p>" +
            "<script>var consent = {a:1}; if (a < b) { track('x'); }</script>" +
            "<p>Bewerbung an Frau Weber.</p>");

        Assert.DoesNotContain("consent", text);
        Assert.DoesNotContain("track", text);
        Assert.Contains("Pflegefachkraft", text);
        Assert.Contains("Frau Weber", text);
    }

    [Fact]
    public void A_requirement_list_arrives_one_item_per_line()
    {
        // The parser reads bullet lists off LINES. A list that came back as one line would be read
        // as a single requirement with the dashes still in it.
        var text = PostingLinkReader.ToText(
            "<ul><li>Examinierte Pflegefachkraft</li><li>Deutsch B2</li><li>Schichtdienst</li></ul>");

        Assert.Equal(["Examinierte Pflegefachkraft", "Deutsch B2", "Schichtdienst"], text.Split('\n'));
    }

    [Fact]
    public void A_br_ends_a_line_and_entities_come_back_as_their_characters()
    {
        var text = PostingLinkReader.ToText("<div>Gr&ouml;&szlig;e &amp; Form<br>Eintritt: 01.03.2026</div>");

        Assert.Equal("Größe & Form\nEintritt: 01.03.2026", text);
    }

    [Fact]
    public void A_nonbreaking_space_becomes_an_ordinary_one()
    {
        // A layout table fills its cells with &nbsp;, and the parser's patterns match on \s runs
        // that a U+00A0 is not part of by every reading a reader expects.
        Assert.Equal("Frau Dr. Weber", PostingLinkReader.ToText("<td>Frau&nbsp;Dr.&nbsp;Weber</td>"));
    }

    [Fact]
    public void A_plain_text_body_is_handed_over_unchanged()
    {
        const string advert = "Wir suchen eine Pflegefachkraft (m/w/d).\n\nEintritt: ab sofort.";

        Assert.Equal(advert, PostingLinkReader.ToText(advert));
    }

    [Fact]
    public void A_page_with_nothing_on_it_is_not_usable()
    {
        Assert.False(PostingLinkReader.IsUsable(PostingLinkReader.ToText("<html><body></body></html>")));
    }

    [Theory]
    [InlineData("https://example.com/stelle/123", "https://example.com/stelle/123")]
    // Copied out of a message rather than out of a browser: no scheme, and https is the one to add.
    [InlineData("example.com/stelle/123", "https://example.com/stelle/123")]
    public void An_address_is_read_as_http(string entered, string expected)
    {
        Assert.Equal(expected, PostingLinkReader.ReadAddress(entered)?.ToString());
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    // The server fetches what this returns, so nothing but http(s) may come out of it.
    [InlineData("file:///C:/Windows/win.ini")]
    [InlineData("ftp://example.com/advert.txt")]
    [InlineData("nicht einmal eine Adresse")]
    public void What_is_not_a_web_address_is_refused(string entered)
    {
        Assert.Null(PostingLinkReader.ReadAddress(entered));
    }

    [Theory]
    [InlineData("127.0.0.1")]
    [InlineData("10.1.2.3")]
    [InlineData("172.16.0.1")]
    [InlineData("172.31.255.254")]
    [InlineData("192.168.1.10")]
    // The address an EC2/Azure instance keeps its credentials behind — the reason this guard is
    // here at all rather than only in principle.
    [InlineData("169.254.169.254")]
    [InlineData("0.0.0.0")]
    [InlineData("::1")]
    [InlineData("fe80::1")]
    [InlineData("fd00::1")]
    // The mapped form of a loopback address, which is the way round the plain check.
    [InlineData("::ffff:127.0.0.1")]
    public void The_server_does_not_fetch_its_own_network(string address)
    {
        Assert.False(PostingLinkReader.IsPublicAddress(System.Net.IPAddress.Parse(address)));
    }

    [Theory]
    [InlineData("93.184.216.34")]
    [InlineData("172.32.0.1")]
    [InlineData("8.8.8.8")]
    [InlineData("2606:2800:220:1:248:1893:25c8:1946")]
    public void A_public_address_is_fetched(string address)
    {
        Assert.True(PostingLinkReader.IsPublicAddress(System.Net.IPAddress.Parse(address)));
    }
}
