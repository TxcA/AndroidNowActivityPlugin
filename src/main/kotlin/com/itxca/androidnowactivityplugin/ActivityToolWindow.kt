package com.itxca.androidnowactivityplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.Timer
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class ActivityToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val activityToolWindow = ActivityToolWindow(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(activityToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}

class ActivityToolWindow(private val project: Project) {
    
    private val panel = JPanel(BorderLayout())
    private val currentActivityLabel = JLabel("No Activity", SwingConstants.LEFT)
    private val statusLabel = JLabel("Status: Initializing...", SwingConstants.LEFT)
    private val refreshButton = JButton("Refresh")
    private val copyButton = JButton("Copy")
    private val deviceComboBox = JComboBox<String>()
    private val historyListModel = DefaultListModel<String>()
    private val historyList = JList(historyListModel)
    private val activityHistory = mutableListOf<Pair<String, String>>() // Pair<Activity, Timestamp>
    
    @Volatile
    private var currentActivity: String = "No Activity"
    @Volatile
    private var currentDevice: String? = null
    @Volatile
    private var connectedDevices: List<String> = emptyList()
    @Volatile
    private var adbPath: String? = null
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val settings = ActivityMonitorSettings.getInstance()
    @Volatile
    private var isMonitoring = false
    
    init {
        setupUI()
        initializeMonitoring()
    }
    
    private fun setupUI() {
        // 顶部控制面板 - 横向布局
        val topControlPanel = JPanel()
        topControlPanel.layout = BoxLayout(topControlPanel, BoxLayout.X_AXIS)
        topControlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        // 设备选择
        topControlPanel.add(JLabel("Device: "))
        topControlPanel.add(Box.createHorizontalStrut(5))
        topControlPanel.add(deviceComboBox)
        deviceComboBox.addActionListener {
            val selectedDevice = deviceComboBox.selectedItem as? String
            if (selectedDevice != null && selectedDevice != currentDevice) {
                currentDevice = selectedDevice
                updateCurrentActivity()
            }
        }
        
        // 按钮
        topControlPanel.add(Box.createHorizontalStrut(10))
        refreshButton.addActionListener { 
            updateDeviceList()
            updateCurrentActivity()
        }
        topControlPanel.add(refreshButton)
        
        topControlPanel.add(Box.createHorizontalStrut(5))
        copyButton.addActionListener { copyActivityToClipboard() }
        topControlPanel.add(copyButton)
        
        topControlPanel.add(Box.createHorizontalGlue()) // 推到左边
        
        // 中间内容面板 - 分为左右两部分
        val contentPanel = JPanel(BorderLayout())
        
        // 左侧：当前Activity显示
        val currentActivityPanel = RoundedPanel(12)
        currentActivityPanel.layout = BorderLayout()
        currentActivityPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEmptyBorder(8, 8, 8, 8), 
            "Current Activity"
        )
        
        // 设置当前Activity标签样式
        currentActivityLabel.font = currentActivityLabel.font.deriveFont(java.awt.Font.BOLD, 14f)
        currentActivityLabel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        currentActivityLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (currentActivity != "No Activity" && currentActivity != "No Device") {
                    if (e.clickCount == 1) {
                        // 单击复制简短名称（只复制类名）
                        val shortName = currentActivity.substringAfterLast('.')
                        copyToClipboard(shortName)
                        showCopyMessage("Copied: $shortName")
                    } else if (e.clickCount == 2) {
                        // 双击复制完整名称
                        copyToClipboard(currentActivity)
                        showCopyMessage("Copied: $currentActivity")
                    }
                }
            }
        })
        
        currentActivityPanel.add(currentActivityLabel, BorderLayout.CENTER)
        
        // 右侧：Activity历史列表
        val historyPanel = RoundedPanel(12)
        historyPanel.layout = BorderLayout()
        historyPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEmptyBorder(8, 8, 8, 8), 
            "Recent Activities"
        )
        
        // 设置历史列表
        historyList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        historyList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedIndex = historyList.selectedIndex
                    if (selectedIndex >= 0 && selectedIndex < activityHistory.size) {
                        val activityName = activityHistory[selectedIndex].first
                        copyToClipboard(activityName)
                        showCopyMessage("Copied: $activityName")
                    }
                }
            }
        })
        
        val scrollPane = JScrollPane(historyList)
        scrollPane.preferredSize = java.awt.Dimension(300, 0)
        scrollPane.border = null
        historyPanel.add(scrollPane, BorderLayout.CENTER)
        
        // 使用JSplitPane分割左右两部分
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, currentActivityPanel, historyPanel)
        splitPane.resizeWeight = 0.6 // 左侧占60%
        splitPane.dividerLocation = 400
        splitPane.dividerSize = 6
        splitPane.border = null
        
        contentPanel.add(splitPane, BorderLayout.CENTER)
        
        // 底部状态栏
        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        statusPanel.add(statusLabel, BorderLayout.WEST)
        
        // 组装主面板
        panel.add(topControlPanel, BorderLayout.NORTH)
        panel.add(contentPanel, BorderLayout.CENTER)
        panel.add(statusPanel, BorderLayout.SOUTH)
    }
    
    private fun initializeMonitoring() {
        // 初始化ADB路径
        adbPath = AdbUtils.getAdbPath(project)
        
        if (adbPath == null) {
            statusLabel.text = "Status: ADB not found"
            currentActivityLabel.text = "Please configure ADB path in settings"
            return
        }
        
        // 开始监控
        startMonitoring()
    }
    
    private fun startMonitoring() {
        if (isMonitoring) {
            return // 避免重复启动
        }
        
        isMonitoring = true
        statusLabel.text = "Status: Monitoring..."
        
        // 立即更新一次
        updateDeviceList()
        updateCurrentActivity()
        
        // 定期更新
        executor.scheduleWithFixedDelay({
            try {
                if (!isMonitoring || !settings.enabled) {
                    return@scheduleWithFixedDelay
                }
                
                // 在后台线程中执行ADB操作
                updateDeviceList()
                val newActivity = getCurrentActivityFromAdb()
                
                // 检查Activity是否发生变化
                if (newActivity != currentActivity) {
                    val oldActivity = currentActivity
                    currentActivity = newActivity
                    
                    // 如果是有效的新Activity，添加到历史记录
                    if (newActivity != "No Activity" && newActivity != "No Device" && newActivity != oldActivity) {
                        addToHistory(newActivity)
                    }
                }
                
                // 在EDT线程中更新UI
                SwingUtilities.invokeLater {
                    updateUI()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Status: Error - ${e.message}"
                }
            }
        }, 0, settings.getValidRefreshInterval().toLong(), TimeUnit.SECONDS)
    }
    
    private fun updateDeviceList() {
        if (adbPath != null) {
            connectedDevices = AdbUtils.getConnectedDevices(adbPath!!)
            
            SwingUtilities.invokeLater {
                deviceComboBox.removeAllItems()
                if (connectedDevices.isEmpty()) {
                    deviceComboBox.addItem("No devices")
                    currentDevice = null
                } else {
                    connectedDevices.forEach { device ->
                        deviceComboBox.addItem(device)
                    }
                    if (currentDevice == null || !connectedDevices.contains(currentDevice)) {
                        currentDevice = connectedDevices.firstOrNull()
                        if (currentDevice != null) {
                            deviceComboBox.selectedItem = currentDevice
                        }
                    }
                }
            }
        }
    }
    
    private fun getCurrentActivityFromAdb(): String {
        return if (adbPath != null && currentDevice != null) {
            AdbUtils.getCurrentActivity(adbPath!!, currentDevice) ?: "No Activity"
        } else {
            if (connectedDevices.isEmpty()) "No Device" else "No Activity"
        }
    }
    
    private fun updateCurrentActivity() {
        currentActivity = getCurrentActivityFromAdb()
    }
    
    private fun addToHistory(activity: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss").format(Date())
        val activityPair = Pair(activity, timestamp)
        
        // 避免重复添加相同的Activity
        val existingIndex = activityHistory.indexOfFirst { it.first == activity }
        if (existingIndex >= 0) {
            activityHistory.removeAt(existingIndex)
        }
        
        // 添加到历史记录开头
        activityHistory.add(0, activityPair)
        
        // 限制历史记录数量
        val maxSize = settings.activityHistorySize
        while (activityHistory.size > maxSize) {
            activityHistory.removeAt(activityHistory.size - 1)
        }
        
        // 更新UI列表
        SwingUtilities.invokeLater {
            historyListModel.clear()
            activityHistory.forEach { (activityName, time) ->
                historyListModel.addElement("$time - $activityName")
            }
        }
    }
    
    private fun updateUI() {
        currentActivityLabel.text = if (currentActivity == "No Activity" || currentActivity == "No Device") {
            "<html><div style='color: #888888;'><i>$currentActivity</i></div></html>"
        } else {
            "<html><div><b>$currentActivity</b><br/><small style='color: #666666;'>Double-click to copy</small></div></html>"
        }
        
        statusLabel.text = when {
            adbPath == null -> "Status: ADB not found"
            connectedDevices.isEmpty() -> "Status: No devices connected"
            currentDevice == null -> "Status: No device selected"
            currentActivity == "No Activity" -> "Status: No activity detected"
            else -> "Status: Active (Refresh: ${settings.getValidRefreshInterval()}s) | Device: ${currentDevice ?: "Unknown"}"
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, null)
    }
    
    private fun showCopyMessage(message: String) {
        val originalText = statusLabel.text
        statusLabel.text = message
        Timer(2000) {
            statusLabel.text = originalText
        }.apply {
            isRepeats = false
            start()
        }
    }
    
    private fun copyActivityToClipboard() {
        if (currentActivity != "No Activity" && currentActivity != "No Device") {
            copyToClipboard(currentActivity)
            showCopyMessage("Copied: $currentActivity")
        }
    }
    
    fun getContent(): JComponent = panel
    
    fun dispose() {
        isMonitoring = false
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