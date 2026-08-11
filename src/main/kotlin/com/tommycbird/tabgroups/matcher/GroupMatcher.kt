package com.tommycbird.tabgroups.matcher

import com.intellij.openapi.vfs.VirtualFile
import com.tommycbird.tabgroups.settings.TabGroupsSettings

// the group a file resolved into, ready for display
data class ResolvedGroup(
    val name: String,
    val colorRgb: Int,
    val isMisc: Boolean,
    // display order; misc sorts last
    val order: Int,
)

// resolves files into groups, first-match-wins, with cached compiled regexes
object GroupMatcher {

    private data class CacheEntry(val source: String, val regex: Regex?)

    private val cache = HashMap<String, CacheEntry>()

    @Synchronized
    private fun compile(pattern: String): Regex? {
        val cached = cache[pattern]
        if (cached != null && cached.source == pattern) return cached.regex
        val compiled = try {
            if (pattern.isBlank()) null else Regex(pattern)
        } catch (_: Exception) {
            null // invalid regex never matches
        }
        cache[pattern] = CacheEntry(pattern, compiled)
        return compiled
    }

    fun resolve(file: VirtualFile, settings: TabGroupsSettings): ResolvedGroup {
        val target = if (settings.matchAgainstPath) file.path else file.name
        // evaluate by priority, lower first, tie-broken by list position
        val sorted = settings.groups
            .withIndex()
            .sortedWith(compareBy({ it.value.priority }, { it.index }))
        sorted.forEachIndexed { position, indexed ->
            val group = indexed.value
            val regex = compile(group.regex)
            if (regex != null && regex.containsMatchIn(target)) {
                return ResolvedGroup(group.name, group.colorRgb, isMisc = false, order = position)
            }
        }
        return ResolvedGroup(
            name = settings.miscName,
            colorRgb = settings.miscColorRgb,
            isMisc = true,
            order = Int.MAX_VALUE,
        )
    }
}
