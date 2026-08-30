package com.karnadigital.omnisuite.feature.viewer

import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.engine.document.ParsedPresentation
import com.karnadigital.omnisuite.core.engine.document.TextContent

/**
 * Pure, in-memory search over an already-parsed [ParsedPresentation].
 */
object PptxSearchEngine {

    fun search(presentation: ParsedPresentation, query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResult>()
        presentation.slides.forEachIndexed { slideIndex, slide ->
            val searchable = buildList {
                slide.shapes.forEachIndexed { shapeIndex, shape ->
                    if (shape.content is TextContent) {
                        val text = (shape.content as TextContent).paragraphs
                            .joinToString(" ") { p -> p.runs.joinToString("") { it.text } }
                        if (text.isNotBlank()) add("shape $shapeIndex" to text)
                    }
                }
                if (!slide.speakerNotes.isNullOrBlank()) add("notes" to slide.speakerNotes)
            }
            searchable.forEach { (label, text) ->
                for (pos in com.karnadigital.omnisuite.core.util.TextSearchUtils.findAllMatchIndices(text, query)) {
                    val start = maxOf(0, pos - 25)
                    val end = minOf(text.length, pos + query.length + 25)
                    val snippet = (if (start > 0) "..." else "") +
                        text.substring(start, end).replace('\n', ' ').trim() +
                        (if (end < text.length) "..." else "")
                    results.add(
                        SearchResult(
                            pageIndex = slideIndex,
                            textSnippet = "Slide ${slideIndex + 1} [$label]: $snippet",
                            extraData = "$slideIndex",
                        )
                    )
                }
            }
        }
        return results
    }
}
