using System.Security.Cryptography;

namespace Bewerbo.Api.Services;

/// <summary>
/// What is stored in place of a password: PBKDF2-HMAC-SHA256 over a random salt.
///
/// No package is pulled in for this. Rfc2898DeriveBytes is in the BCL, the csproj carries only the
/// four references it always had, and the one thing that must not be got wrong here — comparing the
/// two hashes in constant time — is one call away in the same namespace.
///
/// The record is self-describing: iterations, salt and hash in one string, separated by dots. A
/// stored password outlives the number of iterations that was current when it was written, so the
/// number it was written with travels beside it rather than being remembered in code.
/// </summary>
public static class PasswordHash
{
    /// <summary>OWASP's figure for PBKDF2-HMAC-SHA256 at the time of writing.</summary>
    private const int Iterations = 600_000;
    private const int SaltBytes = 16;
    private const int HashBytes = 32;

    public static string Create(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(SaltBytes);
        var hash = Derive(password, salt, Iterations);
        return $"{Iterations}.{Convert.ToHexString(salt)}.{Convert.ToHexString(hash)}";
    }

    /// <summary>
    /// Whether <paramref name="password"/> is the one <paramref name="stored"/> was written from.
    ///
    /// A record this cannot read is a NO rather than an exception: a row written by a version that
    /// stored something else is a password nobody can prove, and a 500 in the middle of signing in
    /// would say more about the account than the refusal does.
    /// </summary>
    public static bool Verify(string password, string stored)
    {
        var parts = stored.Split('.');
        if (parts.Length != 3 || !int.TryParse(parts[0], out var iterations)) return false;

        byte[] salt, hash;
        try
        {
            salt = Convert.FromHexString(parts[1]);
            hash = Convert.FromHexString(parts[2]);
        }
        catch (FormatException)
        {
            return false;
        }

        return CryptographicOperations.FixedTimeEquals(Derive(password, salt, iterations), hash);
    }

    private static byte[] Derive(string password, byte[] salt, int iterations) =>
        Rfc2898DeriveBytes.Pbkdf2(password, salt, iterations, HashAlgorithmName.SHA256, HashBytes);
}
