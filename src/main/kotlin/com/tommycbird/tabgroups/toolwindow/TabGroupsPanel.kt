package com.tommycbird.tabgroups.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.tommycbird.tabgroups.matcher.GroupMatcher
import com.tommycbird.tabgroups.matcher.ResolvedGroup
import com.tommycbird.tabgroups.settings.TabGroupsConfigurable
import com.tommycbird.tabgroups.settings.TabGroupsSettings
import com.intellij.icons.AllIcons
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

// tool window showing open files grouped, colored and collapsible; single-click opens a file
class TabGroupsPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val renderer = TabGroupsTreeRenderer(project)

    // guards the expansion listener while we expand/collapse programmatically
    private var rebuilding = false

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = renderer

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                when (val obj = node.userObject) {
                    is FileNode -> openFile(obj.file)
                    is GroupNode -> {
                        if (e.clickCount == 2) toggle(path)
                    }
                }
            }
        })

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
