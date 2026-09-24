using System.Globalization;
using System.Security.Cryptography;
using Bewerbo.Api.Domain;

namespace Bewerbo.Api.Services;

/// <summary>
/// The code that goes into the reset mail, and the two rules that decide whether it still counts.
///
/// Six digits and not a random string, because this is the one secret of the product a user has to
/// read off one screen and type onto another — an address they mistype is an address they can
/// correct, a code they mistype is a mail they have to wait for again. Six digits are guessable in
/// a million tries, so what is capped is the guessing: <see cref="MaxAttempts"/> wrong codes end
/// the reset, and <see cref="Lifetime"/> ends it anyway.
///
/// Drawn from <see cref="RandomNumberGenerator"/> rather than <see cref="Random"/>, for the reason
/// <see cref="SessionToken"/> is: a code somebody can predict from the time of day is not a code.
/// What is stored of it is a <see cref="PasswordHash"/> record — the same treatment the password
/// beside it gets, because a six-digit code read out of a stolen database is a six-digit code.
/// </summary>
public static class ResetCode
{
    /// <summary>How long a code lasts. Long enough for a mail to arrive and be read, and no longer.</summary>
    public static readonly TimeSpan Lifetime = TimeSpan.FromMinutes(30);

    /// <summary>How often the wrong code may be offered before the reset is over.</summary>
    public const int MaxAttempts = 5;

    /// <summary>A new code, always six digits — leading zeros included, because the mail shows it.</summary>
    public static string Issue() =>
        RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6", CultureInfo.InvariantCulture);

    /// <summary>
    /// Whether <paramref name="reset"/> may still be offered a code at all — before the code
    /// itself is looked at.
    ///
    /// Age and attempts are one question here rather than two answers at the route, so that the
    /// refusal cannot accidentally tell the two apart: whoever is typing codes learns nothing from
    /// a reset that is over, and the user who waited too long is told to ask for a new one either
    /// way.
    /// </summary>
    public static bool IsLive(PasswordReset reset, DateTimeOffset now) =>
        now < reset.ExpiresAt && reset.Attempts < MaxAttempts;
}
