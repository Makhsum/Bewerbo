using System.Text.Json.Serialization;
using Bewerbo.Api.Data;
using Bewerbo.Api.Endpoints;
using Bewerbo.Api.Llm;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);


// The rule, checked before the first request rather than trusted: no schema handed to the model may
// contain a layout field.
OutputSchemas.AssertNoLayoutFields();

builder.Services.AddDbContext<BewerboDbContext>(options =>
{
    // PostgreSQL is the product's database and the one the EU hosting runs. A dev machine rarely
    // has one, so a connection string decides: with one, Npgsql; without, a SQLite file. Same
    // DbContext, same model, same migrations shape — only the provider differs.
    var connection = builder.Configuration.GetConnectionString("Bewerbo");
    if (!string.IsNullOrWhiteSpace(connection))
    {
        options.UseNpgsql(connection);
    }
    else
    {
        var path = Path.Combine(AppContext.BaseDirectory, "bewerbo.db");
        options.UseSqlite($"Data Source={path}");
    }
});

builder.Services.Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(options =>
{
    options.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});

builder.Services.AddSingleton(new LlmOptions
{
    ApiKey = builder.Configuration["Anthropic:ApiKey"]
             ?? Environment.GetEnvironmentVariable("ANTHROPIC_API_KEY"),
    Model = builder.Configuration["Anthropic:Model"] ?? "claude-opus-5",
});
builder.Services.AddHttpClient<ILanguageModel, LlmClient>(client =>
{
    client.Timeout = TimeSpan.FromSeconds(90);
});
builder.Services.AddScoped<IApplicationWriter, ApplicationWriter>();

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<BewerboDbContext>();
    db.Database.EnsureCreated();
}

app.MapProfileEndpoints();
app.MapPostingEndpoints();
app.MapApplicationEndpoints();
app.MapLockerEndpoints();

app.MapGet("/api/health", (ILanguageModel model) => Results.Ok(new
{
    status = "ok",
    // Which writer will run, said out loud: a letter written by the rule-based writer and one
    // written by the model are both real output, but nobody should have to guess which they got.
    writer = model.IsConfigured ? "model" : "regeln",
}));

app.Run();

/// <summary>Named so the integration tests can reach the host builder.</summary>
public partial class Program;
