using Bewerbo.Api.Services;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// The two things the door rests on. Neither is business logic, and that is exactly why they get a
/// test: a password check that accepts everything and a token hash that is not a hash both look
/// like a working sign-in from the outside, and the app would go on letting people in either way.
/// </summary>
public class PasswordHashTests
{
    [Fact]
    public void The_password_it_was_written_from_verifies()
    {
        var stored = PasswordHash.Create("Nürnberg-2026!");

        Assert.True(PasswordHash.Verify("Nürnberg-2026!", stored));
    }

    [Fact]
    public void Another_password_does_not()
    {
        var stored = PasswordHash.Create("Nürnberg-2026!");

        Assert.False(PasswordHash.Verify("nürnberg-2026!", stored));
        Assert.False(PasswordHash.Verify("", stored));
        Assert.False(PasswordHash.Verify("Nürnberg-2026", stored));
    }

    /// <summary>
    /// The salt is what keeps two users who chose the same password from being visibly the same
    /// user — and a record that is the same twice is the sign that there is no salt.
    /// </summary>
    [Fact]
    public void The_same_password_is_stored_differently_every_time()
    {
        Assert.NotEqual(PasswordHash.Create("gleiches Passwort"), PasswordHash.Create("gleiches Passwort"));
    }

    [Fact]
    public void The_password_itself_is_nowhere_in_what_is_stored()
    {
        Assert.DoesNotContain("Nürnberg-2026!", PasswordHash.Create("Nürnberg-2026!"));
    }

    /// <summary>
    /// A record this cannot read has to be a refusal rather than an exception: the alternative is a
    /// 500 in the middle of signing in, which says more about the account than the refusal does.
    /// </summary>
    [Theory]
    [InlineData("")]
    [InlineData("nicht einmal ein Datensatz")]
    [InlineData("600000.NICHTHEX.NICHTHEX")]
    [InlineData("viele.AA.BB")]
    public void A_record_that_cannot_be_read_is_a_no(string stored)
    {
        Assert.False(PasswordHash.Verify("irgendwas", stored));
    }
}

public class SessionTokenTests
{
    [Fact]
    public void Two_tokens_are_never_the_same()
    {
        var tokens = Enumerable.Range(0, 100).Select(_ => SessionToken.Issue()).ToList();

        Assert.Equal(tokens.Count, tokens.Distinct().Count());
    }

    /// <summary>The token rides in a header, so anything a header would mangle must not be in it.</summary>
    [Fact]
    public void A_token_survives_a_header_untouched()
    {
        var token = SessionToken.Issue();

        Assert.DoesNotContain(token, c => c is '+' or '/' or '=' or ' ');
        Assert.Equal(token, SessionToken.FromHeader($"Bearer {token}"));
    }

    [Fact]
    public void The_hash_is_the_same_for_the_same_token_and_is_not_the_token()
    {
        var token = SessionToken.Issue();

        Assert.Equal(SessionToken.HashOf(token), SessionToken.HashOf(token));
        Assert.NotEqual(SessionToken.HashOf(token), SessionToken.HashOf(SessionToken.Issue()));
        Assert.DoesNotContain(token, SessionToken.HashOf(token));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("Bearer")]
    [InlineData("Bearer ")]
    public void A_header_that_names_no_token_reads_as_none(string? header)
    {
        Assert.Null(SessionToken.FromHeader(header));
    }

    /// <summary>
    /// The scheme is expected and not insisted on: a 401 for a header that carried the right token
    /// without the word in front of it looks exactly like a wrong password from the outside.
    /// </summary>
    [Fact]
    public void A_bare_token_is_read_as_the_token()
    {
        Assert.Equal("abc", SessionToken.FromHeader("abc"));
        Assert.Equal("abc", SessionToken.FromHeader("bearer abc"));
    }
}
