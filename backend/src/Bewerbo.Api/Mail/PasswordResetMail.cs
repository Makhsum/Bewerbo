using System.Globalization;
using Bewerbo.Api.Services;

namespace Bewerbo.Api.Mail;

/// <summary>
/// The one mail this product sends, in the language the app is being read in.
///
/// This is the single place where the server writes a finished sentence for a user, and it is worth
/// saying why the rule it breaks does not apply. Everywhere else the server sends a kind plus
/// arguments and the screen writes the sentence, because the server never learns which language the
/// interface is drawn in. A mail has no screen behind it: whatever leaves here is what the user
/// reads. So the app names its interface language in the request, and the four texts below are the
/// four the app itself is offered in — see UI_LANGUAGES on the client. An unknown tag falls back to
/// German, as every other fallback in this API does.
///
/// The code is quoted on a line of its own on purpose: it is going to be copied out by hand, and a
/// number buried in a sentence is a number that gets copied with a full stop attached.
/// </summary>
public static class PasswordResetMail
{
    /// <summary>The mail for one account. One recipient, because <see cref="Mail"/> has room for one.</summary>
    public static Mail For(string to, string? language, string code)
    {
        var text = Texts.TryGetValue(Tag(language), out var found) ? found : Texts["de"];
        var minutes = ((int)ResetCode.Lifetime.TotalMinutes).ToString(CultureInfo.InvariantCulture);

        return new Mail(to, text.Subject, string.Format(CultureInfo.InvariantCulture, text.Body, code, minutes));
    }

    /// <summary>The bare language of a tag: "uk-UA" and "uk" are the same text.</summary>
    private static string Tag(string? language) =>
        (language ?? "").Trim().Split('-')[0].ToLowerInvariant();

    /// <summary>A subject and a body, where the body takes the code as {0} and the minutes as {1}.</summary>
    private record Text(string Subject, string Body);

    private static readonly Dictionary<string, Text> Texts = new()
    {
        ["de"] = new Text(
            "Ihr Code für ein neues Bewerbo-Passwort",
            """
            Sie haben für Ihr Bewerbo-Konto ein neues Passwort angefordert.

            Ihr Code:

            {0}

            Der Code gilt {1} Minuten und lässt sich einmal verwenden. Geben Sie ihn in der App ein und wählen Sie dort Ihr neues Passwort.

            Wenn Sie das nicht waren, müssen Sie nichts tun. Ihr Passwort bleibt unverändert.
            """),

        ["en"] = new Text(
            "Your code for a new Bewerbo password",
            """
            You asked for a new password for your Bewerbo account.

            Your code:

            {0}

            The code is good for {1} minutes and can be used once. Enter it in the app and choose your new password there.

            If this was not you, there is nothing to do. Your password stays as it is.
            """),

        ["ru"] = new Text(
            "Ваш код для нового пароля в Bewerbo",
            """
            Вы запросили новый пароль для своей учётной записи Bewerbo.

            Ваш код:

            {0}

            Код действует {1} минут и подходит для одной попытки. Введите его в приложении и задайте там новый пароль.

            Если это были не вы, делать ничего не нужно. Пароль останется прежним.
            """),

        ["uk"] = new Text(
            "Ваш код для нового пароля в Bewerbo",
            """
            Ви запросили новий пароль для свого облікового запису Bewerbo.

            Ваш код:

            {0}

            Код діє {1} хвилин і його можна використати один раз. Уведіть його в застосунку та задайте там новий пароль.

            Якщо це були не ви, робити нічого не потрібно. Пароль залишиться незмінним.
            """),
    };
}
