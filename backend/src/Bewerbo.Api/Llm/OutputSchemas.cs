using System.Text.Json;

namespace Bewerbo.Api.Llm;

/// <summary>
/// The JSON schemas handed to the model as <c>output_config.format</c>.
///
/// This is where the one architectural rule is enforced mechanically. Every schema sets
/// <c>additionalProperties: false</c>, so the model cannot return a field that is not listed here;
/// and no field listed here is a layout field. <see cref="AssertNoLayoutFields"/> re-checks that at
/// startup, so the rule survives somebody adding a property in good faith later.
/// </summary>
public static class OutputSchemas
{
    public const string LetterSchemaName = "anschreiben_inhalt";
    public const string CvSchemaName = "lebenslauf_inhalt";
    public const string PostingSchemaName = "stellenanzeige_auszug";
    public const string AssistantSchemaName = "assistent_antwort";

    public const string Letter = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["salutation", "subject", "paragraphs", "closing", "attachments"],
      "properties": {
        "salutation":  { "type": "string" },
        "subject":     { "type": "string" },
        "paragraphs":  { "type": "array", "items": { "type": "string" }, "minItems": 3, "maxItems": 5 },
        "closing":     { "type": "string" },
        "attachments": { "type": "array", "items": { "type": "string" } }
      }
    }
    """;

    public const string Cv = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["headline", "sections"],
      "properties": {
        "headline": { "type": "string" },
        "sections": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title", "items"],
            "properties": {
              "title": { "type": "string" },
              "items": {
                "type": "array",
                "items": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["period", "title", "subtitle", "bullets", "isGap"],
                  "properties": {
                    "period":   { "type": "string" },
                    "title":    { "type": "string" },
                    "subtitle": { "type": "string" },
                    "bullets":  { "type": "array", "items": { "type": "string" } },
                    "isGap":    { "type": "boolean" }
                  }
                }
              }
            }
          }
        }
      }
    }
    """;

    public const string Posting = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["fields", "requirements", "employerType"],
      "properties": {
        "fields": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["key", "value", "quote", "confidence"],
            "properties": {
              "key":   { "type": "string",
                         "enum": ["contact", "contactRole", "company", "companyAddress",
                                  "reference", "title", "start"] },
              "value": { "type": "string" },
              "quote": { "type": "string",
                         "description": "The exact substring of the posting this value was read from." },
              "confidence": { "type": "string", "enum": ["sicher", "pruefen"] }
            }
          }
        },
        "requirements": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "quote"],
            "properties": {
              "text":  { "type": "string" },
              "quote": { "type": "string" }
            }
          }
        },
        "employerType": { "type": "string",
                          "enum": ["Konzern", "Mittelstand", "Startup", "OeffentlicherDienst"] }
      }
    }
    """;

    /// <summary>
    /// The assistant's turn. Prose, and therefore the one schema here whose strings are NOT German:
    /// what goes in them is written in the language the user wrote in. The shape is still closed,
    /// for the reason every schema in this file is — an answer with a field nobody drew is an
    /// answer nobody reads.
    /// </summary>
    public const string Assistant = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["reply", "missing"],
      "properties": {
        "reply":   { "type": "string" },
        "missing": { "type": "array", "items": { "type": "string" }, "maxItems": 6 }
      }
    }
    """;

    /// <summary>
    /// Anything that would let the model decide how the document looks. If one of these ever turns
    /// up as a property name in a schema above, the build of the running process stops here rather
    /// than shipping a renderer that takes layout orders from a language model.
    /// </summary>
    private static readonly string[] LayoutFieldNames =
    [
        "font", "fontsize", "fontfamily", "size", "margin", "margins", "padding", "spacing",
        "align", "alignment", "position", "x", "y", "width", "height", "page", "pagebreak",
        "bold", "italic", "underline", "colour", "color", "style", "css", "html", "indent",
        "linespacing", "leading", "columns", "layout", "template", "format",
    ];

    /// <summary>Called at startup. Cheap, and it is the only thing standing between a well-meant
    /// schema edit and PDFs whose formatting drifts between runs.</summary>
    public static void AssertNoLayoutFields()
    {
        foreach (var (name, json) in new[]
                 {
                     (LetterSchemaName, Letter), (CvSchemaName, Cv), (PostingSchemaName, Posting),
                     (AssistantSchemaName, Assistant),
                 })
        {
            using var doc = JsonDocument.Parse(json);
            var offenders = new List<string>();
            Walk(doc.RootElement, offenders);
            if (offenders.Count > 0)
            {
                throw new InvalidOperationException(
                    $"Schema '{name}' declares layout field(s): {string.Join(", ", offenders)}. " +
                    "The model does not lay out the document — layout belongs in Rendering/.");
            }
        }
    }

    private static void Walk(JsonElement element, List<string> offenders)
    {
        switch (element.ValueKind)
        {
            case JsonValueKind.Object:
                foreach (var property in element.EnumerateObject())
                {
                    // Only the members of a "properties" object are field names; "type", "items",
                    // "enum" and friends are schema keywords and must not be flagged.
                    if (property.NameEquals("properties") &&
                        property.Value.ValueKind == JsonValueKind.Object)
                    {
                        foreach (var field in property.Value.EnumerateObject())
                        {
                            if (LayoutFieldNames.Contains(field.Name.ToLowerInvariant()))
                            {
                                offenders.Add(field.Name);
                            }
                        }
                    }
                    Walk(property.Value, offenders);
                }
                break;

            case JsonValueKind.Array:
                foreach (var item in element.EnumerateArray()) Walk(item, offenders);
                break;
        }
    }
}
