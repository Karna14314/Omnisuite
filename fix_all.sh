#!/bin_bash
cd Omnisuite

# Bug 1.1, 1.2, 1.3, 2.5
cat << 'PYEOF' > fix_pptx_vm.py
import re

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerViewModel.kt", "r") as f:
    content = f.read()

content = re.sub(r'private fun getShapeAnchor\(shape: Any\): android\.graphics\.RectF\? \{\s*return try \{.*?\} catch \(e: Exception\) \{',
                 lambda m: m.group(0).replace('catch (e: Exception)', 'catch (e: Throwable)'),
                 content, flags=re.DOTALL)

def replace_parseAllSlides(match):
    return match.group(1) + """val parsedSlides = withContext(Dispatchers.IO) {
                parseAllSlides(ppt, activeFilePath!!)
            }
            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(parsedSlides),
                fileName = File(activeFilePath!!).name
            )""" + match.group(2)

content = re.sub(r'(viewModelScope\.launch \{\s*)_loadState\.value = PptxLoadState\.Success\(\s*presentation = PptxPresentation\(parseAllSlides\(ppt, activeFilePath!!\)\),\s*fileName = File\(activeFilePath!!\)\.name\s*\)(\s*\})',
                 replace_parseAllSlides, content)

def replace_inner_catches(match):
    return match.group(0).replace('catch (e: Exception)', 'catch (e: Throwable)')

content = re.sub(
    r'private fun getXmlObjectReflection\(obj: Any\): Any\? \{([\s\S]*?)catch \(e: Exception\)([\s\S]*?)catch \(e2: Exception\)([\s\S]*?)\}',
    r'private fun getXmlObjectReflection(obj: Any): Any? {\1catch (e: Throwable)\2catch (e2: Throwable)\3}',
    content
)

content = re.sub(
    r'private fun extractTextRunColorHex\(run: org\.apache\.poi\.sl\.usermodel\.TextRun\): String\? \{([\s\S]*?)catch \(e: Exception\)([\s\S]*?)\}',
    r'private fun extractTextRunColorHex(run: org.apache.poi.sl.usermodel.TextRun): String? {\1catch (e: Throwable)\2}',
    content
)

content = re.sub(
    r'private fun getSlideBgColorHex\(slide: org\.apache\.poi\.sl\.usermodel\.Slide<\*, \*>\): String\? \{([\s\S]*?)catch \(e: Exception\)([\s\S]*?)\}',
    r'private fun getSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>): String? {\1catch (e: Throwable)\2}',
    content
)

content = re.sub(
    r'val shapeHeight: Float = 0\.1f\n\)',
    r'val shapeHeight: Float = 0.1f,\n    val isSynthetic: Boolean = false\n)',
    content
)

content = re.sub(
    r'var titleBlock = PptxTextBlock\("title", "Slide \$\{index \+ 1\}", fontSizePt = 32f\)',
    r'var titleBlock = PptxTextBlock("title", "Slide ${index + 1}", fontSizePt = 32f, isSynthetic = true)',
    content
)

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerViewModel.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_pptx_vm.py

cat << 'PYEOF' > fix_pptx_screen.py
import re

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerScreen.kt", "r") as f:
    content = f.read()

def replace_bullet(match):
    before = match.group(1)
    return before + """if (block.bulletLevel > 0) {
                                Spacer(modifier = Modifier.width((block.bulletLevel * 8 * scaleFactor).dp))
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (block.fontSizePt * scaleFactor).sp
                                    ),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text("""

content = re.sub(
    r'(Row\(verticalAlignment = Alignment\.Top\)\s*\{\s*)if\s*\(block\.bulletLevel > 0\)\s*\{\s*Spacer\(modifier = Modifier\.width\(\(block\.bulletLevel \* 8 \* scaleFactor\)\.dp\)\)\s*\}\s*Text\(\s*text = "• ",\s*style = MaterialTheme\.typography\.bodyMedium\.copy\(\s*fontWeight = FontWeight\.Bold,\s*fontSize = \(block\.fontSizePt \* scaleFactor\)\.sp\s*\),\s*color = MaterialTheme\.colorScheme\.secondary\s*\)\s*Text\(',
    replace_bullet,
    content
)

content = content.replace(
    'Icon(imageVector = Icons.Default.Share, contentDescription = "Insert Image"',
    'Icon(imageVector = Icons.Default.Add, contentDescription = "Insert Image"'
)

import_str = "import androidx.compose.material.icons.filled.Add\n"
if "import androidx.compose.material.icons.filled.Add" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.ArrowBack\n", "import androidx.compose.material.icons.filled.ArrowBack\n" + import_str)

if "var currentZoomScale by remember { mutableStateOf(1f) }" not in content:
    content = content.replace(
        "val pagerState = rememberPagerState",
        "var currentZoomScale by remember { mutableStateOf(1f) }\n    val pagerState = rememberPagerState"
    )

old_column = """                    is PptxLoadState.Success -> {
                        val presentation = state.presentation
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {"""
new_column = """                    is PptxLoadState.Success -> {
                        val presentation = state.presentation
                        ZoomableBox(modifier = Modifier.fillMaxSize(), onScaleChanged = { currentZoomScale = it }) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {"""
content = content.replace(old_column, new_column)

old_closing = """                            }
                        }
                    }
                    is PptxLoadState.Error -> {"""

new_closing = """                            }
                        }
                        }
                    }
                    is PptxLoadState.Error -> {"""
content = content.replace(old_closing, new_closing)

old_pager = """                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier"""
new_pager = """                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = currentZoomScale <= 1f,
                                modifier = Modifier"""
content = content.replace(old_pager, new_pager)

old_inner_zoomable = """                                ) {
                                    ZoomableBox(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        SlideCardItem(
                                            slide = slide,"""
new_inner_zoomable = """                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        SlideCardItem(
                                            slide = slide,"""
content = content.replace(old_inner_zoomable, new_inner_zoomable)

slide_card_item_old = """            val title = slide.title
            if (title.text.isNotBlank()) {"""
slide_card_item_new = """            val title = slide.title
            if (title.text.isNotBlank() && (!title.isSynthetic || isEditMode)) {"""
content = content.replace(slide_card_item_old, slide_card_item_new)

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerScreen.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_pptx_screen.py


cat << 'PYEOF' > fix_docx_screen.py
import re

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerScreen.kt", "r") as f:
    content = f.read()

old_card_content = """                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(start = 36.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)
                                                    .clip(RoundedCornerShape(0.dp)),
                                                verticalArrangement = Arrangement.Top
                                            ) {"""
new_card_content = """                                        Box(modifier = Modifier.fillMaxSize().clipToBounds().clip(RoundedCornerShape(4.dp))) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(start = 36.dp, end = 24.dp, top = 28.dp, bottom = 28.dp),
                                                verticalArrangement = Arrangement.Top
                                            ) {"""
content = content.replace(old_card_content, new_card_content)

if "import androidx.compose.ui.draw.clipToBounds" not in content:
    content = content.replace("import androidx.compose.ui.draw.clip", "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.clipToBounds")

pagination_logic = """
                    val elements = (state as DocxLoadState.Success).document.elements
                    val docxPages = mutableListOf<List<DocxBodyElement>>()
                    var currentList = mutableListOf<DocxBodyElement>()
                    var currentLineCount = 0

                    for (element in elements) {
                        val lines = when (element) {
                            is DocxBodyElement.Para -> {
                                val para = element.paragraph
                                val hasImage = para.runs.any { it.imageUrl != null }
                                if (hasImage) 10 else {
                                    val textLength = element.paragraph.runs.sumOf { it.text.length }
                                    val baseLines = maxOf(1, textLength / 65)
                                    when {
                                        element.paragraph.headingLevel == 1 -> 4
                                        element.paragraph.headingLevel == 2 -> 3
                                        element.paragraph.headingLevel in 3..6 -> 2
                                        else -> baseLines
                                    }
                                }
                            }
                            is DocxBodyElement.Table -> {
                                element.rows.sumOf { row ->
                                    row.cells.maxOfOrNull { cell ->
                                        cell.paragraphs.sumOf { para ->
                                            val text = para.runs.joinToString("") { it.text }
                                            (text.length / 30).coerceAtLeast(1)  // narrower cols
                                        }
                                    } ?: 1
                                }
                            }
                        }
                        if (currentLineCount + lines > 55 && currentList.isNotEmpty()) {
                            docxPages.add(currentList)
                            currentList = mutableListOf(element)
                            currentLineCount = lines
                        } else {
                            currentList.add(element)
                            currentLineCount += lines
                        }
                    }
                    if (currentList.isNotEmpty()) docxPages.add(currentList)
"""

old_launched_effect = """    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
            val match = searchResults[currentMatchIndex]
            lazyListState.animateScrollToItem(match.pageIndex)
        }
    }"""

new_launched_effect = """    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
            val match = searchResults[currentMatchIndex]
            if (isPrintLayout) {
                if (state is DocxLoadState.Success) {""" + pagination_logic + """
                    val pageIndex = docxPages.indexOfFirst { page ->
                        page.any { elements.indexOf(it) == match.pageIndex }
                    }
                    if (pageIndex >= 0) lazyListState.animateScrollToItem(pageIndex)
                }
            } else {
                lazyListState.animateScrollToItem(match.pageIndex)
            }
        }
    }"""
content = content.replace(old_launched_effect, new_launched_effect)

old_toggle_print = """                            IconButton(onClick = { isPrintLayout = !isPrintLayout }) {
                                Icon(
                                    imageVector = if (isPrintLayout) Icons.Default.Print else Icons.Default.PictureAsPdf,
                                    contentDescription = "Toggle Print Layout",
                                    tint = if (isPrintLayout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }"""
new_toggle_print = """                            IconButton(onClick = {
                                isPrintLayout = !isPrintLayout
                                if (isPrintLayout) isEditMode = false
                            }) {
                                Icon(
                                    imageVector = if (isPrintLayout) Icons.Default.ViewAgenda else Icons.Default.Article,
                                    contentDescription = if (isPrintLayout) "Switch to reading mode" else "Switch to print layout",
                                    tint = if (isPrintLayout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }"""
content = content.replace(old_toggle_print, new_toggle_print)

if "import androidx.compose.material.icons.filled.Article" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Warning", "import androidx.compose.material.icons.filled.Warning\nimport androidx.compose.material.icons.filled.Article\nimport androidx.compose.material.icons.filled.ViewAgenda")

old_edit_button = """                            if (isEditMode) {
                                IconButton(onClick = { viewModel.commitChanges() }) {
                                    Icon(Icons.Default.Check, "Save Changes", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { isEditMode = !isEditMode }) {
                                Icon(
                                    Icons.Default.Edit, "Edit",
                                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }"""
new_edit_button = """                            if (!isPrintLayout) {
                                if (isEditMode) {
                                    IconButton(onClick = { viewModel.commitChanges() }) {
                                        Icon(Icons.Default.Check, "Save Changes", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { isEditMode = !isEditMode }) {
                                    Icon(
                                        Icons.Default.Edit, "Edit",
                                        tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }"""
content = content.replace(old_edit_button, new_edit_button)

old_text_style = """    val baseStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = if (paragraph.isHeading) FontWeight.Bold else FontWeight.Normal,
        fontSize = fontSizeSp.sp,
        fontFamily = fontFamily,
        color = fontColor
    )"""
new_text_style = """    val textIndent = if (paragraph.firstLineIndentDp > 0) {
        androidx.compose.ui.text.style.TextIndent(firstLine = paragraph.firstLineIndentDp.sp)
    } else {
        androidx.compose.ui.text.style.TextIndent.None
    }

    val baseStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = if (paragraph.isHeading) FontWeight.Bold else FontWeight.Normal,
        fontSize = fontSizeSp.sp,
        fontFamily = fontFamily,
        color = fontColor,
        textIndent = textIndent
    )"""
content = content.replace(old_text_style, new_text_style)

if "var currentZoomScale by remember { mutableStateOf(1f) }" not in content:
    content = content.replace("val lazyListState = rememberLazyListState()", "var currentZoomScale by remember { mutableStateOf(1f) }\n    val lazyListState = rememberLazyListState()")

old_print_zoomable = """                            ZoomableBox(
                                modifier = Modifier.fillMaxSize()
                            ) {"""
new_print_zoomable = """                            ZoomableBox(
                                modifier = Modifier.fillMaxSize(),
                                onScaleChanged = { currentZoomScale = it }
                            ) {"""
content = content.replace(old_print_zoomable, new_print_zoomable)

old_print_lazy = """                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                                ) {"""
new_print_lazy = """                                LazyColumn(
                                    state = lazyListState,
                                    userScrollEnabled = currentZoomScale <= 1f,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                                ) {"""
content = content.replace(old_print_lazy, new_print_lazy)

old_read_zoomable = """                            ZoomableBox(
                                modifier = Modifier.fillMaxSize()
                            ) {"""
new_read_zoomable = """                            ZoomableBox(
                                modifier = Modifier.fillMaxSize(),
                                onScaleChanged = { currentZoomScale = it }
                            ) {"""
content = content.replace(old_read_zoomable, new_read_zoomable)

old_read_lazy = """                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                                ) {"""
new_read_lazy = """                                LazyColumn(
                                    state = lazyListState,
                                    userScrollEnabled = currentZoomScale <= 1f,
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                                ) {"""
content = content.replace(old_read_lazy, new_read_lazy)

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerScreen.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_docx_screen.py

cat << 'PYEOF' > fix_docx_vm.py
import re

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerViewModel.kt", "r") as f:
    content = f.read()

old_legacy_heading = """            val alignment = when (paragraph.justification) {
                0 -> DocxAlignment.LEFT
                1 -> DocxAlignment.CENTER
                2 -> DocxAlignment.RIGHT
                3 -> DocxAlignment.JUSTIFY
                else -> DocxAlignment.LEFT
            }
            elements.add(
                DocxBodyElement.Para(
                    DocxParagraph(
                        runs = runs,
                        alignment = alignment,
                        headingLevel = 0,
                        isHeading = false,
                        comment = null
                    )
                )
            )"""

new_legacy_heading = """            val alignment = when (paragraph.justification) {
                0 -> DocxAlignment.LEFT
                1 -> DocxAlignment.CENTER
                2 -> DocxAlignment.RIGHT
                3 -> DocxAlignment.JUSTIFY
                else -> DocxAlignment.LEFT
            }
            val headingLevel = when (paragraph.styleIndex.toInt()) {
                1 -> 1  // Heading 1
                2 -> 2  // Heading 2
                3 -> 3  // Heading 3
                4 -> 4  // Heading 4
                5, 6 -> 5  // Heading 5/6
                else -> 0
            }
            elements.add(
                DocxBodyElement.Para(
                    DocxParagraph(
                        runs = runs,
                        alignment = alignment,
                        headingLevel = headingLevel,
                        isHeading = headingLevel > 0,
                        comment = null
                    )
                )
            )"""
content = content.replace(old_legacy_heading, new_legacy_heading)

old_style_id = """        val styleId = paragraph.styleID?.lowercase() ?: ""
        val headingLevel = when {
            styleId.contains("heading1") || styleId == "title" -> 1
            styleId.contains("heading2") -> 2
            styleId.contains("heading3") -> 3
            styleId.contains("heading4") -> 4
            styleId.contains("heading5") || styleId.contains("heading6") -> 5
            else -> 0
        }"""
new_style_id = """        val styleIdNorm = (paragraph.styleID ?: "").lowercase().replace(" ", "").replace("-", "").replace("_", "")
        val headingLevel = when {
            styleIdNorm.startsWith("heading1") || styleIdNorm == "title" || styleIdNorm == "h1" || styleIdNorm == "documenttitle" -> 1
            styleIdNorm.startsWith("heading2") || styleIdNorm == "h2" -> 2
            styleIdNorm.startsWith("heading3") || styleIdNorm == "h3" -> 3
            styleIdNorm.startsWith("heading4") || styleIdNorm == "h4" -> 4
            styleIdNorm.startsWith("heading5") || styleIdNorm.startsWith("heading6") || styleIdNorm == "h5" || styleIdNorm == "h6" -> 5
            else -> 0
        }"""
content = content.replace(old_style_id, new_style_id)

def replace_func(content, func_name, replacement_func):
    start_idx = content.find(func_name)
    if start_idx == -1: return content
    brace_count = 0
    end_idx = -1
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end_idx = i + 1
                break

    if end_idx != -1:
        func_body = content[start_idx:end_idx]
        new_func_body = replacement_func(func_body)
        content = content[:start_idx] + new_func_body + content[end_idx:]
    return content

def fix_update(body):
    body = body.replace('if (paraIndex in paragraphs.indices) {\n            val p = paragraphs[paraIndex]',
                       'if (paraIndex in paragraphs.indices) {\n            viewModelScope.launch {\n                withContext(Dispatchers.IO) {\n                    val p = paragraphs[paraIndex]')
    body = body.replace('val parsedDoc = parseDocument(doc)\n            _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)\n        }',
                       'val parsedDoc = parseDocument(doc)\n                    withContext(Dispatchers.Main) {\n                        _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)\n                    }\n                }\n            }\n        }')
    return body

def fix_insert(body):
    body = body.replace('if (paraIndex in paragraphs.indices) {\n            val p = paragraphs[paraIndex]',
                        'if (paraIndex in paragraphs.indices) {\n            viewModelScope.launch {\n                withContext(Dispatchers.IO) {\n                    val p = paragraphs[paraIndex]')
    body = body.replace('val parsedDoc = parseDocument(doc)\n            _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)\n        }',
                       'val parsedDoc = parseDocument(doc)\n                    withContext(Dispatchers.Main) {\n                        _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)\n                    }\n                }\n            }\n        }')
    return body

def fix_append(body):
    body = body.replace('val doc = activeDocument ?: return\n        val newP = doc.createParagraph()',
                        'val doc = activeDocument ?: return\n        viewModelScope.launch {\n            withContext(Dispatchers.IO) {\n                val newP = doc.createParagraph()')
    body = body.replace('val parsedDoc = parseDocument(doc)\n        _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)\n    }',
                       'val parsedDoc = parseDocument(doc)\n                withContext(Dispatchers.Main) {\n                    _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)\n                }\n            }\n        }\n    }')
    return body

content = replace_func(content, 'fun updateParagraph(', fix_update)
content = replace_func(content, 'fun insertImageIntoParagraph(', fix_insert)
content = replace_func(content, 'fun appendParagraph(', fix_append)

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerViewModel.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_docx_vm.py

cat << 'PYEOF' > fix_zoomable.py
with open("app/src/main/java/com/karnadigital/omnisuite/core/util/ZoomableBox.kt", "r") as f:
    content = f.read()

content = content.replace("offsetX = (offsetX + panChange.x * scale).coerceIn(-maxOffsetX, maxOffsetX)", "offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)")
content = content.replace("offsetY = (offsetY + panChange.y * scale).coerceIn(-maxOffsetY, maxOffsetY)", "offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)")

content = content.replace("scale = 1f\n", "scale = 1f\n                            onScaleChanged(scale)\n")
content = content.replace("scale = 2.5f\n", "scale = 2.5f\n                            onScaleChanged(scale)\n")
content = content.replace("scale = (scale * zoomChange).coerceIn(minScale, maxScale)\n", "scale = (scale * zoomChange).coerceIn(minScale, maxScale)\n                                onScaleChanged(scale)\n")

with open("app/src/main/java/com/karnadigital/omnisuite/core/util/ZoomableBox.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_zoomable.py

cat << 'PYEOF' > fix_image.py
with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/ImageViewerScreen.kt", "r") as f:
    content = f.read()

content = content.replace("offset += offsetChange * scale", "offset += offsetChange")

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/ImageViewerScreen.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_image.py

cat << 'PYEOF' > fix_xlsx.py
with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/XlsxViewerScreen.kt", "r") as f:
    content = f.read()

old_infinite = """    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.layoutInfo.totalItemsCount) {
        val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = lazyListState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 15) {"""
new_infinite = """    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.layoutInfo.totalItemsCount) {
        val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = lazyListState.layoutInfo.totalItemsCount
        if (total > 0 && lazyListState.firstVisibleItemIndex > 0 && lastVisible >= total - 15) {"""
content = content.replace(old_infinite, new_infinite)

old_scale = "    var selectedColForSort by remember { mutableStateOf<Int?>(null) }"
new_scale = """    var selectedColForSort by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(scale) {
        horizontalScrollState.scrollTo(0)
    }"""
content = content.replace(old_scale, new_scale)

with open("app/src/main/java/com/karnadigital/omnisuite/feature/viewer/XlsxViewerScreen.kt", "w") as f:
    f.write(content)
PYEOF
python3 fix_xlsx.py
