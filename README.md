# cloud-itonami-lei-5493008h3828emexb082

> **Independent third-party archive/analysis. Not affiliated with, endorsed by, or sponsored by ANHEUSER-BUSCH INBEV.**

This repository archives the publicly published Privacy Policy of **ANHEUSER-BUSCH INBEV** (BE), with source-url and retrieval-date provenance, per
ADR-2607110300 (`cloud-itonami-lei-corporate-tos-catalog`, `com-junkawasaki/root`).
Read-only reference/archive repository — not a governed Advisor/Governor actor.

- LEI: `5493008H3828EMEXB082` (GLEIF entity status ACTIVE, registration ISSUED)
- Source: https://www.ab-inbev.com/privacy-policy
- Retrieved: 2026-07-25T07:01:30Z
- SHA-256 of archived text: `2b822976e7f5e5fe2fde47885acb4d15db56b33cc1a12532cfd8016937be30c8`

Acquired by `scripts/lei-acquire.cljs` as part of the worldwide-broadening
continuation that followed the 2026-07-25 coverage audit, which found the
catalog's real reach was 27 countries with the United States at 55%.

## Verified public-register citations

`facts/catalog.edn` holds 90 citations to four independent public registers, and
`tools/verify_citations.cljs` checks every one of them live. Nothing is recorded
there that was not retrieved.

| authority | what it answers for | rows |
|---|---|---|
| GLEIF | the LEI record, its registration authority, legal form, instruments, children, relationships and managing LOU | 60 |
| KBO / BCE Public Search (FPS Economy) | enterprise number 0417.497.106 — the subject's OWN national register, in Dutch and in English | 18 |
| UK Companies House | company 11642142, a direct child GLEIF validates at RA000585 | 7 |
| Australian Business Register (ATO) | ACN 071 508 702, a direct child GLEIF validates at RA000014 | 5 |

```bash
nbb tools/verify_citations.cljs --min 85
```

Each **distinct URL** is fetched once — twenty requests for ninety rows — and
every row naming that URL is checked against that one body: HTTP 2xx, and
`:cite/expect-substring` present. A run takes about 17 seconds.

Exit codes are three, and they do not overlap:

- **0** — answered, every citation checked, floor met
- **1** — answered, at least one citation is wrong (each is printed as `DRIFT`)
- **2** — could not answer: catalog missing, unparseable, empty, fewer rows than
  `--min`, a URL that could not be fetched at all (printed as `UNREACHABLE`,
  with the underlying error), or a register that served a challenge instead of
  a record (printed as `BLOCKED`)

Both refusals are decided **before** any row is judged, so a `DRIFT` always
means the body was read and did not carry the claim.

"Nothing was checked" and "nothing was wrong" must not share an exit code, which
is why 2 exists. Neither may "the register refused to answer me" and "the
register no longer carries this claim" — see below.

### If you see BLOCKED

`kbopub.economie.fgov.be` answers a burst of repeated lookups with a 302 to
`captchaform.html`. That page states that consultation of Public Search "may
only take place enterprise by enterprise", under article 2 of the Royal Decree
of 28 March 2014 implementing article III.31 of the Code of Economic Law. It is
the register telling automated clients to stop, and this gate does not work
around it: it reports `BLOCKED`, exits 2, and judges nothing.

That refusal is the whole point. Before the gate deduplicated its fetches it
issued eighteen GETs for the two KBO pages, provoked the challenge on roughly
one request in eight, and then reported the affected rows as
`DRIFT ... missing substring` — announcing that a national register had dropped
a fact, when what had actually happened is that it had declined to answer. A
check that cannot tell "measured and wrong" from "could not measure" is worse
than no check. If you get `BLOCKED`, wait and re-run; do not add credentials,
headers or retries to get past it.

### Why the substring, and not the status code

Every one of these registers answers **HTTP 200 for a wrong identifier**
(measured 2026-08-20): KBO returns a 9,154-byte error page for 0417497107 and a
different real company for 0400378485; Companies House returns HARROP LEGAL LTD
for 11642143; the ABR returns a page reading "Invalid" for 071508703; GLEIF
returns `"total":0`. The status code discriminates nothing here. The substring
does all the work, and that is the property the gate exists to hold.

Sixteen of the ninety rows are labelled `ATTRIBUTE ROW` in their own
`:cite/claim`, because pointing them at a real peer entity leaves them standing.
Three of the four blocks were measured by repointing the catalog and re-running
the gate — SOLVAY for GLEIF, COLRUYT GROUP for KBO, HARROP LEGAL LTD for
Companies House. The ABR block was measured by fetching MONAKA PTY LTD
(ACN 000 000 019) and comparing its page against the five substrings directly,
not by a gate run. Attribute rows say what is true of whatever entity the URL
names; the identity rows beside them are what pin which entity that is. The
catalog header records the measurement in full.

## The archived document is checked separately

The citation gate says nothing about the privacy policy above. That capture has
its own check, and it asks one question only — does the recorded text still hash
to the recorded digest:

```bash
nbb tools/verify_archive.cljs
```

Exit 0 the digest reproduces, 1 it does not, 2 the check could not be made
(journal missing, unparseable, or a required field absent).

It is **not** a network check and must not be read as one. It does not say the
source still serves that document, and it does not say the document is what
`:tos/doc-type` claims: a sibling repository in this fleet holds a capture whose
digest reproduces perfectly and which is a 404 page. As of 2026-08-20 this
repository's capture reproduces, carries no 404 marker, and its source URL still
answers HTTP 200 with a privacy policy — but only the first of those three is
what the tool checks.
