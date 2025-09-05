package com.itxca.androidnowactivityplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "ActivityMonitorSettings",
    storages = [Storage("AndroidNowActivitySettings.xml")]
)
@Service
class ActivityMonitorSettings : PersistentStateComponent<ActivityMonitorSettings> {
    
    companion object {
        fun getInstance(): ActivityMonitorSettings {
            return ApplicationManager.getApplication().getService(ActivityMonitorSettings::class.java)
        }
    }
    
    /**
     * 刷新间隔（秒）
     */
    var refreshInterval: Int = 1
    
    /**
     * 是否启用插件
     */
    var enabled: Boolean = true
    
    /**
     * 是否显示设备信息
     */
    var showDeviceInfo: Boolean = true
    
    /**
     * 自定义ADB路径
     */
    var customAdbPath: String = ""
    
    /**
     * 是否使用自定义ADB路径
     */
    var useCustomAdbPath: Boolean = false
    
    /**
     * Activity历史记录数量
     */
    var activityHistorySize: Int = 10
    
    override fun getState(): ActivityMonitorSettings = this
    
    override fun loadState(state: ActivityMonitorSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    /**
     * 验证刷新间隔是否有效
     */
    fun isValidRefreshInterval(): Boolean {
        return refreshInterval in 1..60
    }
    
    /**
     * 获取有效的刷新间隔
     */
    fun getValidRefreshInterval(): Int {
        return if (isValidRefreshInterval()) refreshInterval else 1
    }
}