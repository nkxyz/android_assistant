package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

class SystemAccessibilityService : IAccessibilityService.Stub() {

    companion object {
        private const val TAG = "SystemAccessibilityService"
    }

    private var accessibilityManager: AccessibilityManager? = null
    private var context: Context? = null

    // 回调管理
    private val eventCallbacks = CopyOnWriteArrayList<IAccessibilityEventCallback>()
    private var eventFilter: EventFilter? = null
    private val handler = Handler(Looper.getMainLooper())

    // 事件过滤器
    data class EventFilter(
        val packageNames: Set<String>?,
        val eventTypes: Int
    )

    init {
        initializeAccessibilityManager()
//        startAccessibilityEventMonitoring()
    }

    /**
     * 初始化AccessibilityManager服务
     */
    private fun initializeAccessibilityManager() {
        try {
            // 获取系统服务
            val contextClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = contextClass.getMethod("currentApplication")
            context = currentApplicationMethod.invoke(null) as Context?

            if (context != null) {
                accessibilityManager = context!!.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                Log.i(TAG, "AccessibilityManager服务初始化成功")
            } else {
                Log.e(TAG, "无法获取Context")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AccessibilityManager服务初始化失败", e)
            accessibilityManager = null
        }
    }

    /**
     * 获取根节点信息
     */
    override fun getRootNodeInfo(): String {
        return try {
            val windows = getAccessibilityWindows()
            val result = JSONObject()
            val windowsArray = JSONArray()

            for (window in windows) {
                val windowInfo = JSONObject()
                windowInfo.put("id", window.id)
                windowInfo.put("type", window.type)
                windowInfo.put("layer", window.layer)
                windowInfo.put("title", window.title?.toString() ?: "")

                val root = window.root
                if (root != null) {
                    windowInfo.put("rootNode", nodeToJson(root))
                }
                windowsArray.put(windowInfo)
            }

            result.put("windows", windowsArray)
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
            val nodes = findNodesByIdInternal(id)
            val result = JSONArray()

            for (node in nodes) {
                result.put(nodeToJson(node))
            }

            JSONObject().put("nodes", result).toString()
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
            val nodes = findNodesByTextInternal(text)
            val result = JSONArray()

            for (node in nodes) {
                result.put(nodeToJson(node))
            }

            JSONObject().put("nodes", result).toString()
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
            val nodes = findNodesByClassInternal(className)
            val result = JSONArray()

            for (node in nodes) {
                result.put(nodeToJson(node))
            }

            JSONObject().put("nodes", result).toString()
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
            val nodes = findNodesByIdInternal(id)
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
            val nodes = findNodesByTextInternal(text)
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
            val windows = getAccessibilityWindows()
            val result = JSONObject()
            val activeWindows = JSONArray()

            for (window in windows) {
                if (window.isActive) {
                    val windowInfo = JSONObject()
                    windowInfo.put("id", window.id)
                    windowInfo.put("type", window.type)
                    windowInfo.put("layer", window.layer)
                    windowInfo.put("title", window.title?.toString() ?: "")
                    windowInfo.put("isActive", window.isActive)
                    windowInfo.put("isFocused", window.isFocused)
                    activeWindows.put(windowInfo)
                }
            }

            result.put("activeWindows", activeWindows)
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
            val windows = getAccessibilityWindows()
            for (window in windows) {
                if (window.isActive) {
                    val root = window.root
                    if (root != null) {
                        val packageName = root.packageName?.toString() ?: ""
                        Log.i(TAG, "当前应用包名: $packageName")
                        return packageName
                    }
                }
            }
            ""
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
            // 这里需要通过反射调用系统级的全局手势功能
            // 由于运行在系统权限下，可以访问更多的系统API
            val accessibilityService = getSystemAccessibilityService()
            if (accessibilityService != null) {
                val method = accessibilityService.javaClass.getMethod("performGlobalAction", Int::class.javaPrimitiveType)
                val result = method.invoke(accessibilityService, action) as Boolean
                Log.i(TAG, "执行全局手势[$action]结果: $result")
                result
            } else {
                Log.w(TAG, "无法获取系统AccessibilityService")
                false
            }
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
            accessibilityManager != null && accessibilityManager!!.isEnabled
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
            accessibilityManager = null
            context = null
            Log.i(TAG, "SystemAccessibilityService已销毁")
        } catch (e: Exception) {
            Log.e(TAG, "销毁服务失败", e)
        }
    }

    // 私有辅助方法

    /**
     * 获取无障碍窗口列表
     */
    private fun getAccessibilityWindows(): List<AccessibilityWindowInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 通过反射获取系统级窗口信息
                val windowManagerClass = Class.forName("android.view.WindowManagerGlobal")
                val getInstanceMethod = windowManagerClass.getMethod("getInstance")
                val windowManager = getInstanceMethod.invoke(null)

                // 这里需要更复杂的反射来获取窗口信息
                // 暂时返回空列表，实际实现需要根据系统API调整
                emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取窗口列表失败", e)
            emptyList()
        }
    }

    /**
     * 根据ID查找节点（内部实现）
     */
    private fun findNodesByIdInternal(id: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windows = getAccessibilityWindows()
            for (window in windows) {
                val root = window.root
                if (root != null) {
                    findNodesByIdRecursive(root, id, results)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查找节点失败", e)
        }
        return results
    }

    /**
     * 根据文本查找节点（内部实现）
     */
    private fun findNodesByTextInternal(text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windows = getAccessibilityWindows()
            for (window in windows) {
                val root = window.root
                if (root != null) {
                    findNodesByTextRecursive(root, text, results)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查找节点失败", e)
        }
        return results
    }

    /**
     * 根据类名查找节点（内部实现）
     */
    private fun findNodesByClassInternal(className: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windows = getAccessibilityWindows()
            for (window in windows) {
                val root = window.root
                if (root != null) {
                    findNodesByClassRecursive(root, className, results)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查找节点失败", e)
        }
        return results
    }

    /**
     * 递归查找ID节点
     */
    private fun findNodesByIdRecursive(node: AccessibilityNodeInfo?, id: String, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        if (node.viewIdResourceName == id) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesByIdRecursive(child, id, results)
            }
        }
    }

    /**
     * 递归查找文本节点
     */
    private fun findNodesByTextRecursive(node: AccessibilityNodeInfo?, text: String, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        if (node.text?.toString()?.contains(text) == true) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesByTextRecursive(child, text, results)
            }
        }
    }

    /**
     * 递归查找类名节点
     */
    private fun findNodesByClassRecursive(node: AccessibilityNodeInfo?, className: String, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        if (node.className?.toString()?.contains(className) == true) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesByClassRecursive(child, className, results)
            }
        }
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
            json.put("packageName", node.packageName?.toString() ?: "")

            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val boundsJson = JSONObject()
            boundsJson.put("left", bounds.left)
            boundsJson.put("top", bounds.top)
            boundsJson.put("right", bounds.right)
            boundsJson.put("bottom", bounds.bottom)
            json.put("bounds", boundsJson)

        } catch (e: Exception) {
            Log.e(TAG, "节点转JSON失败", e)
        }
        return json
    }

    /**
     * 获取系统AccessibilityService实例
     */
    private fun getSystemAccessibilityService(): Any? {
        return try {
            // 通过反射获取系统级AccessibilityService
            // 这里需要根据具体的系统API实现
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取系统AccessibilityService失败", e)
            null
        }
    }
}
