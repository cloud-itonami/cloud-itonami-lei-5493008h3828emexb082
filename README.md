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
`tools/verify_citations.cljk` checks every one of them live. Nothing is recorded
there that was not retrieved.

| authority | what it answers for | rows |
|---|---|---|
| GLEIF | the LEI record, its registration authority, legal form, instruments, children, relationships and managing LOU | 60 |
| KBO / BCE Public Search (FPS Economy) | enterprise number 0417.497.106 — the subject's OWN national register, in Dutch and in English | 18 |
| UK Companies House | company 11642142, a direct child GLEIF validates at RA000585 | 7 |
| Australian Business Register (ATO) | ACN 071 508 702, a direct child GLEIF validates at RA000014 | 5 |

```bash
kbb --backend sci tools/verify_citations.cljk --min 85
```

For each row the gate GETs `:cite/url`, requires HTTP 2xx, and requires
`:cite/expect-substring` in the response body. Exit codes are three, and they do
not overlap:

- **0** — answered, every citation checked, floor met
- **1** — answered, at least one citation is wrong (each is printed as `DRIFT`)
- **2** — could not answer: catalog missing, unparseable, empty, or fewer rows
  than `--min`

"Nothing was checked" and "nothing was wrong" must not share an exit code, which
is why 2 exists.

### Why the substring, and not the status code

Every one of these registers answers **HTTP 200 for a wrong identifier**
(measured 2026-08-20): KBO returns a 9,154-byte error page for 0417497107 and a
different real company for 0400378485; Companies House returns HARROP LEGAL LTD
for 11642143; the ABR returns a page reading "Invalid" for 071508703; GLEIF
returns `"total":0`. The status code discriminates nothing here. The substring
does all the work, and that is the property the gate exists to hold.

Sixteen of the ninety rows are labelled `ATTRIBUTE ROW` in their own
`:cite/claim`, because a mutation run against real peer entities — SOLVAY for
GLEIF, COLRUYT GROUP for KBO, HARROP LEGAL LTD for Companies House, MONAKA PTY
LTD for the ABR — left them standing. They say what is true of whatever entity
the URL names; the identity rows beside them are what pin which entity that is.
The catalog header records the measurement in full.

The gate is **not** a check on the archived privacy policy above. That archive is
verified separately by reproducing the recorded SHA-256 from the recorded text.
