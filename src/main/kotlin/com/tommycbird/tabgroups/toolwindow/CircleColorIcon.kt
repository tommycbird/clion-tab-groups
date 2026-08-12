package com.tommycbird.tabgroups.toolwindow

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

// filled circle swatch for normal groups (square is kept for misc)
class CircleColorIcon(unscaledSize: Int, private val color: Color) : Icon {
    private val size = JBUI.scale(unscaledSize)

    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.fillOval(x, y, size, size)
        g2.dispose()
    }
}
