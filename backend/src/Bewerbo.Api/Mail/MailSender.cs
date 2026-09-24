using System.Net;
using System.Net.Mail;
using System.Text;

namespace Bewerbo.Api.Mail;

/// <summary>
/// One mail, as everything in this API that wants to send one describes it. One recipient and no
/// second field for a second one: the only mail the product sends is a password reset, and "it
/// reaches the address of the account and nobody else" is a promise that is easier to keep when
/// there is nowhere to put anybody else.
/// </summary>
public record Mail(string To, string Subject, string Body);

/// <summary>
/// Where a mail goes. Configuration and not a resource, for the reason <see cref="Legal.LegalOptions"/>
/// is: a mail server compiled into the binary would be wrong for every deployment but one.
/// </summary>
public class MailOptions
{
    public string? Host { get; set; }
    public int Port { get; set; } = 587;
    public string? User { get; set; }
    public string? Password { get; set; }

    /// <summary>The address the reset appears to come from. Without one there is no sender at all.</summary>
    public string? From { get; set; }

    public bool UseSsl { get; set; } = true;

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(Host) && !string.IsNullOrWhiteSpace(From);
}

/// <summary>
/// The one way out of this API that is not an HTTP answer.
///
/// An interface with two implementations rather than one class that checks whether it is configured
/// — which is how <see cref="Llm.ILanguageModel"/> does the same job — because the two do not share
/// a line of code: one opens an SMTP connection, the other writes a file. What they do share is
/// <see cref="Kind"/>, which GET /api/health prints for the reason it prints the writer: both of
/// them produce real output, and nobody should have to guess which one they got.
/// </summary>
public interface IMailSender
{
    /// <summary>"smtp" or "datei" — what /api/health reports.</summary>
    string Kind { get; }

    Task SendAsync(Mail mail, CancellationToken ct = default);
}

/// <summary>A mail server, as an installation with <c>Mail:Host</c> and <c>Mail:From</c> configured has one.</summary>
public class SmtpMailSender(MailOptions options, ILogger<SmtpMailSender> log) : IMailSender
{
    public string Kind => "smtp";

    public async Task SendAsync(Mail mail, CancellationToken ct = default)
    {
        using var client = new SmtpClient(options.Host, options.Port) { EnableSsl = options.UseSsl };
        if (!string.IsNullOrWhiteSpace(options.User))
        {
            client.Credentials = new NetworkCredential(options.User, options.Password);
        }

        using var message = new MailMessage(options.From!, mail.To, mail.Subject, mail.Body)
        {
            SubjectEncoding = Encoding.UTF8,
            BodyEncoding = Encoding.UTF8,
        };

        await client.SendMailAsync(message, ct);
        log.LogInformation("Mail sent to one recipient: {Subject}", mail.Subject);
    }
}

/// <summary>
/// The sender an installation with no mail server configured gets: the mail is written to a file
/// next to the binary and nothing leaves the machine.
///
/// It exists for the reason the rule-based writer does — the product has to run on a dev machine
/// with nothing installed, and a password reset that throws because no SMTP host is configured is a
/// sign-in screen that cannot be finished. What keeps it from being a silent hole in a real
/// deployment is <see cref="Kind"/>: /api/health says "datei" for as long as this is what runs.
///
/// One file per mail, named for the moment it was written, with the recipient on its first line.
/// That is deliberate as well: it is what makes "the reset reached that address and no other"
/// something a test can read rather than something the code promises.
/// </summary>
public class FileMailSender(ILogger<FileMailSender> log) : IMailSender
{
    /// <summary>Beside <c>bewerbo.db</c>, which the same kind of installation writes for the same reason.</summary>
    private static readonly string Folder = Path.Combine(AppContext.BaseDirectory, "post");

    public string Kind => "datei";

    public async Task SendAsync(Mail mail, CancellationToken ct = default)
    {
        Directory.CreateDirectory(Folder);
        var path = Path.Combine(Folder, $"{DateTime.UtcNow:yyyyMMdd-HHmmss-fff}.txt");

        await File.WriteAllTextAsync(
            path,
            $"To: {mail.To}\nSubject: {mail.Subject}\n\n{mail.Body}\n",
            Encoding.UTF8,
            ct);

        log.LogInformation("No mail server is configured; the mail was written to {Path}.", path);
    }
}
