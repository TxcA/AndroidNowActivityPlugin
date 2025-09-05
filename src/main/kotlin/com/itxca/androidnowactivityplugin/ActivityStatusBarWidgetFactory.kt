package com.itxca.androidnowactivityplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.Nls

class ActivityStatusBarWidgetFactory : StatusBarWidgetFactory {
    
    override fun getId(): String = ActivityStatusBarWidget.WIDGET_ID
    
    override fun getDisplayName(): String = "Android Now Activity"
    
    override fun isAvailable(project: Project): Boolean {
        // 在所有项目中显示此组件，让用户自己决定是否使用
        return true
    }
    
    override fun createWidget(project: Project): StatusBarWidget {
        return ActivityStatusBarWidget(project)
    }
    
    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }
    
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
    
    /**
     * 检查是否为Android项目
     */
    private fun isAndroidProject(project: Project): Boolean {
        return try {
            // 检查项目是否包含Android模块
            val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)
            val modules = moduleManager.modules
            
            for (module in modules) {
                // 检查模块是否为Android模块
                val facetManager = com.intellij.facet.FacetManager.getInstance(module)
                val androidFacets = facetManager.getFacetsByType(org.jetbrains.android.facet.AndroidFacet.ID)
                if (androidFacets.isNotEmpty()) {
                    return true
                }
            }
            
            // 如果没有找到Android Facet，检查是否存在Android相关文件
            val baseDir = project.baseDir
            if (baseDir != null) {
                // 检查是否存在build.gradle文件并包含Android插件
                val buildGradle = baseDir.findChild("build.gradle") ?: baseDir.findChild("build.gradle.kts")
                if (buildGradle != null && buildGradle.exists()) {
                    val content = String(buildGradle.contentsToByteArray())
                    if (content.contains("com.android.application") || 
                        content.contains("com.android.library") ||
                        content.contains("android {")) {
                        return true
                    }
                }
                
                // 检查是否存在AndroidManifest.xml
                val manifestFile = baseDir.findFileByRelativePath("src/main/AndroidManifest.xml") ?:
                                 baseDir.findFileByRelativePath("app/src/main/AndroidManifest.xml")
                if (manifestFile != null && manifestFile.exists()) {
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            // 如果检查过程中出现异常，默认显示组件
            true
        }
    }
}