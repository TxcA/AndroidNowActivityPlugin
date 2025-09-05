package com.itxca.androidnowactivityplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.WindowManager

class ActivityMonitorStartupActivity : StartupActivity {
    
    override fun runActivity(project: Project) {
        try {
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            if (statusBar != null) {
                // 检查是否已经存在
                val existingWidget = statusBar.getWidget(ActivityStatusBarWidget.WIDGET_ID)
                if (existingWidget == null) {
                    val widget = ActivityStatusBarWidget(project)
                    statusBar.addWidget(widget, ActivityStatusBarWidget.WIDGET_ID)
                }
            }
        } catch (e: Exception) {
            // 忽略启动异常
        }
    }
}