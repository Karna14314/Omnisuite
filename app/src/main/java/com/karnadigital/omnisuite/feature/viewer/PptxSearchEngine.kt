package com.karnadigital.omnisuite.feature.viewer

import com.karnadigital.omnisuite.core.engine.SearchResult

/**
 * Pure, in-memory search over an already-parsed [PptxPresentation].
 *
 * Used by the PPTX viewer so that searching does not re-parse the entire
 * presentation file from disk (which is what [com.karnadigital.omnisuite.core.engine.DocumentSearchEngine.searchPptx]
 * would do).
 */
object PptxSearchEngine {

    /**
     * Searches the in-memory [presentation] for [query] case-insensitively.
     * Returns one [SearchResult] per match, scoped to its slide.
     */
    fun search(presentation: PptxPresentation, query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResult>()
        presentation.slides.forEachIndexed { slideIndex, slide ->
            // Gather all searchable text on the slide, tagging each with a label.
            val searchable = buildList {
                if (slide.title.fullText.isNotBlank()) {
                    add("title" to slide.title.fullText)
                }
                slide.textShapes.forEach { shape ->
                    if (shape.fullText.isNotBlank()) {
                        add(shape.id to shape.fullText)
                    }
                }
                if (!slide.speakerNotes.isNullOrBlank()) {
                    add("notes" to slide.speakerNotes)
                }
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
                            extraData = "$slideIndex"
                        )
                    )
                }
            }
        }
        return results
    }
}
