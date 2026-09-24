using System.Security.Cryptography;
using System.Text;

namespace Bewerbo.Api.Services;

/// <summary>
/// The bearer one signed-in device carries, and the record the server keeps of it.
///
/// The device gets the token once, at register or sign-in, and it is never written down here —
/// only <see cref="HashOf"/> is, for the reason a password is not stored either: a table of live
/// tokens read by anybody is a table of open sessions. SHA-256 alone and not PBKDF2, deliberately:
/// this is 256 bits of randomness rather than something a person chose, so there is nothing for a
/// dictionary to guess and the hash is checked on every request that names it.
/// </summary>
public static class SessionToken
{
    private const int TokenBytes = 32;

    /// <summary>A new token for a device. Url-safe base64, so it survives a header untouched.</summary>
    public static string Issue() =>
        Convert.ToBase64String(RandomNumberGenerator.GetBytes(TokenBytes))
            .Replace('+', '-').Replace('/', '_').TrimEnd('=');

    /// <summary>What <see cref="Domain.AuthToken.TokenHash"/> holds for the given token.</summary>
    public static string HashOf(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    /// <summary>
    /// The token out of an Authorization header, or null when the header names none.
    ///
    /// "Bearer " is expected but not insisted on — a header that is nothing but the token is read
    /// as the token, because the alternative is a 401 that looks exactly like a wrong password to
    /// whoever is reading the log.
    /// </summary>
    public static string? FromHeader(string? header)
    {
        var value = header?.Trim();
        if (string.IsNullOrEmpty(value)) return null;

        // The word has to be followed by a space or by nothing at all. Matched as a prefix alone it
        // also ate the first six characters of a token that happened to begin with those letters —
        // which a base64 token does about once in a billion, and never on the day it is looked for.
        const string scheme = "Bearer";
        if (value.StartsWith(scheme, StringComparison.OrdinalIgnoreCase)
            && (value.Length == scheme.Length || char.IsWhiteSpace(value[scheme.Length])))
        {
            value = value[scheme.Length..].Trim();
        }

        return string.IsNullOrEmpty(value) ? null : value;
    }
}
