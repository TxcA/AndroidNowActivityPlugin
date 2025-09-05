package com.itxca.androidnowactivityplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object AdbUtils {
    
    /**
     * 获取ADB可执行文件的路径
     * 优先从设置中的自定义路径获取，然后从Android Studio的SDK配置中获取，最后从环境变量中查找
     */
    fun getAdbPath(project: Project?): String? {
        val settings = ActivityMonitorSettings.getInstance()
        
        // 首先检查是否使用自定义ADB路径
        if (settings.useCustomAdbPath && settings.customAdbPath.isNotBlank()) {
            val customAdbFile = File(settings.customAdbPath)
            if (customAdbFile.exists()) {
                return customAdbFile.absolutePath
            }
        }
        
        // 跳过Android Studio SDK配置获取，直接使用环境变量方式
        // 这样可以避免版本兼容性问题
        
        // 从环境变量ANDROID_HOME中查找
        val androidHome = System.getenv("ANDROID_HOME")
        if (androidHome != null) {
            val adbPath = File(androidHome, "platform-tools${File.separator}adb${if (SystemInfo.isWindows) ".exe" else ""}")
            if (adbPath.exists()) {
                return adbPath.absolutePath
            }
        }
        
        // 从PATH环境变量中查找
        val pathEnv = System.getenv("PATH")
        if (pathEnv != null) {
            val paths = pathEnv.split(if (SystemInfo.isWindows) ";" else ":")
            for (path in paths) {
                val adbPath = File(path, "adb${if (SystemInfo.isWindows) ".exe" else ""}")
                if (adbPath.exists()) {
                    return adbPath.absolutePath
                }
            }
        }
        
        return null
    }
    
    /**
     * 获取连接的设备列表
     */
    fun getConnectedDevices(adbPath: String): List<String> {
        return try {
            val result = executeAdbCommand(adbPath, listOf("devices"))
            val devices = mutableListOf<String>()
            val lines = result.split("\n")
            for (line in lines) {
                if (line.contains("\t") && line.endsWith("device")) {
                    val deviceId = line.split("\t")[0].trim()
                    devices.add(deviceId)
                }
            }
            devices
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取当前活动的Activity
     */
    fun getCurrentActivity(adbPath: String, deviceId: String? = null): String? {
        return try {
            // 方法1: 最基础的测试 - 检查ADB连接
            testBasicAdbConnection(adbPath, deviceId)
            
            // 方法2: 使用最简单的dumpsys命令
            var activity = getCurrentActivityFromSimpleDumpsys(adbPath, deviceId)
            if (activity != null) {
                return activity
            }
            
            // 方法3: 使用dumpsys activity activities (传统方法)
            activity = getCurrentActivityFromDumpsys(adbPath, deviceId)
            if (activity != null) {
                return activity
            }
            
            // 方法4: 使用dumpsys activity top
            activity = getCurrentActivityFromTop(adbPath, deviceId)
            if (activity != null) {
                return activity
            }
            
            // 方法5: 使用am命令方式 (适用于Android 10+)
            activity = getCurrentActivityFromAm(adbPath, deviceId)
            if (activity != null) {
                return activity
            }
            
            // 方法6: 使用logcat方式
            activity = getCurrentActivityFromLogcat(adbPath, deviceId)
            if (activity != null) {
                return activity
            }
            
            // 方法7: 使用dumpsys activity recents
            activity = getCurrentActivityFromRecents(adbPath, deviceId)
            if (activity != null) {
                return activity
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 测试基础ADB连接
     */
    private fun testBasicAdbConnection(adbPath: String, deviceId: String?) {
        try {
            val commands = mutableListOf<String>()
            if (deviceId != null) {
                commands.addAll(listOf("-s", deviceId))
            }
            commands.addAll(listOf("shell", "echo", "test"))
            
            executeAdbCommand(adbPath, commands)
        } catch (e: Exception) {
            // 忽略连接测试异常
        }
    }
    
    /**
     * 使用最简单的dumpsys命令获取当前Activity
     */
    private fun getCurrentActivityFromSimpleDumpsys(adbPath: String, deviceId: String?): String? {
        return try {
            // 尝试使用dumpsys window windows命令获取焦点窗口
            val windowCommands = mutableListOf<String>()
            if (deviceId != null) {
                windowCommands.addAll(listOf("-s", deviceId))
            }
            windowCommands.addAll(listOf("shell", "dumpsys", "window", "windows"))
            
            val windowResult = executeAdbCommand(adbPath, windowCommands)
            var activity = parseActivityFromWindowDumpsys(windowResult)
            if (activity != null) {
                return activity
            }
            
            // 备用方法：使用dumpsys activity activities
            val activityCommands = mutableListOf<String>()
            if (deviceId != null) {
                activityCommands.addAll(listOf("-s", deviceId))
            }
            activityCommands.addAll(listOf("shell", "dumpsys", "activity", "activities"))
            
            val activityResult = executeAdbCommand(adbPath, activityCommands)
            parseActivityFromSimpleDumpsys(activityResult)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 使用am命令获取当前Activity (适用于Android 10+)
     */
    private fun getCurrentActivityFromAm(adbPath: String, deviceId: String?): String? {
        return try {
            // 使用am stack list命令
            val stackCommands = mutableListOf<String>()
            if (deviceId != null) {
                stackCommands.addAll(listOf("-s", deviceId))
            }
            stackCommands.addAll(listOf("shell", "am", "stack", "list"))
            
            val stackResult = executeAdbCommand(adbPath, stackCommands)
            var activity = parseActivityFromAmStack(stackResult)
            if (activity != null) {
                return activity
            }
            
            // 尝试使用am get-current命令
            val currentCommands = mutableListOf<String>()
            if (deviceId != null) {
                currentCommands.addAll(listOf("-s", deviceId))
            }
            currentCommands.addAll(listOf("shell", "am", "get-current"))
            
            val currentResult = executeAdbCommand(adbPath, currentCommands)
            activity = parseActivityFromAmCurrent(currentResult)
            if (activity != null) {
                return activity
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    

    
    /**
     * 使用logcat方式获取当前Activity (适用于高版本Android)
     */
    private fun getCurrentActivityFromLogcat(adbPath: String, deviceId: String?): String? {
        return try {
            // 获取最近的Activity启动日志（不清除缓存，避免权限问题）
            val logCommands = mutableListOf<String>()
            if (deviceId != null) {
                logCommands.addAll(listOf("-s", deviceId))
            }
            logCommands.addAll(listOf("shell", "logcat", "-d", "-t", "100", "ActivityManager:I", "ActivityTaskManager:I", "*:S"))
            
            val logResult = executeAdbCommand(adbPath, logCommands)
            parseActivityFromLogcat(logResult)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从dumpsys activity activities获取当前Activity (传统方法)
     */
    private fun getCurrentActivityFromDumpsys(adbPath: String, deviceId: String?): String? {
        return try {
            val commands = mutableListOf<String>()
            if (deviceId != null) {
                commands.addAll(listOf("-s", deviceId))
            }
            commands.addAll(listOf("shell", "dumpsys", "activity", "activities"))
            
            val result = executeAdbCommand(adbPath, commands)
            parseActivityFromDumpsys(result)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从dumpsys activity top获取当前Activity
     */
    private fun getCurrentActivityFromTop(adbPath: String, deviceId: String?): String? {
        return try {
            val commands = mutableListOf<String>()
            if (deviceId != null) {
                commands.addAll(listOf("-s", deviceId))
            }
            commands.addAll(listOf("shell", "dumpsys", "activity", "top"))
            
            val result = executeAdbCommand(adbPath, commands)
            parseActivityFromTop(result)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从dumpsys activity recents获取当前Activity
     */
    private fun getCurrentActivityFromRecents(adbPath: String, deviceId: String?): String? {
        return try {
            val commands = mutableListOf<String>()
            if (deviceId != null) {
                commands.addAll(listOf("-s", deviceId))
            }
            commands.addAll(listOf("shell", "dumpsys", "activity", "recents"))
            
            val result = executeAdbCommand(adbPath, commands)
            parseActivityFromRecents(result)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 执行ADB命令
     */
    private fun executeAdbCommand(adbPath: String, commands: List<String>): String {
        return try {
            val processBuilder = ProcessBuilder()
            val fullCommand = mutableListOf(adbPath)
            fullCommand.addAll(commands)
            
            processBuilder.command(fullCommand)
            val process = processBuilder.start()
            
            // 读取标准输出
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            // 读取错误输出
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val errorOutput = StringBuilder()
            var errorLine: String?
            
            while (errorReader.readLine().also { errorLine = it } != null) {
                errorOutput.append(errorLine).append("\n")
            }
            
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
            }
            
            output.toString()
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 解析窗口dumpsys输出
     */
    private fun parseActivityFromWindowDumpsys(windowOutput: String): String? {
        return try {
            val lines = windowOutput.split("\n")
            for (line in lines) {
                // 查找当前焦点窗口
                if (line.contains("mCurrentFocus") || line.contains("mFocusedWindow")) {
                    // 提取Activity名称 - 格式如: Window{abc123 u0 com.example.app/com.example.MainActivity}
                    val activityMatch = Regex("([a-zA-Z0-9._]+/[a-zA-Z0-9._]+)").find(line)
                    if (activityMatch != null) {
                        val component = activityMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 解析简单dumpsys输出
     */
    private fun parseActivityFromSimpleDumpsys(dumpsysOutput: String): String? {
        return try {
            val lines = dumpsysOutput.split("\n")
            for (line in lines) {
                // 查找包含Activity信息的行
                if (line.contains("mResumedActivity") || line.contains("mFocusedActivity") || 
                    line.contains("ResumedActivity") || line.contains("topActivity")) {
                    // 提取Activity名称
                    val activityMatch = Regex("([a-zA-Z0-9._]+/[a-zA-Z0-9._]+)").find(line)
                    if (activityMatch != null) {
                        val component = activityMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从dumpsys输出中解析Activity名称
     */
    private fun parseActivityFromDumpsys(dumpsysOutput: String): String? {
        val lines = dumpsysOutput.split("\n")
        for (line in lines) {
            if (line.contains("mResumedActivity") || line.contains("mFocusedActivity")) {
                // 解析格式如: mResumedActivity: ActivityRecord{hash u0 com.example.app/.MainActivity t123}
                val regex = Regex("([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)")
                val matchResult = regex.find(line)
                if (matchResult != null) {
                    val activityName = matchResult.value
                    // 转换为完整的类名格式
                    return convertToFullClassName(activityName)
                }
            }
        }
        return null
    }
    
    /**
     * 从dumpsys activity top输出中解析Activity名称
     */
    private fun parseActivityFromTop(topOutput: String): String? {
        val lines = topOutput.split("\n")
        for (line in lines) {
            // 查找ACTIVITY行
            if (line.trim().startsWith("ACTIVITY")) {
                // 格式如: ACTIVITY com.example.app/.MainActivity hash {hash}
                val regex = Regex("ACTIVITY\\s+([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)")
                val matchResult = regex.find(line)
                if (matchResult != null) {
                    val activityName = matchResult.groupValues[1]
                    return convertToFullClassName(activityName)
                }
            }
            // 也查找包含包名的行
            if (line.contains("/") && (line.contains("Activity") || line.contains("activity"))) {
                val regex = Regex("([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)")
                val matchResult = regex.find(line)
                if (matchResult != null) {
                    val activityName = matchResult.value
                    return convertToFullClassName(activityName)
                }
            }
        }
        return null
    }
    
    /**
     * 从dumpsys activity recents输出中解析Activity名称
     */
    private fun parseActivityFromRecents(recentsOutput: String): String? {
        val lines = recentsOutput.split("\n")
        for (line in lines) {
            // 查找Recent #0行，通常是最近的Activity
            if (line.contains("Recent #0") || line.contains("Recent #1")) {
                val regex = Regex("([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)")
                val matchResult = regex.find(line)
                if (matchResult != null) {
                    val activityName = matchResult.value
                    return convertToFullClassName(activityName)
                }
            }
        }
        return null
    }
    

    

    
    /**
     * 解析am stack list输出
     */
    private fun parseActivityFromAmStack(stackOutput: String): String? {
        return try {
            val lines = stackOutput.split("\n")
            for (line in lines) {
                // 查找当前可见的Activity
                if (line.contains("visible=true") || line.contains("topActivity")) {
                    val activityMatch = Regex("([a-zA-Z0-9.]+/[a-zA-Z0-9.]+)").find(line)
                    if (activityMatch != null) {
                        val component = activityMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 解析am get-current输出
     */
    private fun parseActivityFromAmCurrent(currentOutput: String): String? {
        return try {
            val lines = currentOutput.split("\n")
            for (line in lines) {
                if (line.contains("Activity") && line.contains("/")) {
                    val activityMatch = Regex("([a-zA-Z0-9.]+/[a-zA-Z0-9.]+)").find(line)
                    if (activityMatch != null) {
                        val component = activityMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    

    
    /**
     * 解析logcat输出获取Activity
     */
    private fun parseActivityFromLogcat(logOutput: String): String? {
        return try {
            val lines = logOutput.split("\n")
            var latestActivity: String? = null
            
            for (line in lines.reversed()) {
                // 查找Activity启动日志，支持ActivityManager和ActivityTaskManager
                if ((line.contains("ActivityManager") || line.contains("ActivityTaskManager")) && 
                    (line.contains("START u0") || line.contains("Displayed") || line.contains("Resuming"))) {
                    
                    // 匹配 START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 cmp=com.example/.MainActivity}
                    val cmpMatch = Regex("cmp=([^\\s}]+)").find(line)
                    if (cmpMatch != null) {
                        val component = cmpMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                    
                    // 匹配 Displayed com.example/.MainActivity: +123ms
                    val displayedMatch = Regex("Displayed ([^:]+)").find(line)
                    if (displayedMatch != null) {
                        val component = displayedMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                    
                    // 匹配 Resuming ActivityRecord{...} com.example/.MainActivity
                    val resumingMatch = Regex("Resuming.*?([a-zA-Z0-9.]+/[a-zA-Z0-9.]+)").find(line)
                    if (resumingMatch != null) {
                        val component = resumingMatch.groupValues[1]
                        if (component.contains("/")) {
                            val parts = component.split("/")
                            if (parts.size >= 2) {
                                val packageName = parts[0]
                                val activityName = parts[1]
                                return convertToFullClassName(packageName, activityName)
                            }
                        }
                    }
                }
            }
            latestActivity
        } catch (e: Exception) {
            null
        }
    }
    
    private fun convertToFullClassName(packageName: String, activityName: String): String {
        return if (activityName.startsWith(".")) {
            // 如果以.开头，添加包名前缀
            packageName + activityName
        } else if (!activityName.contains(".")) {
            // 如果不包含.，可能是简短的类名，添加包名
            "$packageName.$activityName"
        } else {
            // 已经是完整类名
            activityName
        }
    }
    
    /**
     * 将Activity名称转换为完整的类名格式
     * 例如: com.example.app/.MainActivity -> com.example.app.MainActivity
     */
    private fun convertToFullClassName(activityName: String): String {
        if (activityName.contains("/.")) {
            val parts = activityName.split("/")
            if (parts.size == 2) {
                val packageName = parts[0]
                val className = parts[1].removePrefix(".")
                return "$packageName.$className"
            }
        }
        return activityName
    }
}