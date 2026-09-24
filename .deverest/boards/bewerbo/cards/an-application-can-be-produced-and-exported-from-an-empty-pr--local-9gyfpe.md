---
id: local-9gyfpe

title: An application can be produced and exported from an empty profile

state: Done

category: Bug

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-23T06:56:41.9918043Z

lastChangeAt: 2026-09-23T21:32:20.811395Z

lastChangedBy:
  kind: agent
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1
---

With no profile filled in, the app still writes an Anschreiben and offers it for export. The letter carries no sender address and no signature, its body only restates the advert, and it lists a Lebenslauf as an enclosure that does not exist. The export button stays active underneath a failed machine-readability check. Sending that document would cost the applicant the position.

**Acceptance criteria**
- The letter cannot be produced before the personal details and at least one work entry are present.
- The export stays unavailable while a check is failing, and names what is missing.
- Every produced letter carries the applicant's address and their name under the closing.
