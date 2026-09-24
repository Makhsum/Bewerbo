# Bewerbo

An Android app that produces a German job application — Lebenslauf and Anschreiben — from data
the user enters in their own language.

The audience is people who moved to Germany, not Germans. A German does not need this app; a
newcomer does. That is the whole wedge: input in Russian/Ukrainian/Turkish, output in correct,
idiomatic German, formatted to the norm a German HR department expects.

## Decisions

| | |
|---|---|
| Backend | C# — .NET 10 LTS, Minimal API |
| Database | PostgreSQL + EF Core |
| PDF | QuestPDF — deterministic DIN 5008 layout |
| Model | Anthropic API over `HttpClient`, structured JSON output |
| Client | Kotlin + Jetpack Compose (not MAUI) |
| Hosting | EU only (DSGVO) — CVs are personal data |

## The one architectural rule

**The model does not lay out the document.** It returns structured content as JSON; the PDF is
rendered from that by code, against a fixed DIN 5008 template. Anything else gives formatting that
drifts between runs and PDFs that applicant tracking systems cannot parse.

## Domain rules the generator has to respect

- **Anschreiben ≠ Motivationsschreiben.** Anschreiben is for jobs. Motivationsschreiben is for
  Studium and Stipendium. Conflating them is the classic foreigner mistake.
- **DIN 5008** governs the letter: Anschriftenfeld, date right-aligned, Betreffzeile in bold and
  without the word "Betreff", Anlagen at the bottom.
- **Address a person.** "Sehr geehrte Damen und Herren" is the weak fallback. The contact name and
  the Referenznummer are almost always in the posting — extract them.
- **Gaps in the Lebenslauf** read as alarm to a German reader. Name them (Sprachkurs,
  Anerkennungsverfahren, Umzug) rather than hiding them.
- **Photo and personal data** are not legally required (AGG). Expected at a traditional employer,
  out of place at a startup — advise by company type instead of always including them.
- **Foreign degrees** need the anabin/ZAB framing to mean anything to a German recruiter.
- **Do not use Europass.** In Germany it reads as foreign and bureaucratic.
- **Banned phrases**: "Hiermit bewerbe ich mich", "Ich bin ein Teamplayer", "kommunikationsstark".
  Recruiters recognise AI text by these.

## MVP

1. Profile entered once, in the user's language, with hints on what a German reader expects.
2. Lebenslauf rendered to PDF from the fixed template.
3. Job posting pasted in, Anschreiben written against it, contact person and Referenznummer pulled
   out of the text.
4. Export as one file: `Bewerbung_Vorname_Nachname_Stellenbezeichnung.pdf`.

The Arbeitszeugnis decoder — German employer references are written in code, where "zu unserer
Zufriedenheit" means a bad review — is the real moat, and belongs in version two.

## UI testing

The Compose UI is driven by ARIA over Appium. Set `testTagsAsResourceId` at the root, above every
branch, from the first screen — retrofitting it later means re-tagging the whole UI.

## Licence note

QuestPDF is free under its Community licence below roughly $1M annual revenue; above that it needs
a paid one.

## Layout of the repository

```
backend/src/Bewerbo.Api      .NET 10 Minimal API — the whole server
  Domain/                    the entities
  Data/                      EF Core DbContext
  Llm/                       OutputSchemas.cs (no layout fields), LlmClient.cs, ApplicationWriter.cs
  Rendering/                 DIN 5008 letter, Lebenslauf, Anlagenverzeichnis, merge, ATS re-read
  Services/                  timeline and gaps, posting parser, evidence locator, requirement matcher
  Text/                      FloskelRules.cs, TextReview.cs, ScriptCheck.cs
backend/tests                one test per rule this README states
android/app                  Kotlin + Compose client, five destinations
  ui/theme/                  the colour, type and spacing tokens
  ui/icons/                  24 drawn vectors — no emoji anywhere
  ui/components/             Timeline, EvidenceText, RequirementRow, DinOverlay, ReadinessRing
  ui/screens/                Overview, Profile, Posting, Match, Application, Locker
```

## Running it

```bash
# backend — no database needed; with no connection string it uses a SQLite file
dotnet run --project backend/src/Bewerbo.Api --urls http://0.0.0.0:5099

# client — 10.0.2.2 is the host as the emulator sees it
gradle -p android :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

`backend/smoke.sh` walks the whole card end to end against a running API.

### A build for a real phone

```bash
gradle -p android :app:assembleRelease -PbewerboApiUrl=https://your-backend
# app/build/outputs/apk/release/app-arm64-v8a-release.apk   — any modern phone
# app/build/outputs/apk/release/app-x86_64-release.apk      — the emulator
```

One APK per architecture, because the bundled text recogniser carries an 11 MB native library for
each: all four in one file is 56 MB, of which a phone uses a quarter.

`-PbewerboApiUrl` is not optional for a device. The default is `http://10.0.2.2:5099`, which means
"the machine running the emulator" and nothing at all on a phone. It must be an **https** address:
plain HTTP is permitted only to the three development addresses in
`app/src/main/res/xml/network_security_config.xml`, and the payload here is somebody's CV.

**Signing.** The release is signed from `android/keystore.properties`, which names a keystore and
carries its passwords. Neither is in the repository and neither may be — whoever holds them can
publish an update that every phone with Bewerbo installed accepts as ours. Without that file the
release still builds, unsigned, and cannot be installed.

```properties
storeFile=bewerbo-release.jks
storePassword=...
keyAlias=bewerbo
keyPassword=...
```

**Losing the keystore cannot be repaired.** Android identifies an app by its signature, so an update
signed with a different key will not install over one already on a device — the only way out is a
new `applicationId`. Keep a copy somewhere other than the machine that builds.

### The language model is optional, and the app says which one wrote the letter

Set `ANTHROPIC_API_KEY` to have `claude-opus-5` write the German. Without it a **complete
rule-based writer** runs instead: it produces a correct German letter and Lebenslauf structure, but
it cannot translate the user's free text, so entries stay in the language they were typed in and the
app says so rather than shipping a half-German CV. `GET /api/health` reports which is in use, and
the Bewerbung screen states it under the letter.

Either way the model never lays out the document, and either way the letter has to clear the same
rule-based Floskel check before it is accepted.
