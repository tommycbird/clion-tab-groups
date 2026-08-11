package com.tommycbird.tabgroups.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorChooser
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class TabGroupsConfigurable : Configurable {

    private var rootPanel: JPanel? = null
    private val working = mutableListOf<GroupConfig>()
    private lateinit var model: GroupsTableModel
    private lateinit var table: JBTable
    private lateinit var miscNameField: JBTextField
    private lateinit var miscColorButton: JButton
    private lateinit var matchPathCheck: JBCheckBox
    private var miscColor: Int = 0x9E9E9E

    override fun getDisplayName(): String = "Tab Groups"

    override fun createComponent(): JComponent {
        model = GroupsTableModel(working)
        table = JBTable(model)
        table.setShowGrid(false)
        table.rowHeight = JBUI.scale(24)
        val colorCol = model.colorColumn
        table.columnModel.getColumn(colorCol).cellRenderer = colorCellRenderer()
        table.columnModel.getColumn(colorCol).maxWidth = JBUI.scale(110)
        table.columnModel.getColumn(1).maxWidth = JBUI.scale(70) // priority

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)
                if (col == colorCol && row >= 0) {
                    val current = Color(model.getValueAt(row, colorCol) as Int)
                    val chosen = ColorChooser.chooseColor(table, "Choose Group Color", current)
                    if (chosen != null) {
                        model.setValueAt(chosen.rgb and 0xFFFFFF, row, colorCol)
                    }
                }
            }
        })

        val tablePanel = ToolbarDecorator.createDecorator(table).createPanel()
        tablePanel.preferredSize = Dimension(JBUI.scale(520), JBUI.scale(220))

        miscNameField = JBTextField()
        miscColorButton = JButton().apply {
            addActionListener {
                val chosen = ColorChooser.chooseColor(this, "Choose Misc Color", Color(miscColor))
                if (chosen != null) {
                    miscColor = chosen.rgb and 0xFFFFFF
                    updateMiscButton()
                }
            }
        }
        matchPathCheck = JBCheckBox("Match regex against full file path (instead of just the file name)")

        val help = JBLabel(
            "<html>Files are matched by <b>Priority</b> (lowest number first). The first group whose<br>" +
                "regex matches wins; that order is also the display order. <b>Misc</b> is always last.</html>"
        ).apply { border = JBUI.Borders.emptyBottom(8) }

        val bottom = FormBuilder.createFormBuilder()
            .addComponent(matchPathCheck)
            .addLabeledComponent("Misc group name:", miscNameField)
            .addLabeledComponent("Misc group color:", miscColorButton)
            .panel
            .apply { border = JBUI.Borders.emptyTop(10) }

        rootPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(help, BorderLayout.NORTH)
            add(tablePanel, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }

        reset()
        return rootPanel!!
    }

    private fun colorCellRenderer(): DefaultTableCellRenderer = object : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            t: JTable, value: Any?, selected: Boolean, focus: Boolean, row: Int, col: Int,
        ): Component {
            val c = super.getTableCellRendererComponent(t, value, selected, focus, row, col)
            val rgb = (value as? Int) ?: 0
            val color = Color(rgb)
            background = color
            foreground = contrastColor(color)
            text = String.format("#%06X", rgb and 0xFFFFFF)
            horizontalAlignment = CENTER
            return c
        }
    }

    private fun contrastColor(c: Color): Color {
        val luminance = (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) / 255.0
        return if (luminance > 0.6) JBColor.BLACK else JBColor.WHITE
    }

    private fun updateMiscButton() {
        val color = Color(miscColor)
        miscColorButton.text = String.format("#%06X", miscColor and 0xFFFFFF)
        miscColorButton.background = color
        miscColorButton.foreground = contrastColor(color)
        miscColorButton.isOpaque = true
    }

    override fun isModified(): Boolean {
        val s = TabGroupsSettings.getInstance()
        if (matchPathCheck.isSelected != s.matchAgainstPath) return true
        if (miscNameField.text != s.miscName) return true
        if (miscColor != s.miscColorRgb) return true
        if (working.size != s.groups.size) return true
        for (i in working.indices) {
            val a = working[i]
            val b = s.groups[i]
            if (a.name != b.name || a.regex != b.regex || a.colorRgb != b.colorRgb || a.priority != b.priority) return true
        }
        return false
    }

    override fun apply() {
        val s = TabGroupsSettings.getInstance()
        s.groups = working.map { it.copy() }.toMutableList()
        s.miscName = miscNameField.text.ifBlank { "Misc" }
        s.miscColorRgb = miscColor
        s.matchAgainstPath = matchPathCheck.isSelected
        TabGroupsSettings.notifyChanged()
    }

    override fun reset() {
        val s = TabGroupsSettings.getInstance()
        working.clear()
        s.groups.forEach { working.add(it.copy()) }
        model.fireTableDataChanged()
        miscNameField.text = s.miscName
        miscColor = s.miscColorRgb
        updateMiscButton()
        matchPathCheck.isSelected = s.matchAgainstPath
    }

    override fun disposeUIResources() {
        rootPanel = null
    }
}
