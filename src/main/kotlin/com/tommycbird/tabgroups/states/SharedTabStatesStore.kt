package com.tommycbird.tabgroups.states

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection

// app-level store holding one shelf of saved tab states per repository, keyed by
// RepoIdentity so every clone/worktree of the same repo sees the same shelf.
// stored paths are relative to the project root (see StatePaths) so a state saved
// in one worktree restores the matching files in any other.
@Service(Service.Level.APP)
@State(name = "TabGroupsSharedStates", storages = [Storage("tabGroupsStates.xml")])
class SharedTabStatesStore : PersistentStateComponent<SharedTabStatesStore.Store> {

    class RepoShelf {
        var repoKey: String = ""

        @XCollection(propertyElementName = "states")
        var states: MutableList<SavedTabState> = mutableListOf()
    }

    class Store {
        @XCollection(propertyElementName = "shelves")
        var shelves: MutableList<RepoShelf> = mutableListOf()

        // repo keys whose legacy per-project shelf has already been imported
        @XCollection(propertyElementName = "migrated")
        var migrated: MutableList<String> = mutableListOf()
    }

    private var store = Store()

    override fun getState(): Store = store

    override fun loadState(state: Store) {
        store = state
    }

    // live shelf for a repo key; an unregistered one is detached until saved to
    fun shelf(key: String): RepoShelf =
        store.shelves.firstOrNull { it.repoKey == key } ?: RepoShelf()

    // upserts under the state's name, creating the shelf if needed
    fun save(key: String, state: SavedTabState) {
        val target = mutableShelf(key)
        val index = target.states.indexOfFirst { it.name == state.name }
        if (index >= 0) target.states[index] = state else target.states.add(state)
    }

    fun remove(key: String, name: String) {
        store.shelves.firstOrNull { it.repoKey == key }?.states?.removeIf { it.name == name }
    }

    // one-time import of the old per-project shelf; existing names are kept
    fun importLegacy(key: String, states: List<SavedTabState>) {
        val target = mutableShelf(key)
        for (state in states) {
            val index = target.states.indexOfFirst { it.name == state.name }
            if (index >= 0) target.states[index] = state else target.states.add(state)
        }
    }

    fun isMigrated(key: String): Boolean = store.migrated.contains(key)

    fun markMigrated(key: String) {
        if (!store.migrated.contains(key)) store.migrated.add(key)
    }

    private fun mutableShelf(key: String): RepoShelf =
        store.shelves.firstOrNull { it.repoKey == key }
            ?: RepoShelf().also { it.repoKey = key; store.shelves.add(it) }

    companion object {
        fun getInstance(): SharedTabStatesStore =
            ApplicationManager.getApplication().getService(SharedTabStatesStore::class.java)
    }
}
