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
