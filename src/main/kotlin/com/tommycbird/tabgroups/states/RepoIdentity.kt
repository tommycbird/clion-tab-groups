package com.tommycbird.tabgroups.states

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

// identifies the git repository a project belongs to, so clones and worktrees of
// the same repo can share their saved tab states. linked worktrees point back at
// the main checkout's .git dir; separate clones are tied together by origin url.
object RepoIdentity {

    private val LOG = Logger.getInstance(RepoIdentity::class.java)

    // stable key for the repo this project lives in: the normalized origin url
    // (shared by every clone and worktree of the repo), else the shared git dir
    // (same clone but no remote), else the project dir (not a repo — keeps the
    // old per-project behaviour).
    fun repoKey(project: Project): String {
        val basePath = project.basePath ?: return "project:${project.name}"
        val git = findGit(File(basePath)) ?: return "project:$basePath"
        val commonDir = commonGitDir(git) ?: return "project:$basePath"
        val origin = readOriginUrl(commonDir)
        return if (origin != null) "repo:${normalize(origin)}" else "local:${commonDir.absolutePath}"
    }

    // walk up from the project dir looking for .git
    private fun findGit(dir: File): File? {
        var current = dir
        while (true) {
            val git = File(current, ".git")
            if (git.exists()) return git
            current = current.parentFile ?: return null
        }
    }

    // the main checkout's .git dir. a linked worktree stores it in a ".git" file
    // like "gitdir: /main/.git/worktrees/<name>", so the common dir is two levels up.
    private fun commonGitDir(git: File): File? {
        if (git.isDirectory) return git
        val target = runCatching {
            git.readLines().firstOrNull { it.startsWith("gitdir:") }
                ?.removePrefix("gitdir:")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { raw -> if (File(raw).isAbsolute) File(raw) else File(git.parentFile, raw) }
        }.getOrNull() ?: return null
        return target.parentFile?.parentFile
    }

    // pull remote "origin" url out of the repo config (simple INI scan)
    private fun readOriginUrl(commonDir: File): String? {
        val config = File(commonDir, "config")
        if (!config.isFile) return null
        try {
            var inOrigin = false
            for (raw in config.readLines()) {
                val line = raw.trim()
                if (line.startsWith("[")) {
                    inOrigin = line == "[remote \"origin\"]"
                } else if (inOrigin && line.startsWith("url")) {
                    val value = line.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) return value
                }
            }
        } catch (e: Exception) {
            LOG.warn("failed to read ${config.path}", e)
        }
        return null
    }

    // strip protocol, credentials and trailing ".git" so https://host/x.git,
    // git@host:x.git and ssh://git@host/x all map to the same key
    private fun normalize(url: String): String {
        var u = url.trim()
        val scheme = u.indexOf("://")
        if (scheme >= 0) {
            u = u.substring(scheme + 3)
            val at = u.indexOf('@')
            if (at >= 0 && at < u.indexOf('/')) u = u.substring(at + 1)
        } else if (u.contains(':')) {
            // scp-style "git@host:path"
            val at = u.indexOf('@')
            if (at >= 0) u = u.substring(at + 1)
            u = u.replace(':', '/')
        }
        while (u.endsWith(".git")) u = u.removeSuffix(".git")
        while (u.endsWith("/")) u = u.removeSuffix("/")
        return u
    }
}
