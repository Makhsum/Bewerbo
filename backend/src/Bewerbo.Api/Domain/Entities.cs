namespace Bewerbo.Api.Domain;

/// <summary>
/// Everything a user enters about themselves. Free text, in <see cref="InputLanguage"/> —
/// never German unless that happens to be the language they chose.
/// </summary>
public class Profile
{
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>BCP-47 tag of the language the user types in: "ru", "uk", "tr", "en", "de".</summary>
    public string InputLanguage { get; set; } = "ru";

    public string FirstName { get; set; } = "";
    public string LastName { get; set; } = "";
    public string Street { get; set; } = "";
    public string PostalCode { get; set; } = "";
    public string City { get; set; } = "";
    public string Phone { get; set; } = "";
    public string Email { get; set; } = "";
    public DateOnly? BirthDate { get; set; }

    /// <summary>Which of the German layouts the Lebenslauf is rendered with. Never Europass.</summary>
    public CvTemplate Template { get; set; } = CvTemplate.Klassisch;

    public List<ExperienceEntry> Experience { get; set; } = [];
    public List<EducationEntry> Education { get; set; } = [];
    public List<LanguageSkill> Languages { get; set; } = [];
    public List<GapExplanation> Gaps { get; set; } = [];
    public List<StoredDocument> Documents { get; set; } = [];
    public List<Application> Applications { get; set; } = [];
}

public enum CvTemplate { Klassisch, Modern, Fachlich }

public class ExperienceEntry
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }

    public string Position { get; set; } = "";
    public string Employer { get; set; } = "";
    public string Location { get; set; } = "";
    public DateOnly From { get; set; }
    /// <summary>Null means "heute" — the entry is the current job.</summary>
    public DateOnly? To { get; set; }
    /// <summary>Vollzeit / Teilzeit 20 Std. etc., as the user phrased it.</summary>
    public string Workload { get; set; } = "";
    public string Industry { get; set; } = "";
    /// <summary>One duty per line, in the user's own language.</summary>
    public string Duties { get; set; } = "";
    public bool ReferenceOnFile { get; set; }

    public IEnumerable<string> DutyLines =>
        Duties.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
}

public class EducationEntry
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }

    public string Degree { get; set; } = "";
    public string Institution { get; set; } = "";
    public string Location { get; set; } = "";
    public string Country { get; set; } = "";
    public DateOnly From { get; set; }
    public DateOnly? To { get; set; }

    /// <summary>
    /// The anabin/ZAB equivalence, only ever what the user confirmed. A wrong equivalence claim in
    /// a Lebenslauf is worse than none at all, so this stays empty until confirmed.
    /// </summary>
    public string? AnabinAssessment { get; set; }
    public string? GermanEquivalent { get; set; }
    public bool EquivalenceConfirmed { get; set; }

    /// <summary>
    /// A Zeugnisbewertung applied for at the ZAB whose result is not back yet. That is a fact about
    /// the user's own application and not a claim about the degree, so it reaches the Lebenslauf
    /// while <see cref="EquivalenceConfirmed"/> is still false — named as outstanding, never as a
    /// settled equivalence.
    /// </summary>
    public bool ZabAssessmentPending { get; set; }
}

public class LanguageSkill
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }

    /// <summary>
    /// Where the user put this language in their own list. A Lebenslauf names the Muttersprache
    /// first and the weakest language last, and that is the user's statement to make — the rows
    /// have no order of their own, so the position they were sent in is kept.
    /// </summary>
    public int Ordinal { get; set; }

    public string Language { get; set; } = "";
    /// <summary>CEFR level, or "Muttersprache".</summary>
    public string Level { get; set; } = "";
    public bool CertificateOnFile { get; set; }
}

/// <summary>
/// A gap between two entries, and the reason the user gave for it. Gaps read as alarm to a German
/// reader — the product's answer is to name them, not to hide them.
/// </summary>
public class GapExplanation
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }

    public DateOnly From { get; set; }
    public DateOnly To { get; set; }
    /// <summary>What the user typed, in their own language.</summary>
    public string Reason { get; set; } = "";
    /// <summary>The German wording that goes into the Lebenslauf.</summary>
    public string GermanWording { get; set; } = "";
}

public class StoredDocument
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }

    public string Title { get; set; } = "";
    public DocumentKind Kind { get; set; }
    public string Note { get; set; } = "";
    public int PageCount { get; set; }
    public DateTimeOffset AddedAt { get; set; } = DateTimeOffset.UtcNow;
}

public enum DocumentKind { Arbeitszeugnis, Zertifikat, Sprachnachweis, AnabinAuszug, Sonstiges }

public class Posting
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }

    public string SourceText { get; set; } = "";
    public EmployerType EmployerType { get; set; } = EmployerType.Mittelstand;

    public string ContactName { get; set; } = "";
    public string ContactRole { get; set; } = "";
    public string Company { get; set; } = "";
    public string CompanyAddress { get; set; } = "";
    public string Reference { get; set; } = "";
    public string JobTitle { get; set; } = "";
    public string StartDate { get; set; } = "";

    /// <summary>JSON: the extracted fields with the evidence span each came from.</summary>
    public string FieldsJson { get; set; } = "[]";
    /// <summary>JSON: the requirements read out of the posting.</summary>
    public string RequirementsJson { get; set; } = "[]";

    public DateTimeOffset ParsedAt { get; set; } = DateTimeOffset.UtcNow;
}

/// <summary>
/// Drives whether a photo and personal data belong in the Lebenslauf, and the register of the
/// letter. Neither is legally required (AGG) — this is advice by employer type, not a rule.
/// </summary>
public enum EmployerType { Konzern, Mittelstand, Startup, OeffentlicherDienst }

public class Application
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }
    public Guid PostingId { get; set; }

    public LetterTone Tone { get; set; } = LetterTone.Sachlich;
    public ApplicationStatus Status { get; set; } = ApplicationStatus.Entwurf;

    /// <summary>JSON: the letter's content blocks as the model returned them. No layout fields.</summary>
    public string LetterJson { get; set; } = "{}";
    /// <summary>JSON: the requirement-by-requirement Abgleich this letter was written against.</summary>
    public string MatchJson { get; set; } = "{}";

    /// <summary>
    /// "model" or "regeln" — which writer produced <see cref="LetterJson"/>. Stored beside the
    /// letter because it is a fact about THIS letter and nothing else can be asked for it later:
    /// the writer only knows who wrote the last one, and whether a key is configured today says
    /// nothing about the letter written last week. The screen states this to the reader before
    /// they read the letter, so a guess would be a guess told as a fact.
    /// </summary>
    public string Source { get; set; } = "regeln";

    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? SentAt { get; set; }
}

public enum LetterTone { Klassisch, Sachlich, Modern }

/// <summary>
/// Where one application stands, in the order it passes through. <c>Wartend</c> is the stretch
/// after the employer has the Mappe and before they answer — "sent" is the act, waiting is the
/// state the applicant is actually in, and it is the one they sit in longest. Stored by NAME
/// (<see cref="Data.BewerboDbContext"/> converts it), so this order carries no data.
/// </summary>
public enum ApplicationStatus { Entwurf, Versendet, Wartend, Einladung, Absage }

/// <summary>
/// The person, as opposed to the phone they are holding.
///
/// Until this existed a <see cref="Profile"/> was the account: its id was written into one device's
/// preferences on first run and never left it, so a new phone started empty, cleared app data took
/// the Lebenslauf with it, and whoever picked the phone up opened somebody else's application. The
/// account is what makes the profile follow the user instead of the device.
///
/// <see cref="Email"/> is stored lower-cased — it is what the user signs in with, and nobody
/// remembers which letters they capitalised when they registered. The password is never stored;
/// see <see cref="Services.PasswordHash"/> for what <see cref="Password"/> holds instead.
/// </summary>
public class Account
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public string Email { get; set; } = "";

    /// <summary>The PBKDF2 record of the password, in the format <see cref="Services.PasswordHash"/> writes.</summary>
    public string Password { get; set; } = "";

    /// <summary>
    /// The one profile this account owns. A plain id and not a navigation property, deliberately:
    /// a profile outlives the account in no case, but the erasure of one has to be written in one
    /// place and <see cref="Services.AccountErasure"/> is it.
    /// </summary>
    public Guid ProfileId { get; set; }

    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;

    public List<AuthToken> Tokens { get; set; } = [];
}

/// <summary>
/// One device that is signed in, as the server knows it.
///
/// An opaque random token rather than a signed one, and stored HASHED the way the password is. Both
/// follow from what signing out and erasure have to mean here: a JWT stays valid until it expires
/// however firmly the user pressed "Sign out", and a token readable in the database is a password
/// for every account in it. Deleting the row is what actually revokes the session.
/// </summary>
public class AuthToken
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid AccountId { get; set; }
    public Account? Account { get; set; }

    /// <summary>SHA-256 of the token the device holds, hex. The token itself exists only on the device.</summary>
    public string TokenHash { get; set; } = "";

    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
}
