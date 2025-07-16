package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 系统级无障碍服务
 * 通过Shizuku在系统进程中运行，具有系统级权限
 */
class SystemAccessibilityService : IAccessibilityService.Stub() {

    companion object {
        private const val TAG = "SystemAccessibilityService"
        private var instance: SystemAccessibilityService? = null

        fun getInstance(): SystemAccessibilityService? = instance
    }

    private var accessibilityManager: AccessibilityManager? = null
    private var context: Context? = null
    private var accessibilityService: AccessibilityService? = null

    // 回调管理
    private val eventCallbacks = CopyOnWriteArrayList<IAccessibilityEventCallback>()
    private var eventFilter: EventFilter? = null

    // 事件处理线程
    private val handlerThread = HandlerThread("AccessibilityEventHandler")
    private lateinit var eventHandler: Handler

    // 内部的AccessibilityService实现
    private var internalService: InternalAccessibilityService? = null

    // 事件过滤器
    data class EventFilter(
        val packageNames: Set<String>?,
        val eventTypes: Int
    )

    init {
        instance = this
        handlerThread.start()
        eventHandler = Handler(handlerThread.looper)
        initializeAccessibilityManager()
    }

    /**
     * 内部AccessibilityService实现类
     */
    private inner class InternalAccessibilityService : AccessibilityService() {

        override fun onServiceConnected() {
            Log.i(TAG, "内部AccessibilityService已连接")

            // 配置服务信息
            serviceInfo = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                notificationTimeout = 100
            }

            accessibilityService = this
        }

        override fun onAccessibilityEvent(event: AccessibilityEvent?) {
            event?.let { handleAccessibilityEvent(it) }
        }

        override fun onInterrupt() {
            Log.w(TAG, "内部AccessibilityService被中断")
            notifyInterrupt()
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.i(TAG, "内部AccessibilityService已销毁")
            accessibilityService = null
        }
    }

    /**
     * 初始化AccessibilityManager服务
     */
    private fun initializeAccessibilityManager() {
        try {
            // 获取系统Context
            val contextClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = contextClass.getMethod("currentApplication")
            context = currentApplicationMethod.invoke(null) as Context?

            if (context != null) {
                accessibilityManager = context!!.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

                // 创建并启动内部服务
                internalService = InternalAccessibilityService()

                Log.i(TAG, "AccessibilityManager服务初始化成功")

                // 启动事件监听
                startAccessibilityEventMonitoring()
            } else {
                Log.e(TAG, "无法获取Context")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AccessibilityManager服务初始化失败", e)
            accessibilityManager = null
        }
    }

    /**
     * 启动无障碍事件监控
     */
    private fun startAccessibilityEventMonitoring() {
        try {
            // 如果有内部服务，它会自动接收事件
            if (internalService != null && accessibilityService != null) {
                Log.i(TAG, "无障碍事件监控已通过内部服务启动")
                return
            }

            // 备用方案：通过反射监听
            accessibilityManager?.let { manager ->
                // 通过反射添加AccessibilityStateChangeListener
                val listenerClass = Class.forName("android.view.accessibility.AccessibilityManager\$AccessibilityStateChangeListener")
                val addListenerMethod = manager.javaClass.getMethod("addAccessibilityStateChangeListener", listenerClass)

                val listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    when (method.name) {
                        "onAccessibilityStateChanged" -> {
                            val enabled = args[0] as Boolean
                            Log.d(TAG, "无障碍状态变化: $enabled")
                        }
                    }
                    null
                }

                addListenerMethod.invoke(manager, listener)
                Log.i(TAG, "无障碍事件监控已通过反射启动")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动无障碍事件监控失败", e)
        }
    }

    /**
     * 处理无障碍事件
     */
    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        eventHandler.post {
            try {
                // 检查事件过滤器
                eventFilter?.let { filter ->
                    if (filter.eventTypes and event.eventType == 0) {
                        return@post
                    }
                    filter.packageNames?.let { packages ->
                        if (!packages.contains(event.packageName?.toString())) {
                            return@post
                        }
                    }
                }

                // 构建事件信息
                val packageName = event.packageName?.toString() ?: ""
                val className = event.className?.toString() ?: ""
                val text = event.text?.joinToString(" ") ?: ""
                val contentDescription = event.contentDescription?.toString() ?: ""
                val eventTime = event.eventTime
                val windowId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    event.windowId
                } else {
                    -1
                }

                // 通知所有回调
                eventCallbacks.forEach { callback ->
                    try {
                        callback.onAccessibilityEvent(
                            event.eventType,
                            packageName,
                            className,
                            text,
                            contentDescription,
                            eventTime,
                            windowId
                        )
                    } catch (e: RemoteException) {
                        Log.e(TAG, "回调通知失败", e)
                        eventCallbacks.remove(callback)
                    }
                }

                // 记录重要事件
                when (event.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        Log.d(TAG, "窗口状态改变: 包名=$packageName, 类名=$className")
                    }
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                        Log.v(TAG, "窗口内容改变: 包名=$packageName")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "处理无障碍事件失败", e)
            }
        }
    }

    /**
     * 通知服务中断
     */
    private fun notifyInterrupt() {
        eventHandler.post {
            eventCallbacks.forEach { callback ->
                try {
                    callback.onInterrupt()
                } catch (e: RemoteException) {
                    Log.e(TAG, "中断通知失败", e)
                    eventCallbacks.remove(callback)
                }
            }
        }
    }

    /**
     * 通知服务连接状态变化
     */
    private fun notifyServiceConnectionChanged(connected: Boolean) {
        eventHandler.post {
            eventCallbacks.forEach { callback ->
                try {
                    callback.onServiceConnectionChanged(connected)
                } catch (e: RemoteException) {
                    Log.e(TAG, "连接状态通知失败", e)
                    eventCallbacks.remove(callback)
                }
            }
        }
    }

    // IAccessibilityService接口实现

    override fun registerEventCallback(callback: IAccessibilityEventCallback?, eventTypes: Int): Boolean {
        return try {
            if (callback != null && !eventCallbacks.contains(callback)) {
                eventCallbacks.add(callback)
                Log.i(TAG, "注册事件回调成功，当前回调数: ${eventCallbacks.size}")

                // 通知当前连接状态
                callback.onServiceConnectionChanged(isServiceAvailable())
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册事件回调失败", e)
            false
        }
    }

    override fun unregisterEventCallback(callback: IAccessibilityEventCallback?): Boolean {
        return try {
            if (callback != null && eventCallbacks.remove(callback)) {
                Log.i(TAG, "取消注册事件回调成功，剩余回调数: ${eventCallbacks.size}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消注册事件回调失败", e)
            false
        }
    }

    override fun setEventFilter(packageNames: Array<String>?, eventTypes: Int) {
        try {
            eventFilter = EventFilter(
                packageNames = packageNames?.toSet(),
                eventTypes = eventTypes
            )
            Log.i(TAG, "设置事件过滤器: 包名=${packageNames?.joinToString()}, 事件类型=$eventTypes")
        } catch (e: Exception) {
            Log.e(TAG, "设置事件过滤器失败", e)
        }
    }

    /**
     * 获取根节点信息
     */
    override fun getRootNodeInfo(): String {
        return try {
            val result = JSONObject()
            val windowsArray = JSONArray()

            // 使用AccessibilityService获取窗口
            accessibilityService?.windows?.forEach { window ->
                val windowInfo = JSONObject()
                windowInfo.put("id", window.id)
                windowInfo.put("type", window.type)
                windowInfo.put("layer", window.layer)
                windowInfo.put("title", window.title?.toString() ?: "")
                windowInfo.put("isActive", window.isActive)
                windowInfo.put("isFocused", window.isFocused)

                window.root?.let { root ->
                    windowInfo.put("rootNode", nodeToJson(root))
                    windowInfo.put("packageName", root.packageName?.toString() ?: "")
                }

                windowsArray.put(windowInfo)
            } ?: run {
                // 备用方案：获取当前窗口的根节点
                accessibilityService?.rootInActiveWindow?.let { root ->
                    val windowInfo = JSONObject()
                    windowInfo.put("id", -1)
                    windowInfo.put("type", AccessibilityWindowInfo.TYPE_APPLICATION)
                    windowInfo.put("rootNode", nodeToJson(root))
                    windowInfo.put("packageName", root.packageName?.toString() ?: "")
                    windowsArray.put(windowInfo)
                }
            }

            result.put("windows", windowsArray)
            result.put("windowCount", windowsArray.length())
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取根节点信息失败", e)
            JSONObject().put("error", e.message).toString()
        }
    }

    /**
     * 根据ID查找节点
     */
    override fun findNodeById(id: String): String {
        return try {
            val result = JSONArray()

            // 从所有窗口中查找
            accessibilityService?.windows?.forEach { window ->
                window.root?.let { root ->
                    findNodesByIdRecursive(root, id).forEach { node ->
                        result.put(nodeToJson(node))
                    }
                }
            } ?: run {
                // 备用方案：只从活动窗口查找
                accessibilityService?.rootInActiveWindow?.let { root ->
                    findNodesByIdRecursive(root, id).forEach { node ->
                        result.put(nodeToJson(node))
                    }
                }
            }

            JSONObject().put("nodes", result).put("count", result.length()).toString()
        } catch (e: Exception) {
            Log.e(TAG, "根据ID查找节点失败", e)
            JSONObject().put("error", e.message).toString()
        }
    }

    /**
     * 根据文本查找节点
     */
    override fun findNodeByText(text: String): String {
        return try {
            val result = JSONArray()

            accessibilityService?.windows?.forEach { window ->
                window.root?.let { root ->
                    findNodesByTextRecursive(root, text).forEach { node ->
                        result.put(nodeToJson(node))
                    }
                }
            } ?: run {
                accessibilityService?.rootInActiveWindow?.let { root ->
                    findNodesByTextRecursive(root, text).forEach { node ->
                        result.put(nodeToJson(node))
                    }
                }
            }

            JSONObject().put("nodes", result).put("count", result.length()).toString()
        } catch (e: Exception) {
            Log.e(TAG, "根据文本查找节点失败", e)
            JSONObject().put("error", e.message).toString()
        }
    }

    /**
     * 根据类名查找节点
     */
    override fun findNodeByClass(className: String): String {
        return try {
            val result = JSONArray()

            accessibilityService?.windows?.forEach { window ->
                window.root?.let { root ->
                    findNodesByClassRecursive(root, className).forEach { node ->
                        result.put(nodeToJson(node))
                    }
                }
            } ?: run {
                accessibilityService?.rootInActiveWindow?.let { root ->
                    findNodesByClassRecursive(root, className).forEach { node ->
                        result.put(nodeToJson(node))
                    }
                }
            }

            JSONObject().put("nodes", result).put("count", result.length()).toString()
        } catch (e: Exception) {
            Log.e(TAG, "根据类名查找节点失败", e)
            JSONObject().put("error", e.message).toString()
        }
    }

    /**
     * 点击指定ID的节点
     */
    override fun clickNodeById(id: String): Boolean {
        return try {
            val nodes = mutableListOf<AccessibilityNodeInfo>()

            accessibilityService?.windows?.forEach { window ->
                window.root?.let { root ->
                    nodes.addAll(findNodesByIdRecursive(root, id))
                }
            } ?: run {
                accessibilityService?.rootInActiveWindow?.let { root ->
                    nodes.addAll(findNodesByIdRecursive(root, id))
                }
            }

            if (nodes.isNotEmpty()) {
                val node = nodes[0]
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "点击节点ID[$id]结果: $success")
                success
            } else {
                Log.w(TAG, "未找到ID为[$id]的节点")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "点击节点失败", e)
            false
        }
    }

    /**
     * 点击指定文本的节点
     */
    override fun clickNodeByText(text: String): Boolean {
        return try {
            val nodes = mutableListOf<AccessibilityNodeInfo>()

            accessibilityService?.windows?.forEach { window ->
                window.root?.let { root ->
                    nodes.addAll(findNodesByTextRecursive(root, text))
                }
            } ?: run {
                accessibilityService?.rootInActiveWindow?.let { root ->
                    nodes.addAll(findNodesByTextRecursive(root, text))
                }
            }

            if (nodes.isNotEmpty()) {
                val node = nodes[0]
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "点击节点文本[$text]结果: $success")
                success
            } else {
                Log.w(TAG, "未找到文本为[$text]的节点")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "点击节点失败", e)
            false
        }
    }

    /**
     * 获取当前活动窗口信息
     */
    override fun getCurrentWindowInfo(): String {
        return try {
            val result = JSONObject()
            val activeWindows = JSONArray()

            accessibilityService?.windows?.forEach { window ->
                if (window.isActive) {
                    val windowInfo = JSONObject()
                    windowInfo.put("id", window.id)
                    windowInfo.put("type", window.type)
                    windowInfo.put("layer", window.layer)
                    windowInfo.put("title", window.title?.toString() ?: "")
                    windowInfo.put("isActive", window.isActive)
                    windowInfo.put("isFocused", window.isFocused)

                    window.root?.let { root ->
                        windowInfo.put("packageName", root.packageName?.toString() ?: "")
                        windowInfo.put("className", root.className?.toString() ?: "")
                    }

                    activeWindows.put(windowInfo)
                }
            }

            result.put("activeWindows", activeWindows)
            result.put("activeWindowCount", activeWindows.length())
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取当前窗口信息失败", e)
            JSONObject().put("error", e.message).toString()
        }
    }

    /**
     * 获取当前应用包名
     */
    override fun getCurrentPackageName(): String {
        return try {
            accessibilityService?.windows?.forEach { window ->
                if (window.isActive) {
                    window.root?.packageName?.toString()?.let { packageName ->
                        Log.i(TAG, "当前应用包名: $packageName")
                        return packageName
                    }
                }
            }

            // 备用方案
            accessibilityService?.rootInActiveWindow?.packageName?.toString() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "获取当前包名失败", e)
            ""
        }
    }

    /**
     * 执行全局手势
     */
    override fun performGlobalAction(action: Int): Boolean {
        return try {
            val result = accessibilityService?.performGlobalAction(action) ?: false
            Log.i(TAG, "执行全局手势[$action]结果: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "执行全局手势失败", e)
            false
        }
    }

    /**
     * 检查服务是否可用
     */
    override fun isServiceAvailable(): Boolean {
        return try {
            accessibilityManager != null &&
                    accessibilityManager!!.isEnabled &&
                    accessibilityService != null
        } catch (e: Exception) {
            Log.e(TAG, "检查服务可用性失败", e)
            false
        }
    }

    /**
     * 销毁服务
     */
    override fun destroy() {
        try {
            // 停止事件处理线程
            handlerThread.quitSafely()

            // 清理回调
            eventCallbacks.clear()

            // 销毁内部服务
            internalService?.onDestroy()
            internalService = null

            // 清理其他资源
            accessibilityService = null
            accessibilityManager = null
            context = null
            instance = null

            Log.i(TAG, "SystemAccessibilityService已销毁")
        } catch (e: Exception) {
            Log.e(TAG, "销毁服务失败", e)
        }
    }

    // 私有辅助方法

    /**
     * 递归查找ID节点
     */
    private fun findNodesByIdRecursive(node: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        if (node.viewIdResourceName == id) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findNodesByIdRecursive(child, id))
            }
        }

        return results
    }

    /**
     * 递归查找文本节点
     */
    private fun findNodesByTextRecursive(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        if (node.text?.toString()?.contains(text) == true ||
            node.contentDescription?.toString()?.contains(text) == true) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findNodesByTextRecursive(child, text))
            }
        }

        return results
    }

    /**
     * 递归查找类名节点
     */
    private fun findNodesByClassRecursive(node: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        if (node.className?.toString()?.contains(className) == true) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findNodesByClassRecursive(child, className))
            }
        }

        return results
    }

    /**
     * 将AccessibilityNodeInfo转换为JSON
     */
    private fun nodeToJson(node: AccessibilityNodeInfo): JSONObject {
        val json = JSONObject()
        try {
            json.put("id", node.viewIdResourceName ?: "")
            json.put("text", node.text?.toString() ?: "")
            json.put("className", node.className?.toString() ?: "")
            json.put("contentDescription", node.contentDescription?.toString() ?: "")
            json.put("isClickable", node.isClickable)
            json.put("isEnabled", node.isEnabled)
            json.put("isFocused", node.isFocused)
            json.put("isSelected", node.isSelected)
            json.put("isCheckable", node.isCheckable)
            json.put("isChecked", node.isChecked)
            json.put("isScrollable", node.isScrollable)
            json.put("packageName", node.packageName?.toString() ?: "")
            json.put("childCount", node.childCount)

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val boundsJson = JSONObject()
            boundsJson.put("left", bounds.left)
            boundsJson.put("top", bounds.top)
            boundsJson.put("right", bounds.right)
            boundsJson.put("bottom", bounds.bottom)
            boundsJson.put("width", bounds.width())
            boundsJson.put("height", bounds.height())
            json.put("bounds", boundsJson)

            // 添加可执行的动作
            val actions = JSONArray()
            if (node.isClickable) actions.put("click")
            if (node.isLongClickable) actions.put("longClick")
            if (node.isScrollable) actions.put("scroll")
            if (node.isFocusable) actions.put("focus")
            json.put("availableActions", actions)

        } catch (e: Exception) {
            Log.e(TAG, "节点转JSON失败", e)
        }
        return json
    }
}