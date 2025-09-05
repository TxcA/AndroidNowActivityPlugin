package com.itxca.androidnowactivityplugin

import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.JPanel
import javax.swing.border.Border

class RoundedPanel(private val cornerRadius: Int = 10) : JPanel() {
    
    init {
        isOpaque = false
    }
    
    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // 使用IDE主题自适应背景色
        val backgroundColor = JBColor.namedColor("Panel.background", 
            JBColor(Color.WHITE, Color(60, 63, 65)))
        
        g2.color = backgroundColor
        g2.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
        
        super.paintComponent(g)
    }
    
    override fun paintBorder(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // 使用IDE主题自适应边框色
        val borderColor = JBColor.namedColor("Component.borderColor", 
            JBColor(Color(200, 200, 200), Color(85, 85, 85)))
        
        g2.color = borderColor
        g2.stroke = BasicStroke(1f)
        g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    }
    
    override fun contains(x: Int, y: Int): Boolean {
        // 确保点击检测也遵循圆角形状
        val shape = java.awt.geom.RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius.toFloat(), cornerRadius.toFloat())
        return shape.contains(x.toDouble(), y.toDouble())
    }
}