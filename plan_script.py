plan_md = """
1. **Fix PptxViewerViewModel.kt**
   - Bug 1.1: In `getShapeAnchor()`, change `catch (e: Exception)` to `catch (e: Throwable)`.
   - Bug 1.2: In `updateSlideTextShape`, `insertImageIntoSlide`, `addSlide`, `deleteSlide`, `duplicateSlide`, and `setSlideBackground`, wrap `parseAllSlides(...)` calls in `withContext(Dispatchers.IO)`.
   - Bug 1.3: In `getSlideBgColorHex`, `getXmlObjectReflection`, and `extractTextRunColorHex`, change `catch (e: Exception)` to `catch (e: Throwable)` where reflection is used.
   - Bug 1.4: Skip (not applicable).
   - Bug 2.5 (ViewModel part): In `parseAllSlides` of ViewModel, set `isSynthetic = true` for fallback titles.

2. **Fix PptxViewerScreen.kt**
   - Bug 2.1: In `SlideCardItem`, wrap bullet text `•` and spacer in `if (block.bulletLevel > 0)`.
   - Bug 2.2: In `PptxTextFormatterDialog`, change "Insert Picture" icon from `Icons.Default.Share` to `Icons.Default.Add`.
   - Bug 2.3: Verify or add `@OptIn(ExperimentalFoundationApi::class)` import scope for `LazyRow` usage if necessary.
   - Bug 2.4: Move `ZoomableBox` in `PptxViewerScreen` to wrap the `HorizontalPager` instead of `SlideCardItem`. Set `userScrollEnabled` based on scale.
   - Bug 2.5: In `PptxTextBlock`, add `val isSynthetic: Boolean = false`. In `SlideCardItem`, check `!title.isSynthetic`.

3. **Fix DocxViewerScreen.kt**
   - Bug 3.1: In print layout mode, add `Modifier.clipToBounds()` to the parent Box and `Modifier.clip(RoundedCornerShape(4.dp))` to the Card's content Box.
   - Bug 3.2: In print layout mode's search jump, map `match.pageIndex` to the correct page index before scrolling.
   - Bug 3.3: Hide edit mode button if `isPrintLayout == true`, and set `isEditMode = false` when toggling print layout.
   - Bug 3.4: In `DocxParagraphItem`, apply `firstLineIndentDp` via `TextIndent`.
   - Bug 3.5: In heading detection, normalize `styleId` before comparing.
   - Bug X.1: Swap print layout icons to correctly indicate the *action* rather than the current state (`Icons.Default.Article` and `Icons.Default.ViewAgenda`).
   - Bug 5.3 (Docx part): Use `onScaleChanged` to track scale from ZoomableBox and disable scroll on `LazyColumn` when `scale > 1f`.

4. **Fix DocxViewerViewModel.kt**
   - Bug 4.1: Wrap POI document operations inside `updateParagraph`, `appendParagraph`, and `insertImageIntoParagraph` with `withContext(Dispatchers.IO)`.
   - Bug 4.2: In `parseLegacyDocument`, map `paragraph.styleIndex` (1-6) to `headingLevel`.

5. **Fix ZoomableBox.kt**
   - Bug 5.1: In pan accumulators, remove `* scale` multiplier.

6. **Fix ImageViewerScreen.kt**
   - Bug 6.1: In transform state, remove `* scale` from `offsetChange`.
   - Bug 6.2: On double tap resetting scale, also set `offset = Offset.Zero`.

7. **Fix XlsxViewerScreen.kt**
   - Bug 7.1: In infinite scroll `LaunchedEffect`, add condition `lazyListState.firstVisibleItemIndex > 0`.
   - Bug 7.2: In `ZoomableDataGrid`, observe `scale` and reset `horizontalScrollState.scrollTo(0)`.

8. **Run tests**
   - Execute `./gradlew test` to ensure changes didn't break anything.

9. **Complete pre commit steps**
   - Complete pre commit steps to ensure proper testing, verification, review, and reflection are done.

10. **Submit**
   - Commit all changes and submit.
"""
with open("plan.md", "w") as f:
    f.write(plan_md)
