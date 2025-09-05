package com.itxca.androidnowactivityplugin

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class ActivityMonitorConfigurable : Configurable {
    
    private val settings = ActivityMonitorSettings.getInstance()
    
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var refreshIntervalSpinner: JSpinner
    private lateinit var showDeviceInfoCheckBox: JBCheckBox
    private lateinit var useCustomAdbPathCheckBox: JBCheckBox
    private lateinit var customAdbPathField: JBTextField
    private lateinit var activityHistorySizeSpinner: JSpinner
    
    override fun getDisplayName(): String = "Android Now Activity"
    
    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 创建表单组件
        enabledCheckBox = JBCheckBox("Enable Android Activity Monitor")
        
        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(1, 1, 60, 1))
        
        showDeviceInfoCheckBox = JBCheckBox("Show device information in tooltip")
        
        activityHistorySizeSpinner = JSpinner(SpinnerNumberModel(10, 5, 50, 1))
        
        useCustomAdbPathCheckBox = JBCheckBox("Use custom ADB path")
        
        customAdbPathField = JBTextField()
        customAdbPathField.isEnabled = false
        
        // 设置复选框事件
        useCustomAdbPathCheckBox.addActionListener {
            customAdbPathField.isEnabled = useCustomAdbPathCheckBox.isSelected
        }
        
        // 使用FormBuilder创建表单布局
        val formBuilder = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckBox)
            .addLabeledComponent(JBLabel("Refresh interval (seconds):"), refreshIntervalSpinner)
            .addComponent(showDeviceInfoCheckBox)
            .addLabeledComponent(JBLabel("Activity history size:"), activityHistorySizeSpinner)
            .addVerticalGap(10)
            .addComponent(useCustomAdbPathCheckBox)
            .addLabeledComponent(JBLabel("Custom ADB path:"), customAdbPathField)
            .addComponentFillVertically(JPanel(), 0)
        
        panel.add(formBuilder.panel, BorderLayout.CENTER)
        
        return panel
    }
    
    override fun isModified(): Boolean {
        return enabledCheckBox.isSelected != settings.enabled ||
                (refreshIntervalSpinner.value as Int) != settings.refreshInterval ||
                showDeviceInfoCheckBox.isSelected != settings.showDeviceInfo ||
                (activityHistorySizeSpinner.value as Int) != settings.activityHistorySize ||
                useCustomAdbPathCheckBox.isSelected != settings.useCustomAdbPath ||
                customAdbPathField.text != settings.customAdbPath
    }
    
    override fun apply() {
        settings.enabled = enabledCheckBox.isSelected
        settings.refreshInterval = refreshIntervalSpinner.value as Int
        settings.showDeviceInfo = showDeviceInfoCheckBox.isSelected
        settings.activityHistorySize = activityHistorySizeSpinner.value as Int
        settings.useCustomAdbPath = useCustomAdbPathCheckBox.isSelected
        settings.customAdbPath = customAdbPathField.text
    }
    
    override fun reset() {
        enabledCheckBox.isSelected = settings.enabled
        refreshIntervalSpinner.value = settings.refreshInterval
        showDeviceInfoCheckBox.isSelected = settings.showDeviceInfo
        activityHistorySizeSpinner.value = settings.activityHistorySize
        useCustomAdbPathCheckBox.isSelected = settings.useCustomAdbPath
        customAdbPathField.text = settings.customAdbPath
        customAdbPathField.isEnabled = settings.useCustomAdbPath
    }
    
    // Validation method for older IntelliJ versions
    fun doValidate(): ValidationInfo? {
        val refreshInterval = refreshIntervalSpinner.value as Int
        if (refreshInterval < 1 || refreshInterval > 60) {
            return ValidationInfo("Refresh interval must be between 1 and 60 seconds", refreshIntervalSpinner)
        }
        
        if (useCustomAdbPathCheckBox.isSelected && customAdbPathField.text.isBlank()) {
            return ValidationInfo("Custom ADB path cannot be empty when enabled", customAdbPathField)
        }
        
        return null
    }
}