package com.karnadigital.omnisuite.core.util

/**
 * Pure, testable helpers for validating URI schemes against the strict offline-only policy.
 */
object UriSchemeUtils {

    /**
     * Returns true when the given URI [scheme] is allowed for offline-only caching.
     *
     * OmniSuite is strictly offline: only `content://` and `file://` schemes (and a null scheme
     * treated as a bare file path) are permitted. Network schemes such as `http`/`https` are rejected.
     */
    fun isOfflineScheme(scheme: String?): Boolean {
        return scheme == null || scheme == "file" || scheme == "content"
    }
}
