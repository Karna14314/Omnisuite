# Cloud Agent Prompt — PPTX Rendering Fidelity Fixes

> **How to use:** clone the repo, then paste everything between the two
> `---` rules into your agent. The agent must read
> `docs/pptx-rendering-fixes.md` in full before touching code — that file is the
> authoritative spec and contains verified Apache POI 5.2.5 API facts that took
> real effort to establish.

---

## PROMPT BEGINS

You are working in the Android repo at the repo root (an offline-first Android
document suite called **OmniSuite** — Compose + Hilt + Room, single app module
`app`, package `com.karnadigital.omnisuite`).

Your task: fix the PPTX viewer's rendering fidelity, in four phases, verifying
with unit tests after each phase. Work autonomously. Do not stop to ask for
approval on routine decisions; make the call, follow the existing code style, and
record what you decided.

### STEP 0 — Read the spec

Read `docs/pptx-rendering-fixes.md` **completely** before writing any code. It
contains:

- §1 environment + exact build/test/lint commands
- §3 architecture map with file paths and line numbers
- §4 **verified Apache POI 5.2.5 API facts** (confirmed via `javap` against the
  actual jars — trust these, do not re-derive, they explain the bugs)
- §5 symptom → cause map from a real visual diff
- §6/§7/§8/§9 the four phases, each with exact call sites, line numbers, what to
  change, and the specific unit tests to write
- §10 definition of done, §11 style constraints

### STEP 1 — Baseline

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected: **BUILD SUCCESSFUL, 45 tests, 0 failures.** This was just fixed and
pushed; the suite previously did not compile at all (`updateShapeTextSync` is a
`suspend fun` called from a non-suspend test). If you see 45 green, you have a
correct baseline. If not, fix that first before doing anything else.

Ignore the pre-existing harmless noise: the "SDK processing … up to version 3"
warning, the `dagger.*` kapt "options not recognized" warning, and the Gradle 9
deprecation notice.

Do **not** run `assembleRelease` / `bundleRelease` — R8 needs 6 GB and is slow.
`compileDebugKotlin` + `testDebugUnitTest` + `lintDebug` is the verify gate.

### STEP 2 — Execute the four phases in order

**Phase 1 — Text metrics.** The single highest-impact fix. OOXML run properties
inherit through a 7-level chain (run `rPr` → paragraph `defRPr` → shape
`lstStyle` → layout placeholder `lstStyle` → master `txStyles` → presentation
`defaultTextStyle` → built-in default). POI's `XSLFTextRun.getFontSize()`
returns **null** whenever the run has no explicit `sz`, which is the norm for
template placeholder text — so the parser currently substitutes hardcoded
14/18/24/26 pt constants and every slide renders with grossly oversized,
overflowing text. Implement the chain, resolve `+mj-lt`/`+mn-lt` against the
theme's `<a:fontScheme>`, and delete the constants except as a last resort. Also
in this phase: `<a:bodyPr anchor>` vertical alignment, bullet fidelity
(`buNone`/`buChar`/`buFont`/`buClr`/`buSzPct`), `JUSTIFY` → `Start`, and the
autofit shrink floor.

**Phase 2 — Geometry.** Two defects: (a) the parser **invents** positions
(`0.05, 0.3, 0.6, 0.4` etc.) whenever bounds can't be resolved, dumping shapes
into a fake column — replace with skip-and-log; (b) a shape whose `x` clamps to
`1.0` collapses to a 5 %-wide sliver because of
`shapeWidthVal.coerceAtMost(1f - shapeLeft)` — clamp the rect coherently
instead. Then fix table geometry, which is currently **always** broken:
`extractTableGeometry` calls `getTbl()` on a `CTGraphicalObjectFrame`, which has
no such method, so it always returns `null` and every table gets equal-width
columns and equal-height rows regardless of the file. Use the public
`XSLFTable.getCTTable()`. Also honour cell borders, cell alignment, cell fonts
and merged cells.

**Phase 3 — Images & background.** Resolve image relationships against the
**owning** part (layout/master pictures currently resolve against the slide and
silently vanish). Parse `<a:alphaModFix>` (this is why the template's watermark
renders opaque instead of faint). Stop destructively baking `srcRect` into the
bitmap; carry it and apply at draw time. Replace the 32-bit `contentHashCode()`
cache key with SHA-1. Use `ContentScale.FillBounds` for true `<p:pic>` (PowerPoint
stretches; Compose letterboxes). Stop forcing background pictures to
`0,0,1,1` + `FillBounds`, and stop **discarding** a full-bleed `<p:pic>` when a
background already exists. Add `rotationDegrees`/`flipH`/`flipV` to `PptxImage`.
Stop serialising XML 4× per shape for regex scanning.

**Phase 4 — Architecture.** Extract the parser out of the 3650-line ViewModel
into a standalone `PptxParser`; this is what makes the rest testable, so do it
**first** in this phase, then rewrite the test classes to drop all
`sun.misc.Unsafe` reflection. Deduplicate the geometry helpers that currently
exist twice (`PptxViewerViewModel` and `OfficeConverter`). Make
`OfficeConverter` consume the same parsed model so the GRID/slideshow bitmap
path stops dropping tables that the scroll view shows. Add chart support and a
SmartArt placeholder. Log the remaining known gaps rather than guessing.

### STEP 3 — Verify after every phase

Each phase in the spec ends with a numbered list of required unit tests. Write
them, then:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*Pptx*" --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:compileDebugKotlin --console=plain
.\gradlew.bat :app:lintDebug --console=plain
```

The test count must only ever go **up**, and never regress below 45 green.

Note on unit tests: `testOptions { unitTests.isReturnDefaultValues = true }` is
set, so `android.util.Log` and `android.graphics.*` return defaults rather than
throwing. Use POI's real XMLBeans API (`getXmlObject()`, `CTShape`, `CTTextBody`,
`CTTextBodyProperties`, `CTTextParagraph`, `CTTable`, …) to build fixtures rather
than string-munging. `PptxTextLayoutParserUnitTest` and `PptxRendererUnitTest`
both contain a working `Unsafe.allocateInstance` + reflective-invoke pattern for
reaching `private fun parseAllSlides` — mirror it in new test classes, and
replace it wholesale in Phase 4.

### STEP 4 — Report

When done, report:
1. Test count before → after, and confirmation nothing regressed.
2. Per phase: what changed, which spec bullets are now covered by tests.
3. Anything in the spec you could **not** do, and why.
4. Anything you found that the spec got wrong.

Tick items off in `docs/pptx-rendering-fixes.md` as each phase lands, and update
`docs/agents.md` (PowerPoint viewer row + quick-fix index) and `docs/engines.md`
(`OfficeConverter` contract) per §10.

### Constraints

- Minimal diffs. No speculative features beyond spec §9.4.
- Match surrounding style: 4-space indent, `private fun` helpers, KDoc on
  non-obvious functions (that is the existing convention in these files).
- Keep the existing `try { … } catch (_: Throwable)` tolerance for malformed
  files — but stop letting it silently swallow reachable data. `Log.d` wherever
  a shape is dropped.
- Do not commit `*.jks`, `keystore.properties`, `play-store-key.json`.
- Do not bump versions, touch CI, or change signing config.

## PROMPT ENDS
