#!/usr/bin/env bash
# Drives the whole card end to end against a running API: profile in Ukrainian, gap named, degree
# recognised, posting pasted, requirements matched, letter written, reviewed, exported as one PDF.
#
# Payloads are written to files and sent with --data-binary. Passing them as -d arguments loses
# non-ASCII on Windows, where argv is not UTF-8 — and this product is made of non-ASCII.
set -euo pipefail
API="${1:-http://localhost:5099}"
OUT="${2:-/tmp/bewerbo-smoke}"
mkdir -p "$OUT"

say() { printf '\n=== %s ===\n' "$1"; }
post() { curl -sS -X POST "$API$1" -H 'Content-Type: application/json' --data-binary "@$2"; }
patch() { curl -sS -X PATCH "$API$1" -H 'Content-Type: application/json' --data-binary "@$2"; }

say "1. Profile, entered in Ukrainian"
cat > "$OUT/person.json" <<'JSON'
{"inputLanguage":"uk","firstName":"Olena","lastName":"Kovalchuk",
 "street":"Lindenstraße 14","postalCode":"70173","city":"Stuttgart",
 "phone":"+49 151 2345678","email":"o.kovalchuk@example.de",
 "birthDate":null,"template":"Klassisch"}
JSON
PROFILE=$(post /api/profile "$OUT/person.json")
PID=$(echo "$PROFILE" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
echo "profile id: $PID"

cat > "$OUT/experience.json" <<'JSON'
[{"position":"Buchhalterin (Teilzeit)","employer":"Mayer & Partner GmbH","location":"Stuttgart",
  "from":"2023-09-01","to":null,"workload":"Teilzeit, 20 Std.","industry":"Steuerberatung",
  "duties":"Ведення фінансової бухгалтерії в DATEV для 14 клієнтів\nПідготовка щомісячної звітності",
  "referenceOnFile":true},
 {"position":"Leitende Buchhalterin","employer":"Agrosvit GmbH","location":"Kyjiw",
  "from":"2019-04-01","to":"2023-07-31","workload":"Vollzeit","industry":"Landwirtschaft",
  "duties":"Місячні та річні звіти підприємства зі 120 працівниками, Jahresabschlüsse nach HGB\nКонтактна особа для зовнішнього аудиту та Steuerberater",
  "referenceOnFile":true}]
JSON
patch "/api/profile/$PID/sections/berufserfahrung" "$OUT/experience.json" > /dev/null

cat > "$OUT/education.json" <<'JSON'
[{"degree":"Облік і оподаткування — Diplom","institution":"KHEU","location":"Kyjiw","country":"UA",
  "from":"2014-09-01","to":"2019-06-30",
  "anabinAssessment":"H+","germanEquivalent":"Bachelorabschluss (Rechnungswesen)",
  "equivalenceConfirmed":true}]
JSON
patch "/api/profile/$PID/sections/ausbildung" "$OUT/education.json" > /dev/null

cat > "$OUT/languages.json" <<'JSON'
[{"language":"Ukrainisch","level":"Muttersprache","certificateOnFile":false},
 {"language":"Deutsch","level":"B2","certificateOnFile":false},
 {"language":"Englisch","level":"B1","certificateOnFile":false}]
JSON
patch "/api/profile/$PID/sections/sprachen" "$OUT/languages.json" > /dev/null

cat > "$OUT/doc1.json" <<'JSON'
{"title":"Arbeitszeugnis Agrosvit GmbH","kind":"Arbeitszeugnis","note":"Übersetzt und beglaubigt","pageCount":2}
JSON
cat > "$OUT/doc2.json" <<'JSON'
{"title":"anabin-Auszug KHEU","kind":"AnabinAuszug","note":"Ausdruck vom 12.09.2026","pageCount":1}
JSON
post "/api/documents?profileId=$PID" "$OUT/doc1.json" > /dev/null
post "/api/documents?profileId=$PID" "$OUT/doc2.json" > /dev/null

say "2. Timeline — the gap between the two jobs"
curl -sS "$API/api/profile/$PID/timeline" | tee "$OUT/timeline.json"; echo

say "3. Name the gap, in Ukrainian"
cat > "$OUT/gap-request.json" <<'JSON'
{"from":"2023-07-31","to":"2023-09-01","reason":"Переїзд до Німеччини + курс німецької B2","germanWording":null}
JSON
post "/api/profile/$PID/gaps" "$OUT/gap-request.json" | tee "$OUT/gap.json"; echo

say "4. Lebenslauf as a PDF, on its own"
curl -sS -X POST "$API/api/profile/$PID/lebenslauf" -o "$OUT/lebenslauf.pdf"
ls -l "$OUT/lebenslauf.pdf"

say "5. Paste the posting"
cat > "$OUT/posting-request.json" <<JSON
{"profileId":"$PID","text":"Bilanzbuchhalter (m/w/d) — Vollzeit, Stuttgart-Vaihingen\n\nDie Schwarzwald Technik GmbH sucht Verstärkung für das Team Finanzbuchhaltung. Sie wirken an Monats- und Jahresabschlüssen nach HGB mit und sind Ansprechpartner für Steuerberater.\n\nWir erwarten sichere DATEV-Kenntnisse, Deutsch mindestens B2 und ein sicheres Auftreten. Eine geprüfte Bilanzbuchhalterin (IHK) ist von Vorteil.\n\nBitte richten Sie Ihre Unterlagen an Frau Dr. Annika Weber, Leitung Personal, unter Angabe der Referenznummer SBT-2026-0417. Eintritt zum nächstmöglichen Zeitpunkt.\n\nSchwarzwald Technik GmbH, Industriestraße 8, 70563 Stuttgart"}
JSON
POSTING=$(post /api/postings/parse "$OUT/posting-request.json")
echo "$POSTING" > "$OUT/posting.json"
POSTID=$(echo "$POSTING" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
echo "$POSTING"

say "6. Anforderungsabgleich"
curl -sS -X POST "$API/api/postings/$POSTID/match" | tee "$OUT/match.json"; echo

say "7. Anschreiben"
cat > "$OUT/application-request.json" <<JSON
{"profileId":"$PID","postingId":"$POSTID","tone":"Sachlich"}
JSON
APP=$(post /api/applications "$OUT/application-request.json")
echo "$APP" > "$OUT/application.json"
APPID=$(echo "$APP" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
echo "$APP"

say "8. Prüfung"
curl -sS -X POST "$API/api/applications/$APPID/review" | tee "$OUT/review.json"; echo

say "9. Maschinenlesbarkeit"
curl -sS -X POST "$API/api/applications/$APPID/ats-check" | tee "$OUT/ats.json"; echo

say "10. Export as ONE pdf"
curl -sS "$API/api/applications/$APPID/pdf" -o "$OUT/bewerbung.pdf" -D "$OUT/pdf-headers.txt"
grep -i 'content-disposition' "$OUT/pdf-headers.txt" || true
ls -l "$OUT/bewerbung.pdf"

say "11. Overview readiness"
curl -sS "$API/api/overview/$PID" | tee "$OUT/overview.json"; echo

printf '\nprofileId=%s\npostingId=%s\napplicationId=%s\n' "$PID" "$POSTID" "$APPID" | tee "$OUT/ids.txt"
