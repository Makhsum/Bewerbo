---
id: local-ae1w76

title: The backend becomes a full API rather than a Minimal API

state: Done

category: Refactoring

createdBy: dd4006189@gmail.com

isLocal: true

createdAt: 2026-09-23T06:57:55.7148214Z

lastChangeAt: 2026-09-23T12:41:04.7132656Z

lastChangedBy:
  kind: agent
  name: Makhsum
  email: dd4006189@gmail.com
  userId: 1
---

The backend is written as a Minimal API. The surface it has to carry is growing — profile sections, postings, matches, documents, exports, and now accounts and their legal obligations — and routing, validation, error shape and authorisation need somewhere to live that a route delegate is not.

**Requested by the user:** the backend is to be a full API rather than a Minimal API.

**Acceptance criteria**
- Every endpoint that exists today answers at the same address and with the same result as before.
- Validation failures and errors follow one shape across the whole API.
- The existing smoke run passes unchanged against the rebuilt API.
