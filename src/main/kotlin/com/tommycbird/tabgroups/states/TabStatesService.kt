package com.tommycbird.tabgroups.states

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection

// a named snapshot of the open files, which of them were starred, and the active one
class SavedTabState() {
    var name: String = ""

    @XCollection(propertyElementName = "files")
    var files: MutableList<String> = mutableListOf()

    @XCollection(propertyElementName = "starred")
    var starred: MutableList<String> = mutableListOf()

    var active: String? = null

    constructor(name: String, files: List<String>, starred: List<String>, active: String?) : this() {
        this.name = name
        this.files = files.toMutableList()
        this.starred = starred.toMutableList()
        this.active = active
    }
}

// shelf of saved tab states, shared by every clone and worktree of the same repo
// (keyed by origin url, see RepoIdentity). files are stored relative to the
// project root and rebased per project, so a state saved in one worktree
// restores the matching files in another. the currently open editors are not
// shared — only the named snapshots are.
@Service(Service.Level.PROJECT)
class TabStatesService(private val project: Project) {

    private val repoKey: String by lazy { RepoIdentity.repoKey(project) }
    private val basePath: String? get() = project.basePath

    // resolved copies for this project, so consumers can treat entries as urls
    val states: List<SavedTabState>
        get() = SharedTabStatesStore.getInstance().shelf(repoKey).states.map { StatePaths.resolve(it, basePath) }

    fun find(name: String): SavedTabState? = states.firstOrNull { it.name == name }

    // overwrites in place if the name already exists, otherwise appends
    fun save(state: SavedTabState) {
        SharedTabStatesStore.getInstance().save(repoKey, StatePaths.relativize(state, basePath))
    }

    fun remove(name: String) {
        SharedTabStatesStore.getInstance().remove(repoKey, name)
    }

    companion object {
        fun getInstance(project: Project): TabStatesService = project.getService(TabStatesService::class.java)
    }
}
