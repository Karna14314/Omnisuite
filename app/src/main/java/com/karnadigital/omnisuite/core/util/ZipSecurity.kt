package com.karnadigital.omnisuite.core.util

import java.io.File

/**
 * Security helpers for safe extraction of archive (ZIP/TAR) entries.
 *
 * Prevents "Zip Slip" path traversal where a malicious entry name such as
 * `../../../../etc/passwd` would otherwise cause files to be written outside the
 * intended extraction directory.
 */
object ZipSecurity {

    /**
     * Returns true when [child] is located inside [parent] (or is equal to it),
     * based on canonical (absolute, normalized) paths.
     */
    fun isWithinDirectory(parent: File, child: File): Boolean {
        return try {
            val parentCanonical = parent.canonicalFile
            val childCanonical = child.canonicalFile
            val parentPath = parentCanonical.path + File.separator
            val childPath = childCanonical.path
            childPath == parentCanonical.path || childPath.startsWith(parentPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves a single archive entry name to a file inside [parentDir].
     *
     * The entry name is flattened to its last path segment and validated so that
     * traversal sequences (`..`) or absolute paths cannot escape [parentDir].
     * Returns `null` when the resolved location would fall outside [parentDir].
     */
    fun safeResolveEntryFile(parentDir: File, entryName: String): File? {
        val cleanName = entryName.substringAfterLast('/').substringAfterLast('\\')
        if (cleanName.isEmpty() || cleanName == "." || cleanName == "..") return null
        val resolved = File(parentDir, cleanName)
        return if (isWithinDirectory(parentDir, resolved)) resolved else null
    }
}
