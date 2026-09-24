---
id: local-0nxs8x

title: Missing profile data is reported as a defect of the document

state: Done

category: Bug

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-23T06:56:44.7407495Z

lastChangeAt: 2026-09-23T16:04:45.3275792Z

lastChangedBy:
  kind: agent
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1

issues:

  - id: i47
    fieldKey: openIssues
    text: "Commit 293974d is on local main but NOT pushed - GitHub rejected the run's credential (\"Invalid username or token\"). Needs one push before the card can move to Done."
    done: true
    doneAt: 2026-09-23T16:04:43.2105067Z
    doneByName: Makhsum
    doneByUserId: 1
    implemented: true
    implementedAt: 2026-09-23T16:04:41.5722934Z
    implementedByName: Makhsum
    implementedByUserId: 1
    origin: manual
    createdAt: 2026-09-23T15:06:36.4502553Z
    createdBy:
      kind: agent
      name: Makhsum
      email: dd4006189@gmail.com
      userId: 1
---

The review section marks the application as not machine-readable and lists "name not found", "no employers" and "no periods" in red. Nothing is wrong with the document — the profile is simply empty. Reporting absent input as a fault of the output teaches the user that a red mark means nothing, and hides the real faults the check exists to catch.

**Acceptance criteria**
- A check that fails because a profile field is empty says which field is missing.
- Such a check leads the user to the place where that field is filled in.
- Red marks are reserved for faults of the produced document.
