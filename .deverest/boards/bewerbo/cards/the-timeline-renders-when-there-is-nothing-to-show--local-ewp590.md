---
id: local-ewp590

title: The timeline renders when there is nothing to show

state: Done

category: Bug

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-23T06:56:50.6032508Z

lastChangeAt: 2026-09-23T23:58:01.6929349Z

lastChangedBy:
  kind: agent
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1
---

An empty profile still draws the Zeitstrahl card: a blank chart headed "2026 – 2026", with a legend for Ausbildung, Beruf and Lücke that have no entries. It takes the largest block on the screen and carries no information.

**Acceptance criteria**
- The timeline appears only once there is something to place on it.
- Until then that space invites the first entry instead.
