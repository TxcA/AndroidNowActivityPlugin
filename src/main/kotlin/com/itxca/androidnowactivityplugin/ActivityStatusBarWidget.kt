package com.itxca.androidnowactivityplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget

import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JPopupMenu
import javax.swing.JMenuItem
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ActivityStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    
    companion object {
        const val WIDGET_ID = "AndroidNowActivity"
    }
    
    private var currentActivity: String = "No Activity"
    private var currentDevice: String? = null
    private var connectedDevices: List<String> = emptyList()
    private var adbPath: String? = null
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val settings = ActivityMonitorSettings.getInstance()
    private var statusBar: StatusBar? = null
    
    init {
        // 初始化ADB路径
        adbPath = AdbUtils.getAdbPath(project)
        
        // 开始监控Activity
        startMonitoring()
    }
    
   override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        updateStatusBar()
    }
    
    override fun ID(): String = WIDGET_ID
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    
    override fun getText(): String {
        return if (currentActivity == "No Activity") {
            "Android: No Activity"
        } else {
            "Android: $currentActivity"
        }
    }
    
    override fun getAlignment(): Float = 0.5f
    
    override fun getTooltipText(): String? {
        val tooltip = StringBuilder()
        tooltip.append("Current Activity: $currentActivity")
        
        if (settings.showDeviceInfo && currentDevice != null) {
            tooltip.append("\nDevice: $currentDevice")
        }
        
        tooltip.append("\nLeft click to copy, Right click for device menu")
        tooltip.append("\nRefresh interval: ${settings.refreshInterval}s")
        
        return tooltip.toString()
    }
    
    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { event ->
            when {
                event.button == MouseEvent.BUTTON1 -> { // 左键点击
                    copyActivityToClipboard()
                }
                event.button == MouseEvent.BUTTON3 -> { // 右键点击
                    showDeviceMenu(event)
                }
            }
        }
    }
    
    private fun startMonitoring() {
        if (!settings.enabled) {
            currentActivity = "Disabled"
            updateStatusBar()
            return
        }
        
        if (adbPath == null) {
            currentActivity = "ADB not found"
            updateStatusBar()
            return
        }
        
        // 定期更新Activity信息
        executor.scheduleWithFixedDelay({
            try {
                if (!settings.enabled) {
                    ApplicationManager.getApplication().invokeLater {
                        currentActivity = "Disabled"
                        updateStatusBar()
                    }
                    return@scheduleWithFixedDelay
                }
                
                updateDeviceList()
                updateCurrentActivity()
                
                // 在EDT线程中更新UI
                ApplicationManager.getApplication().invokeLater {
                    updateStatusBar()
                }
            } catch (e: Exception) {
                // 处理异常
                ApplicationManager.getApplication().invokeLater {
                    currentActivity = "Error: ${e.message}"
                    updateStatusBar()
                }
            }
        }, 0, settings.getValidRefreshInterval().toLong(), TimeUnit.SECONDS)
    }
    
    private fun updateDeviceList() {
        adbPath?.let { path ->
            connectedDevices = AdbUtils.getConnectedDevices(path)
            
            // 如果当前设备不在连接列表中，选择第一个可用设备
            if (currentDevice == null || !connectedDevices.contains(currentDevice)) {
                currentDevice = connectedDevices.firstOrNull()
            }
        }
    }
    
    private fun updateCurrentActivity() {
        if (adbPath != null && currentDevice != null) {
            val activity = AdbUtils.getCurrentActivity(adbPath!!, currentDevice)
            currentActivity = activity ?: "No Activity"
        } else {
            currentActivity = if (connectedDevices.isEmpty()) "No Device" else "No Activity"
        }
    }
    
    private fun updateStatusBar() {
        statusBar?.updateWidget(WIDGET_ID)
    }
    
    private fun copyActivityToClipboard() {
        if (currentActivity != "No Activity" && currentActivity != "No Device" && !currentActivity.startsWith("Error:")) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(currentActivity)
            clipboard.setContents(selection, null)
            
            // 可以添加通知提示用户已复制
            // NotificationGroupManager.getInstance()
            //     .getNotificationGroup("AndroidNowActivity")
            //     .createNotification("Copied to clipboard: $currentActivity", NotificationType.INFORMATION)
            //     .notify(myProject)
        }
    }
    
    private fun showDeviceMenu(event: MouseEvent) {
        if (connectedDevices.isEmpty()) {
            return
        }
        
        val popupMenu = JPopupMenu()
        
        for (device in connectedDevices) {
            val menuItem = JMenuItem(device)
            if (device == currentDevice) {
                menuItem.text = "● $device" // 标记当前选中的设备
            }
            menuItem.addActionListener {
                currentDevice = device
                // 立即更新Activity信息
                ApplicationManager.getApplication().executeOnPooledThread {
                    updateCurrentActivity()
                    ApplicationManager.getApplication().invokeLater {
                        updateStatusBar()
                    }
                }
            }
            popupMenu.add(menuItem)
        }
        
        popupMenu.show(event.component, event.x, event.y)
    }
    
    override fun dispose() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}