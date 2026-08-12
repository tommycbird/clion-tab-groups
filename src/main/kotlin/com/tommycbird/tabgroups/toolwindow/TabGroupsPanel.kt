package com.tommycbird.tabgroups.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.tommycbird.tabgroups.matcher.GroupMatcher
import com.tommycbird.tabgroups.matcher.ResolvedGroup
import com.tommycbird.tabgroups.settings.TabGroupsConfigurable
import com.tommycbird.tabgroups.settings.TabGroupsSettings
import com.intellij.icons.AllIcons
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

// tool window showing open files grouped, colored and collapsible; single-click opens a file
class TabGroupsPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable, DataProvider {

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val renderer = TabGroupsTreeRenderer(project)

    // whether the cursor is over the close icon of the hovered row
    private var overCloseIcon = false
    private val closeIcon get() = AllIcons.Actions.Close
    private val closeIconHovered get() = AllIcons.Actions.CloseHovered
    private val closeGutter get() = JBUI.scale(24)

    private val tree = GroupTree()

    // guards the expansion listener while we expand/collapse programmatically
    private var rebuilding = false

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = renderer

        // permanent right gutter: text clips + hover/selection stop here, close (x) lives in it
        tree.border = JBUI.Borders.emptyRight(closeGutter)

        // native hover highlight (matches selection insets/rounding)
        TreeHoverListener.DEFAULT.addTo(tree)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger || !SwingUtilities.isLeftMouseButton(e)) return
                val row = rowAt(e.x, e.y)
                if (row < 0) return
                val obj = nodeAt(row)?.userObject
                if (obj is FileNode) {
                    // clicking the hover X closes; anywhere else on the row opens
                    if (closeIconRect(row)?.contains(e.x, e.y) == true) {
                        FileEditorManager.getInstance(project).closeFile(obj.file)
                    } else {
                        openFile(obj.file)
                    }
                } else if (obj is GroupNode && e.clickCount == 2) {
                    tree.getPathForRow(row)?.let { toggle(it) }
                }
            }

            override fun mouseExited(e: MouseEvent) = clearHover()
        })

        tree.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) = updateHover(e.x, e.y)
        })

        installPopupMenu()

        tree.addTreeExpansionListener(object : javax.swing.event.TreeExpansionListener {
            override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent) =
                onExpansionChanged(event.path, expanded = true)

            override fun treeCollapsed(event: javax.swing.event.TreeExpansionEvent) =
                onExpansionChanged(event.path, expanded = false)
        })

        toolbar = buildToolbar()
        setContent(ScrollPaneFactory.createScrollPane(tree))

        subscribe()
        rebuild()
    }

    private fun buildToolbar(): javax.swing.JComponent {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Rebuild the grouped file list", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) = rebuild()
            })
            add(object : AnAction("Configure Groups", "Edit tab groups, colors and regexes", AllIcons.General.Settings) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, TabGroupsConfigurable::class.java)
                }
            })
            addSeparator()
            add(object : AnAction("Expand All", null, AllIcons.Actions.Expandall) {
                override fun actionPerformed(e: AnActionEvent) = setAllCollapsed(false)
            })
            add(object : AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
                override fun actionPerformed(e: AnActionEvent) = setAllCollapsed(true)
            })
        }
        val actionToolbar = ActionManager.getInstance().createActionToolbar("TabGroups", group, true)
        actionToolbar.targetComponent = this
        return actionToolbar.component
    }

    private fun subscribe() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) = rebuild()
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) = rebuild()
            override fun selectionChanged(event: FileEditorManagerEvent) {
                syncActiveSelection()
            }
        })

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(TabGroupsSettings.TOPIC, Runnable {
                ApplicationManager.getApplication().invokeLater({ rebuild() }, project.disposed)
            })
    }

    private fun openFile(file: VirtualFile) {
        if (file.isValid) {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    // reuse the native editor-tab right-click menu on file rows
    private fun installPopupMenu() {
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val path = tree.getPathForLocation(x, y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                if (node.userObject !is FileNode) return
                tree.selectionPath = path
                val group = ActionManager.getInstance().getAction("EditorTabPopupMenu") as? ActionGroup ?: return
                val menu = ActionManager.getInstance().createActionPopupMenu("TabGroupsPopup", group)
                menu.setTargetComponent(tree)
                menu.component.show(comp, x, y)
            }
        })
    }

    // provides the clicked file + its editor window so the tab actions operate on the right target
    override fun getData(dataId: String): Any? {
        val file = selectedFile() ?: return null
        return when {
            CommonDataKeys.PROJECT.`is`(dataId) -> project
            CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> file
            CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> arrayOf(file)
            EditorWindow.DATA_KEY.`is`(dataId) -> windowFor(file)
            else -> null
        }
    }

    private fun selectedFile(): VirtualFile? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null
        return (node.userObject as? FileNode)?.file
    }

    private fun windowFor(file: VirtualFile): EditorWindow? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        return manager.windows.firstOrNull { it.isFileOpen(file) } ?: manager.currentWindow
    }

    // tree that fills the viewport width and paints the close icon on the hovered row
    private inner class GroupTree : Tree(treeModel) {
        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val row = TreeHoverListener.getHoveredRow(this)
            closeIconRect(row)?.let { r ->
                val icon = if (overCloseIcon) closeIconHovered else closeIcon
                icon.paintIcon(this, g, r.x, r.y)
            }
        }
    }

    private fun nodeAt(row: Int): DefaultMutableTreeNode? =
        tree.getPathForRow(row)?.lastPathComponent as? DefaultMutableTreeNode

    private fun isFileRow(row: Int): Boolean = nodeAt(row)?.userObject is FileNode

    // close icon centered in the right gutter for a file row, or null
    private fun closeIconRect(row: Int): Rectangle? {
        if (row < 0 || !isFileRow(row)) return null
        val b = tree.getRowBounds(row) ?: return null
        val icon = closeIcon
        val x = tree.width - closeGutter + (closeGutter - icon.iconWidth) / 2
        val y = b.y + (b.height - icon.iconHeight) / 2
        return Rectangle(x, y, icon.iconWidth, icon.iconHeight)
    }

    private fun rowAt(x: Int, y: Int): Int {
        val row = tree.getClosestRowForLocation(x, y)
        val b = if (row >= 0) tree.getRowBounds(row) else null
        return if (b != null && y >= b.y && y < b.y + b.height) row else -1
    }

    private fun updateHover(x: Int, y: Int) {
        val row = rowAt(x, y)
        val over = row >= 0 && closeIconRect(row)?.contains(x, y) == true
        if (over != overCloseIcon) {
            overCloseIcon = over
            tree.repaint()
        }
    }

    private fun clearHover() {
        if (overCloseIcon) {
            overCloseIcon = false
            tree.repaint()
        }
    }

    private fun toggle(path: TreePath) {
        if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
    }

    private fun onExpansionChanged(path: TreePath, expanded: Boolean) {
        if (rebuilding) return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val group = node.userObject as? GroupNode ?: return
        TabGroupsSettings.getInstance().setCollapsed(group.group.name, !expanded)
    }

    private fun setAllCollapsed(collapsed: Boolean) {
        val settings = TabGroupsSettings.getInstance()
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
            val group = child.userObject as? GroupNode ?: continue
            settings.setCollapsed(group.group.name, collapsed)
        }
        rebuild()
    }

    // recompute groups from the open files and rebuild the tree
    fun rebuild() {
        overCloseIcon = false
        val settings = TabGroupsSettings.getInstance()
        val openFiles = FileEditorManager.getInstance(project).openFiles

        // bucket by display order; misc's Int.MAX_VALUE keeps it last
        val buckets = LinkedHashMap<Int, Pair<ResolvedGroup, MutableList<VirtualFile>>>()
        for (file in openFiles) {
            val resolved = GroupMatcher.resolve(file, settings)
            val entry = buckets.getOrPut(resolved.order) { resolved to mutableListOf() }
            entry.second.add(file)
        }

        val ordered = buckets.entries.sortedBy { it.key }

        rebuilding = true
        try {
            rootNode.removeAllChildren()
            val collapsedPaths = mutableListOf<TreePath>()
            for ((_, value) in ordered) {
                val (group, files) = value
                val groupNode = DefaultMutableTreeNode(GroupNode(group, files.toList()))
                for (file in files) {
                    groupNode.add(DefaultMutableTreeNode(FileNode(file)))
                }
                rootNode.add(groupNode)
                if (settings.isCollapsed(group.name)) {
                    collapsedPaths.add(TreePath(arrayOf<Any>(rootNode, groupNode)))
                }
            }
            treeModel.reload()
            TreeUtil.expandAll(tree)
            collapsedPaths.forEach { tree.collapsePath(it) }
        } finally {
            rebuilding = false
        }
        syncActiveSelection()
    }

    // highlight the active editor file; if its group is collapsed, select the header instead of expanding
    private fun syncActiveSelection() {
        val active = FileEditorManager.getInstance(project).selectedEditor?.file
        renderer.activeFile = active
        if (active == null) {
            tree.repaint()
            return
        }
        var target: TreePath? = null
        for (i in 0 until rootNode.childCount) {
            val groupNode = rootNode.getChildAt(i) as DefaultMutableTreeNode
            for (j in 0 until groupNode.childCount) {
                val fileNode = groupNode.getChildAt(j) as DefaultMutableTreeNode
                val obj = fileNode.userObject as? FileNode ?: continue
                if (obj.file == active) {
                    val groupPath = TreePath(arrayOf<Any>(rootNode, groupNode))
                    // collapsed -> select the header; else select the file row
                    target = if (tree.isCollapsed(groupPath)) {
                        groupPath
                    } else {
                        TreePath(arrayOf<Any>(rootNode, groupNode, fileNode))
                    }
                    break
                }
            }
            if (target != null) break
        }
        if (target != null) {
            tree.selectionPath = target
            tree.scrollPathToVisible(target)
        }
        tree.repaint()
    }

    override fun dispose() {
        // connections registered with `this` are cleaned up automatically
    }
}
