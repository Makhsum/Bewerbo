---
id: local-avw575

title: Everything that appears after a posting is parsed can be driven by a test

state: Done

category: Improvement

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-23T06:57:20.5944234Z

lastChangeAt: 2026-09-23T15:50:33.6755776Z

lastChangedBy:
  kind: agent
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1
---

On the posting screen only the text field and the parse button carry identifiers. Everything the parse produces — the fields read out, their correction links, the employer-type control, and the buttons leading to the match and to the letter — carries none, so an automated run cannot get past pasting the text and has to fall back on tapping coordinates.

**Acceptance criteria**
- Every control that appears after a posting is parsed can be addressed by an automated run.
- A run can walk from pasted text through to the exported file without tapping coordinates.
