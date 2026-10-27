package com.tommycbird.tabgroups.states

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.XCollection
import java.io.File

// one-time import of the pre-shared-storage shelf: the old TabStatesService kept
// its states in the project's .idea/workspace.xml under TabGroupsSavedStates.
// after the import the repo key is marked migrated so it never runs again.
//
// only a non-empty legacy shelf marks the key migrated — a worktree with no
// saved states must not block a sibling that still holds the data.
class LegacyStatesMigrator : ProjectActivity {

    override suspend fun execute(project: Project) {
        val key = RepoIdentity.repoKey(project)
        val store = SharedTabStatesStore.getInstance()
        if (store.isMigrated(key)) return

        val states = readLegacyStates(project) ?: return
        if (states.isEmpty()) return

        store.importLegacy(key, states)
        store.markMigrated(key)
    }

    private fun readLegacyStates(project: Project): List<SavedTabState>? {
        val basePath = project.basePath ?: return null
        val workspace = File(File(basePath, ".idea"), "workspace.xml")
        if (!workspace.isFile) return null
        try {
            val root = JDOMUtil.load(workspace.toPath())
            val component = root.getChildren("component")
                .firstOrNull { it.getAttributeValue("name") == "TabGroupsSavedStates" } ?: return null
            val shelf = XmlSerializer.deserialize(component, LegacyShelf::class.java)
            return shelf.states.map { StatePaths.relativize(expandMacros(it, basePath), basePath) }
        } catch (e: Exception) {
            LOG.warn("failed to read legacy tab states from ${workspace.path}", e)
            return null
        }
    }

    // raw workspace.xml stores urls with $PROJECT_DIR$ / $USER_HOME$ macros; the
    // platform expanded them for the old service, here we do it ourselves
    private fun expandMacros(state: SavedTabState, basePath: String): SavedTabState {
        fun expand(s: String): String = s
            .replace("\$PROJECT_DIR\$", basePath)
            .replace("\$USER_HOME\$", System.getProperty("user.home"))
        return SavedTabState(
            state.name,
            state.files.map(::expand),
            state.starred.map(::expand),
            state.active?.let(::expand),
        )
    }

    // mirrors the old service's serialized shelf shape
    class LegacyShelf {
        @XCollection(propertyElementName = "states")
        var states: MutableList<SavedTabState> = mutableListOf()
    }

    companion object {
        private val LOG = Logger.getInstance(LegacyStatesMigrator::class.java)
    }
}
