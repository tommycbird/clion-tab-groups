package com.tommycbird.tabgroups.settings

import com.intellij.util.ui.EditableModel
import javax.swing.table.AbstractTableModel

// table model over a working copy of the group list
class GroupsTableModel(val rows: MutableList<GroupConfig>) : AbstractTableModel(), EditableModel {

    private val columns = arrayOf("Name", "Priority", "Regex", "Color")

    val colorColumn = 3

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        1 -> Integer::class.java   // priority
        3 -> Integer::class.java   // color rgb
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex != colorColumn

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val g = rows[rowIndex]
        return when (columnIndex) {
            0 -> g.name
            1 -> g.priority
            2 -> g.regex
            else -> g.colorRgb
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val g = rows[rowIndex]
        when (columnIndex) {
            0 -> g.name = value?.toString() ?: ""
            1 -> g.priority = when (value) {
                is Number -> value.toInt()
                else -> value?.toString()?.trim()?.toIntOrNull() ?: g.priority
            }
            2 -> g.regex = value?.toString() ?: ""
            3 -> g.colorRgb = (value as? Int) ?: g.colorRgb
        }
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    // --- editablemodel: drives the toolbar add/remove/up/down buttons ---

    override fun addRow() {
        val nextPriority = (rows.maxOfOrNull { it.priority } ?: 0) + 10
        rows.add(GroupConfig("New Group", 0x808080, "", nextPriority))
        val i = rows.size - 1
        fireTableRowsInserted(i, i)
    }

    override fun removeRow(index: Int) {
        if (index in rows.indices) {
            rows.removeAt(index)
            fireTableRowsDeleted(index, index)
        }
    }

    override fun exchangeRows(oldIndex: Int, newIndex: Int) {
        if (oldIndex in rows.indices && newIndex in rows.indices) {
            val item = rows.removeAt(oldIndex)
            rows.add(newIndex, item)
            fireTableDataChanged()
        }
    }

    override fun canExchangeRows(oldIndex: Int, newIndex: Int): Boolean =
        oldIndex in rows.indices && newIndex in rows.indices
}
