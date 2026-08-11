package com.tommycbird.tabgroups.toolwindow

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
