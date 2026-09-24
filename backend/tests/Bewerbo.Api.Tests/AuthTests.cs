using Bewerbo.Api.Domain;
using Bewerbo.Api.Mail;
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

/// <summary>
/// The third thing the door rests on, and the one whose failure is quietest: a reset code that
/// outlives its half hour, or that survives being guessed at all afternoon, is a working sign-in
/// screen with a back way in behind it.
/// </summary>
public class ResetCodeTests
{
    /// <summary>
    /// Six digits, leading zeros and all. Trimmed to five by an int somewhere, the code in the mail
    /// and the code the server checks stop being the same thing for one user in ten.
    /// </summary>
    [Fact]
    public void A_code_is_always_six_digits()
    {
        for (var i = 0; i < 500; i++)
        {
            var code = ResetCode.Issue();

            Assert.Equal(6, code.Length);
            Assert.All(code, c => Assert.True(char.IsAsciiDigit(c)));
        }
    }

    [Fact]
    public void Two_codes_are_not_the_same_code()
    {
        var codes = Enumerable.Range(0, 200).Select(_ => ResetCode.Issue()).ToList();

        Assert.True(codes.Distinct().Count() > codes.Count / 2);
    }

    [Fact]
    public void A_fresh_reset_is_live()
    {
        var now = DateTimeOffset.UtcNow;

        Assert.True(ResetCode.IsLive(Reset(now + ResetCode.Lifetime, attempts: 0), now));
    }

    /// <summary>The half hour is over. The row may still be there; the code is not.</summary>
    [Fact]
    public void A_reset_that_is_older_than_its_lifetime_is_not()
    {
        var now = DateTimeOffset.UtcNow;

        Assert.False(ResetCode.IsLive(Reset(now - TimeSpan.FromSeconds(1), attempts: 0), now));
    }

    /// <summary>
    /// A million codes and no limit is an afternoon's work. The last allowed attempt still counts,
    /// which is what makes the cap five rather than four or six.
    /// </summary>
    [Fact]
    public void A_reset_that_has_been_guessed_at_too_often_is_not()
    {
        var now = DateTimeOffset.UtcNow;
        var expires = now + ResetCode.Lifetime;

        Assert.True(ResetCode.IsLive(Reset(expires, ResetCode.MaxAttempts - 1), now));
        Assert.False(ResetCode.IsLive(Reset(expires, ResetCode.MaxAttempts), now));
    }

    private static PasswordReset Reset(DateTimeOffset expiresAt, int attempts) =>
        new() { ExpiresAt = expiresAt, Attempts = attempts };
}

/// <summary>
/// The one text this server writes that no screen can rewrite. What it has to carry is the code, in
/// a form a user can copy out of it — everything else about the mail is manners.
/// </summary>
public class PasswordResetMailTests
{
    /// <summary>The four the app itself is offered in. A fifth here would be a language nobody reads.</summary>
    [Theory]
    [InlineData("de")]
    [InlineData("en")]
    [InlineData("ru")]
    [InlineData("uk")]
    public void Every_language_the_app_offers_has_a_mail_with_the_code_in_it(string language)
    {
        var mail = PasswordResetMail.For("olena.k@example.com", language, "064391");

        Assert.Equal("olena.k@example.com", mail.To);
        Assert.NotEmpty(mail.Subject);
        Assert.Contains("064391", mail.Body);
        Assert.Contains("30", mail.Body);
    }

    /// <summary>
    /// A tag this server has no text for still gets a mail. German, the way every other fallback in
    /// this API is German — a reset the user cannot read is still a reset they can use, and no mail
    /// at all is not.
    /// </summary>
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("tr")]
    public void A_language_that_is_not_offered_falls_back_to_German(string? language)
    {
        Assert.Equal(
            PasswordResetMail.For("olena.k@example.com", "de", "064391").Subject,
            PasswordResetMail.For("olena.k@example.com", language, "064391").Subject);
    }

    /// <summary>"uk-UA" is the same language as "uk", and a phone is as likely to name either.</summary>
    [Fact]
    public void A_regional_tag_reads_as_its_language()
    {
        Assert.Equal(
            PasswordResetMail.For("olena.k@example.com", "uk", "064391").Body,
            PasswordResetMail.For("olena.k@example.com", "uk-UA", "064391").Body);
    }
}
