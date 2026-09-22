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
}

public class LanguageSkill
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProfileId { get; set; }
    public Profile? Profile { get; set; }

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

    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? SentAt { get; set; }
}

public enum LetterTone { Klassisch, Sachlich, Modern }

public enum ApplicationStatus { Entwurf, Versendet, Einladung, Absage }
