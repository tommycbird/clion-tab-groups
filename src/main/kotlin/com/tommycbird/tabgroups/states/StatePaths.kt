package com.tommycbird.tabgroups.states

import com.intellij.openapi.vfs.VfsUtilCore

// saved states are shared across worktrees and clones of the same repo, so the
// file urls they were captured with can't be stored verbatim — a different
// worktree has the same files under a different root. instead, paths under the
// project root are stored relative to it and rebased onto the current project
// when the state is used. anything else (files outside the project, non-file
// urls) is stored as-is and simply won't resolve elsewhere.
object StatePaths {

    // url -> stored entry: relative path under the project root when possible
    fun toStored(url: String, basePath: String?): String {
        if (!url.startsWith("file://")) return url
        val path = VfsUtilCore.urlToPath(url)
        if (basePath == null || !path.startsWith("$basePath/")) return path
        return path.substring(basePath.length + 1)
    }

    // stored entry -> url for the project it's being used in
    fun toUrl(entry: String, basePath: String?): String = when {
        entry.contains("://") -> entry
        entry.startsWith("/") -> VfsUtilCore.pathToUrl(entry)
        basePath != null -> VfsUtilCore.pathToUrl("$basePath/$entry")
        else -> entry
    }

    fun relativize(state: SavedTabState, basePath: String?): SavedTabState = SavedTabState(
        state.name,
        state.files.map { toStored(it, basePath) },
        state.starred.map { toStored(it, basePath) },
        state.active?.let { toStored(it, basePath) },
    )

    fun resolve(state: SavedTabState, basePath: String?): SavedTabState = SavedTabState(
        state.name,
        state.files.map { toUrl(it, basePath) },
        state.starred.map { toUrl(it, basePath) },
        state.active?.let { toUrl(it, basePath) },
    )
}
