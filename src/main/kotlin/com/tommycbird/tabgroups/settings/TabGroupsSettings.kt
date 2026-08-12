package com.tommycbird.tabgroups.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection

// application-wide persistent config for tab groups
@Service(Service.Level.APP)
@State(name = "TabGroupsSettings", storages = [Storage("tabGroups.xml")])
class TabGroupsSettings : PersistentStateComponent<TabGroupsSettings> {

    @XCollection(propertyElementName = "groups")
    var groups: MutableList<GroupConfig> = defaultGroups()

    var miscName: String = "Misc"
    var miscColorRgb: Int = 0x9E9E9E

    // match against the full path instead of just the file name
    var matchAgainstPath: Boolean = false

    // groups the user collapsed, remembered across sessions
    @XCollection(propertyElementName = "collapsed")
    var collapsedGroups: MutableList<String> = mutableListOf()

    // urls of starred files, remembered across sessions
    @XCollection(propertyElementName = "starred")
    var starred: MutableList<String> = mutableListOf()

    override fun getState(): TabGroupsSettings = this

    override fun loadState(state: TabGroupsSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun isCollapsed(groupName: String): Boolean = collapsedGroups.contains(groupName)

    fun setCollapsed(groupName: String, collapsed: Boolean) {
        if (collapsed) {
            if (!collapsedGroups.contains(groupName)) collapsedGroups.add(groupName)
        } else {
            collapsedGroups.remove(groupName)
        }
    }

    fun isStarred(url: String): Boolean = starred.contains(url)

    fun setStarred(url: String, star: Boolean) {
        if (star) {
            if (!starred.contains(url)) starred.add(url)
        } else {
            starred.remove(url)
        }
    }

    companion object {
        fun getInstance(): TabGroupsSettings =
            ApplicationManager.getApplication().getService(TabGroupsSettings::class.java)

        // fired on the application message bus whenever the config changes
        val TOPIC: Topic<Runnable> = Topic.create("TabGroups settings changed", Runnable::class.java)

        fun notifyChanged() {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).run()
        }

        private fun defaultGroups(): MutableList<GroupConfig> = mutableListOf(
            GroupConfig("Tests", 0x4CAF50, """(?i)^test_|Test\.cpp$""", 10),
            GroupConfig("Headers", 0x2196F3, """\.(h|hpp|hxx)$""", 20),
            GroupConfig("Sources", 0xFF9800, """\.(c|cc|cpp|cxx|kt|java|py)$""", 30),
        )
    }
}
