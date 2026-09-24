using System.Text.Json.Serialization;

namespace Bewerbo.Api.Llm;

// ---------------------------------------------------------------------------------------------
// These records are the ONLY thing the model is allowed to return.
//
// The one architectural rule: the model does not lay out the document. Note what is absent here —
// no font, no size, no margin, no position, no page break, no alignment. Layout lives in
// Rendering/*. The rule is enforced by the shape of this contract, not by asking nicely: the JSON
// schema handed to the model is generated from these records with additionalProperties: false, so
// a layout field it invented would be rejected before it ever reached a renderer.
// ---------------------------------------------------------------------------------------------

/// <summary>The Anschreiben as content: an address, a subject line and paragraphs.</summary>
public record LetterContent
{
    /// <summary>"Sehr geehrte Frau Dr. Weber" — without the comma, the renderer adds it.</summary>
    [JsonPropertyName("salutation")]
    public string Salutation { get; init; } = "";

    /// <summary>
    /// The Betreffzeile. Must not contain the word "Betreff" and must carry the Referenznummer
    /// when the posting named one.
    /// </summary>
    [JsonPropertyName("subject")]
    public string Subject { get; init; } = "";

    /// <summary>The body, one string per paragraph, in order.</summary>
    [JsonPropertyName("paragraphs")]
    public List<string> Paragraphs { get; init; } = [];

    [JsonPropertyName("closing")]
    public string Closing { get; init; } = "Mit freundlichen Grüßen";

    /// <summary>The Anlagenverzeichnis entries, named as the reader will see them.</summary>
    [JsonPropertyName("attachments")]
    public List<string> Attachments { get; init; } = [];
}

/// <summary>The German rendering of the profile. Again: content only.</summary>
public record CvContent
{
    [JsonPropertyName("headline")]
    public string Headline { get; init; } = "";

    [JsonPropertyName("sections")]
    public List<CvSection> Sections { get; init; } = [];
}

public record CvSection
{
    /// <summary>"Berufserfahrung", "Ausbildung", "Sprachen" …</summary>
    [JsonPropertyName("title")]
    public string Title { get; init; } = "";

    [JsonPropertyName("items")]
    public List<CvItem> Items { get; init; } = [];
}

public record CvItem
{
    /// <summary>"09/2023 – heute"</summary>
    [JsonPropertyName("period")]
    public string Period { get; init; } = "";

    [JsonPropertyName("title")]
    public string Title { get; init; } = "";

    [JsonPropertyName("subtitle")]
    public string Subtitle { get; init; } = "";

    [JsonPropertyName("bullets")]
    public List<string> Bullets { get; init; } = [];

    /// <summary>True for a named gap, so the renderer can set it apart without being told how.</summary>
    [JsonPropertyName("isGap")]
    public bool IsGap { get; init; }
}

/// <summary>What the parser read out of a posting, each field with the words it came from.</summary>
public record PostingExtract
{
    [JsonPropertyName("fields")]
    public List<ExtractedField> Fields { get; init; } = [];

    [JsonPropertyName("requirements")]
    public List<ExtractedRequirement> Requirements { get; init; } = [];

    /// <summary>Konzern | Mittelstand | Startup | OeffentlicherDienst</summary>
    [JsonPropertyName("employerType")]
    public string EmployerType { get; init; } = "Mittelstand";
}

public record ExtractedField
{
    /// <summary>contact | contactRole | company | companyAddress | reference | title | start</summary>
    [JsonPropertyName("key")]
    public string Key { get; init; } = "";

    [JsonPropertyName("value")]
    public string Value { get; init; } = "";

    /// <summary>
    /// The exact substring of the posting the value was read from — quoted text, never character
    /// offsets. Offsets drift as soon as anything re-encodes the text; a quote can be searched for.
    /// </summary>
    [JsonPropertyName("quote")]
    public string Quote { get; init; } = "";

    /// <summary>sicher | pruefen — a field the user should check before it goes in a letter.</summary>
    [JsonPropertyName("confidence")]
    public string Confidence { get; init; } = "sicher";
}

public record ExtractedRequirement
{
    [JsonPropertyName("text")]
    public string Text { get; init; } = "";

    [JsonPropertyName("quote")]
    public string Quote { get; init; } = "";
}

/// <summary>
/// One turn of the assistant: what it understood, and what it still needs.
///
/// The one record here that is PROSE rather than content for a document, and that is deliberate.
/// Everything else in this file is written in German for a German reader; this is written for the
/// user, in the language they wrote in, and it never reaches a page — see
/// <see cref="AssistantConversation"/> for why the rule about finished sentences does not reach it.
/// </summary>
public record AssistantReply
{
    /// <summary>The answer itself: what was understood, said back in the user's own language.</summary>
    [JsonPropertyName("reply")]
    public string Reply { get; init; } = "";

    /// <summary>
    /// What is still missing before a Lebenslauf can be produced, one short line each and in the
    /// same language as <see cref="Reply"/>. Empty when nothing is outstanding.
    /// </summary>
    [JsonPropertyName("missing")]
    public List<string> Missing { get; init; } = [];

    /// <summary>
    /// What of it would stand in the profile, one entry each. Empty for a turn that carried no
    /// station, qualification or language — a question answered, a greeting.
    /// </summary>
    [JsonPropertyName("proposals")]
    public List<AssistantProposal> Proposals { get; init; } = [];

    /// <summary>
    /// The person themselves, where the conversation named them — the one part of the profile that
    /// is not a list of entries. All fields empty for a turn that said nothing about who is writing.
    /// </summary>
    [JsonPropertyName("person")]
    public AssistantPerson Person { get; init; } = new();
}

/// <summary>
/// One thing the assistant understood, as it would stand in the profile — the German beside the
/// user's own wording it was read from.
///
/// <see cref="Source"/> is quoted for the reason <see cref="ExtractedField.Quote"/> is: the user
/// is deciding about a pair, and a paraphrase of their own sentence is not something they can
/// recognise as theirs. <see cref="Title"/> and <see cref="Detail"/> are the German, and they are
/// the exact strings a profile entry is made of — nothing re-derives them on the way in, so what
/// the user accepted is what the profile carries.
///
/// Nothing here is stored by the turn that produced it. An accepted proposal is saved by the
/// client through the profile's own section routes, so this API keeps one write path.
/// </summary>
public record AssistantProposal
{
    /// <summary>
    /// berufserfahrung | ausbildung | sprachen — the profile section it belongs in, spelled as
    /// <c>PATCH /api/profile/{id}/sections/…</c> spells it.
    /// </summary>
    [JsonPropertyName("kind")]
    public string Kind { get; init; } = "";

    /// <summary>The user's own words this was read from, quoted, in their own language.</summary>
    [JsonPropertyName("source")]
    public string Source { get; init; } = "";

    /// <summary>The German first line of the entry: the Position, the Abschluss or the Sprache.</summary>
    [JsonPropertyName("title")]
    public string Title { get; init; } = "";

    /// <summary>
    /// The German second line: the Arbeitgeber, the Institution or the Niveau. May be empty —
    /// somebody who says what they did without saying where still has a station.
    /// </summary>
    [JsonPropertyName("detail")]
    public string Detail { get; init; } = "";

    /// <summary>The start, as a year and a month — never a guessed one. Empty for a language.</summary>
    [JsonPropertyName("from")]
    public string From { get; init; } = "";

    /// <summary>The end, empty for something still going on and for a language.</summary>
    [JsonPropertyName("to")]
    public string To { get; init; } = "";
}

/// <summary>
/// Who the Lebenslauf is about, as the conversation named them — the header of the document and
/// nothing else.
///
/// It is NOT an <see cref="AssistantProposal"/>, and that is deliberate: a proposal is one entry of
/// a list and carries a German title with a period beside it, while the person is a set of fields
/// the profile has exactly one of. Folding it into the proposal's Kind would mean parsing a name
/// and an Anschrift back out of two free-text lines, and what the user accepted would no longer be
/// what the profile receives.
///
/// The fields are not translated: a name, a street and a postal code read the same in every
/// language, which is why nothing here has a German half and a source half the way a proposal does.
/// <see cref="Source"/> quotes the sentence they were read from for the reason
/// <see cref="AssistantProposal.Source"/> does — the user is deciding about a pair.
/// </summary>
public record AssistantPerson
{
    /// <summary>The user's own words this was read from, quoted, in their own language.</summary>
    [JsonPropertyName("source")]
    public string Source { get; init; } = "";

    [JsonPropertyName("firstName")]
    public string FirstName { get; init; } = "";

    [JsonPropertyName("lastName")]
    public string LastName { get; init; } = "";

    [JsonPropertyName("street")]
    public string Street { get; init; } = "";

    [JsonPropertyName("postalCode")]
    public string PostalCode { get; init; } = "";

    [JsonPropertyName("city")]
    public string City { get; init; } = "";

    [JsonPropertyName("phone")]
    public string Phone { get; init; } = "";

    [JsonPropertyName("email")]
    public string Email { get; init; } = "";

    /// <summary>Whether this names anything at all. An answer that read nothing about the person
    /// comes back with every field empty rather than with a null, as every other string here does.</summary>
    [JsonIgnore]
    public bool IsEmpty =>
        new[] { FirstName, LastName, Street, PostalCode, City, Phone, Email }
            .All(string.IsNullOrWhiteSpace);
}
