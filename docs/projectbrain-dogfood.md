# Project Brain Dogfood — OmniSuite Stabilization Session, 2026-10-04

An honest record of how Project Brain (PB) performed while stabilizing OmniSuite, including
where it helped, where it failed, and where plain code inspection was simply better.

**Verdict at the end of this document: Somewhat useful.**

---

## 1. Setup as actually experienced

| Step | Outcome |
|---|---|
| `pb --version` | `pb 0.0.0`, MCP protocol 2026-07-28, schema 1 |
| `pb init` (run in the previous session) | Succeeded. Project `prj_01M42M1WEB7D1XBQCZ6S7VFNN7`, 164 commits indexed |
| `pb doctor` | **17 checks: 14 pass, 3 warn, 0 fail** |
| MCP connection | Live. 8 tools advertised; check 7 spawns a real `pb mcp` child, check 8 answers `get_project_context` |
| `get_project_context` via MCP | Worked, 383 tokens, 5 sections |

The three remaining warnings are benign and documented defaults: Windows ACL inheritance for
`%LOCALAPPDATA%`, `PB_STRICT_SECRETS` off (redact rather than refuse), and no
`.projectbrainignore`.

---

## 2. Useful memories and decisions retrieved

**None at session start. Zero.** This is the single most important finding in this document.

`get_project_context` returned:

```
## Active decisions
_none recorded_
## Open tasks
_none recorded_
## Latest session summary
_none recorded_
## Open issues
_none recorded_
```

The only content was five auto-captured commit notes from the git index (`a8954c3`,
`d8cdafd4`, `c6174b2`, `2c206bd9`, `1de1959`) — all PPTX work, all generated automatically
from commit messages, none containing rationale.

**Why:** `pb init` had run roughly five minutes before this session started, so the database
was brand new and empty. PB had no history to retrieve because none had been written yet.
This is a cold-start problem, not a retrieval-algorithm problem.

### 2.1 Retrieval quality, measured at the end of the session

After writing five memories, `pb search "PPTX clampRect"` returned `3 of 3 match(es)` with the
correct note ranked first, and `pb search "OmniSuite"` returned `4 of 4` with correct types and
IDs. Retrieval precision on the terms actually used was **100% on a 4-to-6 record corpus** —
which is too small a sample to claim anything strong, but it produced **zero irrelevant results
and zero misses**. `pb status` reported `6 active, 0 candidate, 0 superseded, 0 needs review`,
and FTS integrity check 5 passed.

The honest conclusion is that PB's retrieval worked correctly on everything it was asked to
find. It simply had almost nothing to find.

### 2.1 The history that *should* have been there

OmniSuite has substantial hard-won knowledge that PB could not surface:

- A PPTX rendering rewrite was merged and then **reverted** (`1de1959` reverts `2ba0443`).
- A crash, `IllegalArgumentException: Padding must be negative`, was caused by negative PPTX
  text-box insets and fixed by clamping.
- A specific anti-pattern (hardcoded fallback geometry such as `floatArrayOf(0.05f, 0.3f, 0.6f, 0.4f)`)
  was identified, fixed, and later **regressed**.
- A five-item `LruCache` bitmap cache was identified as a crash source.

None of this was retrievable from PB. The PPTX audit that ultimately found the divergent
rect-clamping (`OfficeConverter` clamping edges vs the ViewModel clamping the rect) had to be
reconstructed from `git log`, the source, and the existing `docs/pptx-rendering-fixes.md`.

### 2.2 Where the equivalent context *did* exist

Kilo's own project memory (`.kilo` memory files and prior session digests) carried the PPTX
session history, including the revert and the negative-padding crash. Those digests were the
only reason this session started from "the PPTX rewrite was reverted and a hardcoded-fallback
anti-pattern is known" rather than from zero.

**This is the most actionable dogfood result:** PB and Kilo memory held *complementary*
information, and the critical failure mode was not a bad retrieval, it was that **the durable
knowledge was never migrated into PB.** PB cannot retrieve what was never saved to it.

---

## 3. Missing, outdated, and noisy context

### 3.1 PB tooling defect: `save_memory` reports failure but succeeds — and silently duplicates

Every `pb_save_memory` call returned an error, including a deliberate one-line probe:

```
MCP error -32602: Structured content does not match the tool's output schema:
data/duplicate_of must be string
```

Attempted three times with bodies of roughly 200, 9,000 and 8,000 characters. All three failed
identically, so at the time this was recorded as "capture is unavailable over MCP".

**That conclusion was wrong, and the correction matters more than the original claim.**

Checking `pb status` afterwards showed the writes had **persisted**. The third and fourth
attempts — the two large audit notes — were both in the database, at versions 1, despite the
tool reporting failure to the caller. PB had written the records and then failed to validate its
*own response* back to the client.

Most likely mechanism: on a save, PB computes near-duplicate detection and returns a
`duplicate_of` field. With no duplicate, it evidently emits `null`, while its own declared output
schema requires `string`. The `data/duplicate_of must be string` message is PB disagreeing with
itself about its response format, not rejecting the request.

**Two real consequences:**

1. **A write tool that reports failure on success is worse than one that fails loudly.** An agent
   that trusted the error would conclude it had saved nothing, exactly as this session did, and
   would either abandon capture or duplicate every entry on retry. That duplication actually
   happened here: the 8,000-character retry of the 9,000-character original was stored as a second
   near-identical memory. `mem_01M42NKZ88V97BMNCE96YRAHRN` was deleted once detected, which
   required reading it back — something an agent that believed the error would never have done.
2. **Near-duplicate suppression did not prevent the duplicate**, even though the configured
   threshold is 0.82 and the two bodies were near-identical. If the dedup result is what the
   malformed `duplicate_of` field is meant to carry, then the reporting bug and the dedup miss
   share a root cause.

Read-side tools (`get_project_context`, `search_memory`, `get_decisions`, `get_timeline`) were
unaffected throughout, and `pb add` over the CLI is fully functional and reported success
correctly.

**Fix direction for PB:** emit `duplicate_of` as `""` rather than `null` when there is no
duplicate, or relax the schema to `string | null`. Until then, **verify captures with
`pb status` or `pb search` instead of trusting the MCP return value** — and do not blindly retry
a "failed" `save_memory`, because it probably already succeeded.

Per the session scope rule, **no Project Brain source was modified**; this is reported only.

### 3.2 `bug_fix` requires commit provenance

The first `pb_save_memory` attempt was rejected for a different, and arguably correct, reason:

```
a bug_fix with commit_pending = false must carry at least one commit,
because that flag asserts the commit is known.
```

The `why` field of that call described fixes that had been analysed but not yet applied. PB
correctly refused to record a bug fix for work that had not happened. This is good schema
discipline, and it forced findings and fixes to be recorded separately and honestly.

### 3.3 Project naming

PB recorded the project as **"Omnisuite"** while the product is **"OmniSuite"**. Cosmetic, but
it matters for keyword retrieval: a future query for "OmniSuite" does not substring-match
"Omnisuite" case-sensitively in every code path, and a case-insensitive query may match
nothing. Set an explicit display name with `pb init --name "OmniSuite"`.

### 3.4 Noise

No noisy retrieval occurred, because no retrieval returned anything. For a cold database that
is the best possible noise outcome and the worst possible usefulness outcome.

---

## 4. Situations where ordinary code inspection was strictly better

PB was not consulted for, and would not have helped with, any of the following. Every real
defect in this session came from reading code and running the build:

| Area | Why inspection won |
|---|---|
| **Build and packaging truth** | `assembleDebug`, `assembleRelease`, `bundleRelease`, `lintRelease`, `apksigner verify`, and the Gradle test XML reports are ground truth. PB had nothing. |
| **Release signing** | Confirmed the release APK is signed with the real cert (`CN=OmniSuite`, SHA-256 `484e3061…`) and *not* the debug fallback the build script silently permits. Only the toolchain knows this. |
| **The recycled-bitmap crash** | Found by reading `LruCache` semantics against a `remember`-held Compose value. Requires knowing that `LruCache.put` trims synchronously. |
| **Blank DOCX viewer window** | Found by comparing two thresholds: producer allowed 15 MB, consumer refused >8,000,000 Base64 chars (~5.7 MB). The bug lives in the *disagreement* between two files. |
| **XLSX silent data loss** | Found by tracing `commitChanges` against the clamp applied in `parseWorkbook`. Neither half is suspicious alone. |
| **Divergent PPTX rect clamping** | Found by diffing `OfficeConverter.kt:1528/1628` against `PptxViewerViewModel.kt:302`. |
| **Backup/privacy exposure** | Found by reading `AndroidManifest.xml` and noticing no `dataExtractionRules` next to `allowBackup="true"`, then checking what lives in `filesDir/recent_files`. |
| **Disabled CI** | Found with `git status` and a directory listing, not a memory lookup. |
| **Privacy policy vs code** | Found by grepping for network APIs and reading the published policy side by side. |

PB's `get_timeline` did usefully confirm the *shape* of recent history (five PPTX commits, one of
them a revert) in a single call. That is a genuine convenience, and it is the shape of the value
PB provides: orientation, not diagnosis.

---

## 5. Concrete examples where PB saved time

Honesty requires listing these even though they are modest:

1. **Orientation in one call.** `get_project_context` immediately showed the recent commit
   sequence including `1de1959 Revert "Merge pull request #21 … feature/pptx-rendering-fixes…"`.
   Recognising that a previous attempt at this exact work was reverted changed the approach for
   the whole PPTX track: the fix this time was to unify the duplicated logic behind one shared
   helper (`PptxGeometry`) rather than to patch one call site, which is what a revert-worthy
   patch had done before.
2. **Identity and scope confirmation.** `pb doctor` checks 4, 6 and 8 confirmed the resolved
   project, the git HEAD (`a8954c3`) and a working `get_project_context` before any analysis
   began, which is a cheap guard against auditing the wrong tree.
3. **`pb index` free history.** The 164 indexed commits meant `get_timeline` could answer
   "what changed recently" without a `git log` archaeology pass.

---

## 6. Concrete examples where it failed to help

1. **Could not answer "was this bug previously identified?"** for any of the 29 defects found.
   The PPTX geometry regression, the recycled-bitmap crash, the XLSX truncation data loss, and
   the blank DOCX window were all *new* findings, but so was any answer about whether they were
   known.
2. **Could not answer "why was the current implementation chosen?"** for the hardcoded fallback
   geometry. That anti-pattern is now documented in `docs/agents.md` rule 7 and in a test
   comment, but PB holds no decision record explaining the original intent — because that
   rationale was never recorded anywhere, which is the actual problem.
3. **Capture appeared unavailable for the entire session** (§3.1). It was not — PB wrote every
   record and then reported failure. The cost was real regardless: two near-identical memories
   were created by a retry that should not have happened, and one had to be deleted afterwards.
4. **No supersession history.** The repo has a genuine supersession event — the PPTX rewrite
   replaced by a revert. PB's `supersedes` semantics are designed for exactly this and had
   nothing to show, because the replacement was never recorded.

---

## 7. Overall usefulness assessment

**Somewhat useful.**

PB was reliable for what it did this session: the install is healthy (`0 fail` on `doctor`), the
MCP connection is live, project resolution and git indexing are correct, and `get_timeline`
gave cheap orientation into the recent PPTX history including the revert. Read operations never
misled.

It could not be called useful in any strong sense, for one decisive reason: **an empty database
cannot inform a stabilisation audit.** Every substantive finding came from code inspection and
from running the build. PB was a reliable index over an empty corpus.

To PB's credit, it is now a non-empty corpus with correct retrieval, and the five records
written today — including two `decision` entries with the rationale that `git log` structurally
cannot carry — are exactly the kind of content PB is for.

### What would change the verdict to "Materially useful"

1. **Fix the `save_memory` response schema** so `duplicate_of` is emitted as `""` rather than
   `null` when there is no duplicate — or relax the schema to `string | null`. This is a
   one-line contract fix. Until it lands, an agent cannot trust the tool's return value, which
   silently corrupts capture behaviour in exactly the way observed here (§3.1).
2. **Seed the database from existing history.** `pb index` already captured 164 commits, but only
   as bare commit messages. A handful of `pb add` calls covering the PPTX revert, the
   negative-padding crash fix and the hardcoded-fallback anti-pattern would have made this
   session materially faster — those three facts are the ones a future session most needs.
3. **Adopt a capture habit at fix time, and verify it.** Every bug fixed today was written to
   `docs/agents.md` and covered by a named unit test. The same three lines, sent to
   `pb_save_memory` with a `why`, produce a searchable decision record. Confirm with
   `pb status` rather than the tool's return code, given §3.1.
4. **Set the display name explicitly** (`pb init --name "OmniSuite"`) so keyword retrieval is not
   at the mercy of the `Omnisuite` / `OmniSuite` casing difference (§3.3).

### Standing recommendation

Treat PB as a **durable decision log for things that were hard to figure out**, not as an index
over the code. The code and `git log` already record what the code *is*; PB is only worth the
maintenance cost when it records *why*, which is the part neither can reconstruct. The one
place it already earns its keep is supersession — the moment a decision is reversed, the
rationale for the reversal is otherwise lost permanently.

---

## 8. Incidental finding: the repo's own agent instructions caused a shipped crash

Not a PB finding, but discovered while auditing and worth recording here because it is exactly
the kind of durable knowledge PB should hold.

`docs/agents.md` rule 7 instructed:

> Always `bitmap.recycle()` after use. Use `WeakReference` for page caches.

Following that rule produced `PdfViewerViewModel.bitmapCache`, which overrode
`LruCache.entryRemoved` and recycled evicted bitmaps — a guaranteed
`RuntimeException: Canvas: trying to use a recycled bitmap`, because `LruCache.put` trims
synchronously and Compose was still drawing the evicted page. Rule 7 has been rewritten to state
the opposite and to explain why.
