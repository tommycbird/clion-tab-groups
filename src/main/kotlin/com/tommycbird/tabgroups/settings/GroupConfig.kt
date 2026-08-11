package com.tommycbird.tabgroups.settings

// no-arg ctor is required by the intellij xml serializer
class GroupConfig() {
    var name: String = "New Group"
    var colorRgb: Int = 0x808080
    var regex: String = ""
    // lower number = matched and shown first; misc is always last
    var priority: Int = 100

    constructor(name: String, colorRgb: Int, regex: String, priority: Int) : this() {
        this.name = name
        this.colorRgb = colorRgb
        this.regex = regex
        this.priority = priority
    }

    fun copy(): GroupConfig = GroupConfig(name, colorRgb, regex, priority)
}
