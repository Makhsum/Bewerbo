using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Bewerbo.Api.Llm;

public interface ILanguageModel
{
    /// <summary>Whether a key is configured at all. False means the deterministic writer is used.</summary>
    bool IsConfigured { get; }

    /// <summary>
    /// Asks for one structured answer. <paramref name="schemaJson"/> is the contract — the answer is
    /// deserialised into <typeparamref name="T"/> and nothing outside the schema can come back.
    /// </summary>
    Task<T?> CompleteAsync<T>(string system, string user, string schemaName, string schemaJson,
        CancellationToken ct = default);
}

public class LlmOptions
{
    /// <summary>The most capable model available; the letter is the product.</summary>
    public string Model { get; set; } = "claude-opus-5";
    public string? ApiKey { get; set; }
    public string BaseUrl { get; set; } = "https://api.anthropic.com/v1/messages";
    public int MaxTokens { get; set; } = 4096;
}

/// <summary>
/// The Anthropic call. It asks for content and only content: the JSON schema it hands over carries
/// no layout field, so there is no channel through which the model could tell the renderer what the
/// page should look like even if it wanted to. See <see cref="OutputSchemas"/>.
/// </summary>
public class LlmClient(HttpClient http, LlmOptions options, ILogger<LlmClient> log) : ILanguageModel
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public bool IsConfigured => !string.IsNullOrWhiteSpace(options.ApiKey);

    public async Task<T?> CompleteAsync<T>(string system, string user, string schemaName,
        string schemaJson, CancellationToken ct = default)
    {
        if (!IsConfigured) return default;

        var body = new
        {
            model = options.Model,
            max_tokens = options.MaxTokens,
            system,
            messages = new[] { new { role = "user", content = user } },
            output_config = new
            {
                format = new
                {
                    type = "json_schema",
                    name = schemaName,
                    schema = JsonSerializer.Deserialize<JsonElement>(schemaJson),
                },
            },
        };

        using var request = new HttpRequestMessage(HttpMethod.Post, options.BaseUrl)
        {
            Content = new StringContent(JsonSerializer.Serialize(body, Json), Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("x-api-key", options.ApiKey);
        request.Headers.Add("anthropic-version", "2023-06-01");
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        try
        {
            using var response = await http.SendAsync(request, ct);
            var payload = await response.Content.ReadAsStringAsync(ct);
            if (!response.IsSuccessStatusCode)
            {
                log.LogWarning("Anthropic call failed with {Status}: {Body}", response.StatusCode, payload);
                return default;
            }

            using var doc = JsonDocument.Parse(payload);
            var text = doc.RootElement.GetProperty("content")[0].GetProperty("text").GetString();
            return text is null ? default : JsonSerializer.Deserialize<T>(text, Json);
        }
        catch (Exception ex)
        {
            // A failed call must never take the application down: the deterministic writer is a
            // complete implementation, not a stub, so falling back to it is a real answer.
            log.LogWarning(ex, "Anthropic call failed; falling back to the deterministic writer.");
            return default;
        }
    }
}
