using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;

namespace Bewerbo.Api.Contracts;

// The wire shapes. Kept apart from Domain/ so that adding a column does not silently change the
// client contract, and so a DateOnly crosses the wire as "2023-09-01" rather than as an object.

public record PersonDto(
    string InputLanguage, string FirstName, string LastName, string Street, string PostalCode,
    string City, string Phone, string Email, string? BirthDate, string Template);

public record ExperienceDto(
    Guid? Id, string Position, string Employer, string Location, string From, string? To,
    string Workload, string Industry, string Duties, bool ReferenceOnFile);

public record EducationDto(
    Guid? Id, string Degree, string Institution, string Location, string Country,
    string From, string? To, string? AnabinAssessment, string? GermanEquivalent,
    bool EquivalenceConfirmed);

public record LanguageDto(Guid? Id, string Language, string Level, bool CertificateOnFile);

public record GapDto(string From, string To, int Months, bool Explained, string? Reason, string? GermanWording);

public record GapUpdateDto(string From, string To, string Reason, string? GermanWording);

public record DocumentDto(Guid? Id, string Title, string Kind, string Note, int PageCount);

public record ProfileDto(
    Guid Id,
    PersonDto Person,
    IReadOnlyList<ExperienceDto> Experience,
    IReadOnlyList<EducationDto> Education,
    IReadOnlyList<LanguageDto> Languages,
    IReadOnlyList<DocumentDto> Documents,
    int Completeness,
    TranslationDto Translation);

public record TimelinePeriodDto(string Kind, string Label, string From, string To, bool Ongoing);

public record TimelineDto(
    int FirstYear, int LastYear,
    IReadOnlyList<TimelinePeriodDto> Periods,
    IReadOnlyList<GapDto> Gaps);

public record ParsePostingRequest(Guid ProfileId, string Text, string? EmployerType);

public record EvidenceFieldDto(
    string Key, string Value, string Quote, string Confidence, int SpanStart, int SpanLength);

public record PostingDto(
    Guid Id, string SourceText, string EmployerType,
    IReadOnlyList<EvidenceFieldDto> Fields,
    IReadOnlyList<string> Requirements);

public record CorrectFieldRequest(string Key, string Value);

/// <summary>
/// One requirement of the posting against the profile. <paramref name="Text"/> is quoted from the
/// advert and stays as it stands; <paramref name="Evidence"/> and <paramref name="Action"/> are
/// the app's own words about it, so they also travel as a kind and its arguments for the screen
/// to write in the user's language. See <see cref="NextStepDto"/> — same reason, same shape.
/// </summary>
public record RequirementDto(
    string Text,
    string State,
    string Evidence,
    string Action,
    string Language = "",
    string EvidenceKind = "",
    IReadOnlyList<string>? EvidenceArgs = null,
    string ActionKind = "",
    IReadOnlyList<string>? ActionArgs = null);

/// <summary>One document the posting asks to see. onFile false means outstanding.</summary>
public record DemandedDocumentDto(
    string Kind, string Title, string Quote, bool OnFile,
    IReadOnlyList<string>? TitleArgs = null);

public record MatchDto(
    Guid PostingId, string Company, string Reference,
    int Covered, int Total, int Percent,
    IReadOnlyList<RequirementDto> Requirements,
    IReadOnlyList<DemandedDocumentDto> Documents);

public record CreateApplicationRequest(Guid ProfileId, Guid PostingId, string? Tone);

public record LetterDto(
    string Salutation, string Subject, IReadOnlyList<string> Paragraphs, string Closing,
    IReadOnlyList<string> Attachments);

public record ApplicationDto(
    Guid Id, Guid ProfileId, Guid PostingId, string Tone, string Status, string Source,
    LetterDto Letter, string FileName, IReadOnlyList<RequirementDto> Requirements);

public record ReviewCheckDto(
    string Key,
    string Title,
    string Verdict,
    string Detail,
    IReadOnlyList<string> Items,
    string DetailKind = "",
    IReadOnlyList<string>? DetailArgs = null);

public record ReviewDto(bool Passed, int HintCount, IReadOnlyList<ReviewCheckDto> Checks);

/// <summary>
/// One machine-readability check. <paramref name="Verdict"/> is "ok", "fehler" or "ungeprueft" —
/// the third one says the check reads a profile field that is empty, which is not a fault of the
/// produced document. <paramref name="Target"/> is then the screen that field is filled in on,
/// carried the way <see cref="NextStepDto.Target"/> carries it.
/// </summary>
public record AtsFindingDto(
    string Key,
    string Label,
    string Verdict,
    string Detail,
    string DetailKind = "",
    IReadOnlyList<string>? DetailArgs = null,
    string Target = "");

public record AtsDto(bool Passed, int PageCount, int SizeBytes, string FileName, IReadOnlyList<AtsFindingDto> Findings);

public record StatusRequest(string Status);

/// <summary>
/// One outstanding step. <paramref name="Kind"/> and <paramref name="Args"/> are what the client
/// renders: the server does not know the interface language, so a finished German sentence is the
/// one thing it must not send as a label. <paramref name="Title"/> and <paramref name="Detail"/>
/// stay as the German wording, both as the fallback for a client that does not know a kind and
/// because they are what the API answered before.
/// </summary>
public record NextStepDto(
    string Key,
    string Title,
    string Detail,
    string Severity,
    string Target,
    string Kind = "",
    IReadOnlyList<string>? Args = null);

/// <summary>
/// One application in the Übersicht's list. <paramref name="OpenSteps"/> is what THIS application
/// still needs, as opposed to <see cref="OverviewDto.NextSteps"/>, which is what the profile needs.
/// </summary>
public record ActiveApplicationDto(
    Guid Id, string JobTitle, string Company, string Reference, string Status, string? SentAt,
    IReadOnlyList<NextStepDto> OpenSteps);

/// <summary>
/// The Übersicht. <paramref name="CanStartApplication"/> is whether the profile carries enough for
/// an application to be worth beginning, which is what decides the one first step the screen offers.
/// </summary>
public record OverviewDto(
    string DisplayName, string City, int ApplicationCount,
    bool CanStartApplication, int ProfileCompleteness,
    int GapsExplained, int GapsTotal,
    int EvidenceOnFile, int EvidenceExpected,
    IReadOnlyList<NextStepDto> NextSteps,
    IReadOnlyList<ActiveApplicationDto> Applications,
    IReadOnlyList<DocumentDto> Documents);

public static class DtoMapping
{
    public static string Iso(DateOnly d) => d.ToString("yyyy-MM-dd");
    public static DateOnly ParseDate(string s) => DateOnly.Parse(s);
    public static DateOnly? ParseNullableDate(string? s) =>
        string.IsNullOrWhiteSpace(s) ? null : DateOnly.Parse(s);

    public static T ParseEnum<T>(string? value, T fallback) where T : struct, Enum =>
        Enum.TryParse<T>(value, ignoreCase: true, out var parsed) ? parsed : fallback;

    /// <summary>
    /// The wire spelling of a requirement's state. Lower case with an underscore, not the enum's
    /// own name, because it is read by the client as a key rather than shown.
    /// </summary>
    public static string StateName(RequirementState state) => state switch
    {
        RequirementState.Belegt => "belegt",
        RequirementState.Offen => "offen",
        _ => "nicht_belegt",
    };

    public static ExperienceDto ToDto(this ExperienceEntry e) => new(
        e.Id, e.Position, e.Employer, e.Location, Iso(e.From), e.To is null ? null : Iso(e.To.Value),
        e.Workload, e.Industry, e.Duties, e.ReferenceOnFile);

    public static EducationDto ToDto(this EducationEntry e) => new(
        e.Id, e.Degree, e.Institution, e.Location, e.Country, Iso(e.From),
        e.To is null ? null : Iso(e.To.Value), e.AnabinAssessment, e.GermanEquivalent,
        e.EquivalenceConfirmed);

    public static LanguageDto ToDto(this LanguageSkill l) => new(l.Id, l.Language, l.Level, l.CertificateOnFile);

    public static DocumentDto ToDto(this StoredDocument d) => new(d.Id, d.Title, d.Kind.ToString(), d.Note, d.PageCount);
}

/// <summary>
/// Whether the German output can actually be produced for this profile right now.
///
/// Translating the user's own words is the model's job. When no model is configured the rule-based
/// writer still produces a correct German Lebenslauf structure, but the free text the user typed
/// stays in their language. Saying so is the point of this type: an application that silently ships
/// a Cyrillic bullet in a "German" CV is the exact failure the product exists to prevent.
/// </summary>
public record TranslationDto(bool Available, int Pending, IReadOnlyList<string> PendingExamples);
