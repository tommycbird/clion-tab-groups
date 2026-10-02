package com.tommycbird.tabgroups.toolwindow

import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.vfs.VirtualFile
import com.tommycbird.tabgroups.matcher.ResolvedGroup

// tree node for a collapsible group header
class GroupNode(val group: ResolvedGroup, val files: List<VirtualFile>) {
    val fileCount: Int get() = files.size
    override fun toString(): String = group.name
}

// tree node for an open file
class FileNode(val file: VirtualFile) {
    override fun toString(): String = file.name
}

// tree node for a native IDE line bookmark, nested under its file
class BookmarkNode(val bookmark: LineBookmark, val type: BookmarkType, val description: String?) {
    val line: Int get() = bookmark.line
    override fun toString(): String = description ?: "${bookmark.file.name}:${line + 1}"
}
