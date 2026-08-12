package com.tommycbird.tabgroups.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.RowIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import com.tommycbird.tabgroups.settings.TabGroupsSettings
import java.awt.Color
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

// group headers get a swatch (or star) + name + count; file rows get a star toggle + file icon
class TabGroupsTreeRenderer(private val project: Project) : ColoredTreeCellRenderer() {

    // file currently focused in the editor; set by the panel
    var activeFile: VirtualFile? = null

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = value as? DefaultMutableTreeNode ?: return
        when (val obj = node.userObject) {
            is GroupNode -> {
                val color = safeColor(obj.group.colorRgb)
                icon = if (obj.group.isStarred) AllIcons.Nodes.Favorite else ColorIcon(12, color)
                append(
                    obj.group.name,
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color),
                )
                append("  ${obj.fileCount}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

                // collapsed group holding the active file shows it on the header
                val active = activeFile
                if (!expanded && active != null && obj.files.any { it == active }) {
                    append("   \u25B8 ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(active.presentableName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
            }

            is FileNode -> {
                val file = obj.file
                val fileIcon = try {
                    file.fileType.icon
                } catch (_: Exception) {
                    EmptyIcon.ICON_16
                }
                val starred = TabGroupsSettings.getInstance().isStarred(file.url)
                val hovered = TreeHoverListener.getHoveredRow(tree) == row
                val fav = AllIcons.Nodes.Favorite
                // star shows when starred (filled) or on hover (outline); blank slot otherwise so nothing shifts
                val leading = when {
                    starred -> fav
                    hovered -> AllIcons.Nodes.NotFavoriteOnHover
                    else -> EmptyIcon.create(fav.iconWidth, fav.iconHeight)
                }
                icon = RowIcon(leading, fileIcon)
                val active = file == activeFile
                val attrs = if (active) {
                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(file.presentableName, attrs)
                toolTipText = file.path
            }

            else -> {
                append(obj?.toString() ?: "")
            }
        }
    }

    private fun safeColor(rgb: Int): Color = try {
        val base = Color(rgb, false)
        JBColor(base, base)
    } catch (_: Exception) {
        UIUtil.getLabelForeground()
    }
}
