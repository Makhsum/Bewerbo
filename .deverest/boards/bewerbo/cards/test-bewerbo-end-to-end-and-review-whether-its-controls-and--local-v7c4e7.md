---
id: local-v7c4e7

title: Test Bewerbo end to end and review whether its controls and features make sense

state: Done

category: Improvement

epic: Quality assurance

dev: dd4006189@gmail.com

tester: dd4006189@gmail.com

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-22T23:40:26.7737004Z

lastChangeAt: 2026-09-24T04:45:32.1192303Z

lastChangedBy:
  kind: user
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1

issues:

  - id: i28
    fieldKey: openIssues
    text: "Ausbildung and Sprachen cannot be entered at all: saveEducation and saveLanguages exist in the client, the API and the backend, but no UI control calls them. Both sections are read-only lists with no \"Add\" button, so the whole anabin/ZAB recognition feature is unreachable and profile completeness can never exceed 91 % (Education.Count > 0 is the 12th check)."
    done: false
    implemented: true
    implementedAt: 2026-09-23T02:14:38.9450474Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T01:05:57.9579916Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i29
    fieldKey: openIssues
    text: "\"Evidence on file\" can never be satisfied: certificateOnFile and referenceOnFile are never set by anything the user can do. Adding a \"Sprachnachweis\" to the Mappe does not set the flag, so the meter stays 0/3, the \"offen\" requirement never closes, and the Übersicht keeps showing the next step \"Sprachzertifikat B2 fehlt\" while that very certificate is listed two cards below it."
    done: false
    implemented: true
    implementedAt: 2026-09-23T02:14:45.4593989Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T01:06:02.0019002Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i30
    fieldKey: openIssues
    text: The company is not read from a posting unless its name ends in GmbH, AG, SE, KG or mbH (CompanyRx in PostingParser). Hospitals, clinics, care homes, municipalities and universities — the employers this product's users apply to — never match, and the company is the addressee of the Anschriftenfeld.
    done: false
    implemented: true
    implementedAt: 2026-09-23T02:14:51.9691602Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T01:06:05.8004595Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i31
    fieldKey: openIssues
    text: "The job title swallows the sentence in front of it: JobTitleRx matches from the first capital up to \"(m/w/d)\", so \"Klinikum Muenchen sucht eine Pflegefachkraft (m/w/d)\" becomes the Stelle. It then appears verbatim in the Betreffzeile (\"Bewerbung als Klinikum Muenchen sucht eine Pflegefachkraft …\") and in the export file name."
    done: false
    implemented: true
    implementedAt: 2026-09-23T02:14:59.1968756Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T01:06:09.8218457Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i32
    fieldKey: openIssues
    text: "The soft keyboard covers the lower half of every form and the screen will not scroll while it is open. The manifest declares adjustResize, but MainActivity calls enableEdgeToEdge() and nothing applies imePadding(), so the LazyColumn viewport stays full height and believes there is nothing to scroll. Tapping \"Straße\" in the Person form types into a field the user cannot see; Telefon and Speichern are unreachable until the keyboard is dismissed."
    done: false
    implemented: true
    implementedAt: 2026-09-23T02:15:04.9871931Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T01:06:14.1542693Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i33
    fieldKey: openIssues
    text: "An application's status can never be changed: setStatus exists in the ViewModel and the API but no UI control calls it, so every application stays \"Entwurf\". The Übersicht's status pill colours for \"Einladung\", \"Absage\" and \"Versendet\" are unreachable code. Likewise savePdf(parts) is always called with null, so the API's export-part selection cannot be used."
    done: false
    implemented: true
    implementedAt: 2026-09-23T02:15:11.4182333Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T01:06:17.7738407Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i34
    fieldKey: openIssues
    text: "Backend-generated user-facing text is German only, in an otherwise English UI: every \"Nächster Schritt\", every requirement verdict (\"Nicht vorhanden — wird im Anschreiben nicht behauptet\") and every Textprüfung line comes from the server as a hardcoded German string. The Timeline legend (\"Ausbildung\", \"Beruf\", \"Lücke\") is hardcoded German in DomainComponents.kt, bypassing strings.xml and therefore also the EmojiFreeStringsTest. The plural \"1 HINTS\" is wrong in English."
    done: false
    origin: manual
    createdAt: 2026-09-23T01:06:23.2406736Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i35
    fieldKey: openIssues
    text: "Smaller findings: (1) a pasted posting and its generated letter are lost on app restart — only the profile is reloaded, although both exist on the server; (2) the client never refetches the profile on navigation, so a change made elsewhere stays invisible; (3) the \"1 GAP\" pill counts explained gaps too and stays attention-coloured after the user has answered it; (4) from the Abgleich, tapping the \"Posting\" tab does nothing — only system back returns to the posting; (5) SegmentedControl conveys selection by colour and weight only, with no selected semantics, so no screen reader can tell which template, tone or employer type is chosen; (6) PATCH with a field omitted returns HTTP 500 with an empty body instead of a 400; (7) \"Fachlich\" and \"Klassisch\" render identically apart from 4 mm of date-column width; (8) \"Eintritt\" keeps the preposition (\"zum 01.01.2027\"); (9) NU1903: SQLitePCLRaw.lib.e_sqlite3 2.1.10 has a known high-severity vulnerability."
    done: false
    origin: manual
    createdAt: 2026-09-23T01:06:29.9270561Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i39
    fieldKey: openIssues
    text: "Run 87 status of the two partly-fixed lines above. Line 34: the Zeitstrahl legend now lives in strings.xml and the plural reads \"1 hint\" — but the German-only backend prose (next steps, requirement verdicts, Textprüfung) is STILL OPEN, and deliberately so: those strings are parameterised server-side, so localising them means the API emitting message codes plus arguments and the client owning the wording. Half-translating would read worse than leaving it. Line 35: (1) session restore, (3) gap count, (4) Posting tab, (5) SegmentedControl semantics and (6) 400-instead-of-500 are fixed; (2) no profile refetch on navigation, (7) \"Fachlich\" == \"Klassisch\" and (9) the SQLitePCLRaw advisory remain open. (8) \"Eintritt keeps the preposition\" is WITHDRAWN — the value is used verbatim in \"Anfangen kann ich zum 01.01.2027.\", where it is correct German."
    done: false
    origin: manual
    createdAt: 2026-09-23T02:15:20.8959433Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1
---

Bewerbo should be tested in full and judged on its business logic, not only on whether it runs. For every screen, control and feature it should become clear whether it serves a purpose a user recognizes — or whether it merely exists and confuses whoever meets it.

**Acceptance criteria**
- The app has been walked through end to end and every defect found is written down.
- For each control and feature there is a verdict on whether it serves a purpose a user recognizes.
- Controls and features that serve no purpose are named together with the reason.
