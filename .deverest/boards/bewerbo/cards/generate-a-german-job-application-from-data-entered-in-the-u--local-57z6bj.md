---
id: local-57z6bj

title: Generate a German job application from data entered in the user's own language

state: Done

category: Feature

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-22T20:23:46.0788223Z

lastChangeAt: 2026-09-24T04:45:32.1189462Z

lastChangedBy:
  kind: user
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1

issues:

  - id: i26
    fieldKey: openIssues
    text: Во вкладке Applikation слово Applikation сьесжает
    done: true
    doneAt: 2026-09-23T02:34:31.3257417Z
    doneByName: Makhsum
    doneByUserId: 1
    implemented: true
    implementedAt: 2026-09-23T00:03:51.7145506Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-22T23:36:50.9373517Z
    createdBy:
      kind: user
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i40
    fieldKey: openIssues
    text: "Stellenbezeichnung wird abgeschnitten: \"Fachkraft für Lagerlogistik (m/w/d)\" wird als \"Lagerlogistik (m/w/d)\" gelesen (Vertrauen \"sicher\") und landet so in Betreffzeile, Anschreiben und PDF-Dateiname"
    done: false
    implemented: true
    implementedAt: 2026-09-24T03:54:52.9971831Z
    implementedByName: "Run #69"
    origin: manual
    createdAt: 2026-09-23T02:34:34.7235828Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: i41
    fieldKey: openIssues
    text: "Anforderungsliste nimmt den Schlusssatz der Anzeige auf und bricht bei der Abkürzung ab: \"gute Englischkenntnisse Ihre Bewerbung richten Sie bitte an Frau Dr\""
    done: false
    implemented: true
    implementedAt: 2026-09-24T03:54:52.9971831Z
    implementedByName: "Run #69"
    origin: manual
    createdAt: 2026-09-23T02:34:37.2835744Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

attachments:

  - id: a20
    fieldKey: attachments
    fileName: screen-a-designsystem-uebersicht.png
    contentType: image/png
    sizeBytes: 437085
    storagePath: "42\\145c0328cc3a4d96914cc7662348f5f5_screen-a-designsystem-uebersicht.png"
    testRunId: 66
    uploadedAt: 2026-09-22T20:51:47.309797Z
    uploadedBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: a21
    fieldKey: attachments
    fileName: screen-b-profil-stellenanzeige.png
    contentType: image/png
    sizeBytes: 423736
    storagePath: "42\\a650c5727d8d4053bccf8dfe6e490ab0_screen-b-profil-stellenanzeige.png"
    testRunId: 66
    uploadedAt: 2026-09-22T20:51:47.3947827Z
    uploadedBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: a22
    fieldKey: attachments
    fileName: screen-c-abgleich-bewerbung-pruefung.png
    contentType: image/png
    sizeBytes: 523059
    storagePath: "42\\5b08b0d9ee814b1d8f4f05269aed180e_screen-c-abgleich-bewerbung-pruefung.png"
    testRunId: 66
    uploadedAt: 2026-09-22T20:51:47.4872598Z
    uploadedBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: a25
    fieldKey: attachments
    fileName: Screenshot_20260923_011313_Bewerbo.jpg
    contentType: image/jpeg
    sizeBytes: 191787
    storagePath: "42\\fd2db956fa28475c8525110ca17582d1_Screenshot_20260923_011313_Bewerbo.jpg"
    uploadedAt: 2026-09-22T23:36:16.7978408Z
    uploadedBy:
      kind: user
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1

  - id: a28
    fieldKey: attachments
    fileName: app-debug.apk
    contentType: application/octet-stream
    sizeBytes: 11800905
    storagePath: "42\\73ca87808faa4325a55d9c1a82a9d36c_app-debug.apk"
    uploadedAt: 2026-09-23T05:32:05.2500828Z
    uploadedBy:
      kind: agent

  - id: a29
    fieldKey: attachments
    fileName: Bewerbo-0.1-debug-7428a62-arm64-v8a.apk
    contentType: application/octet-stream
    sizeBytes: 26156464
    storagePath: "42\\dcc14457bc0d4b348a8f624af96989cb_Bewerbo-0.1-debug-7428a62-arm64-v8a.apk"
    uploadedAt: 2026-09-24T03:54:34.5551877Z
    uploadedBy:
      kind: agent

  - id: a30
    fieldKey: attachments
    fileName: Bewerbo-0.1-release-c272412-arm64-v8a.apk
    contentType: application/octet-stream
    sizeBytes: 22178768
    storagePath: "42\\92dcc1d712c647cf9b9350326fb40b9b_Bewerbo-0.1-release-c272412-arm64-v8a.apk"
    uploadedAt: 2026-09-24T04:11:35.9291701Z
    uploadedBy:
      kind: agent
---

People who move to Germany are turned down for how their application looks, not for what they can do: employers expect a Lebenslauf and an Anschreiben in a specific form, and a translated CV reads as foreign immediately. Bewerbo lets someone enter their history in their own language and get back an application a German recruiter reads as local — idiomatic German, laid out to DIN 5008, addressed to the contact named in the job posting.

**Requested by the user:** an Android app with a backend written in C#.

**Acceptance criteria**
- A user enters their data in their own language and receives a Lebenslauf as a PDF.
- Pasting a job posting produces an Anschreiben written against it.
- The finished application downloads as a single PDF file.
