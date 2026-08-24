package com.karnadigital.omnisuite.core.util

/**
 * Pure, testable text-search helpers used by [com.karnadigital.omnisuite.core.engine.DocumentSearchEngine].
 */
object TextSearchUtils {

    /**
     * Returns the starting index of every (non-overlapping) occurrence of [query] within [text].
     *
     * Overlapping matches are deliberately avoided so that, for example, searching "aa" inside
     * "aaa" yields indices [0, 1] (two matches) rather than [0, 1, 2].
     */
    fun findAllMatchIndices(text: String, query: String, ignoreCase: Boolean = true): List<Int> {
        if (query.isBlank()) return emptyList()
        val indices = mutableListOf<Int>()
        var pos = text.indexOf(query, 0, ignoreCase)
        while (pos >= 0) {
            indices.add(pos)
            pos = text.indexOf(query, pos + query.length, ignoreCase)
        }
        return indices
    }
}
